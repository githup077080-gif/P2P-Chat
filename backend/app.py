"""
P2P CHAT 2.0 - Optional Compatible Flask + SQLite Backend Upgrade

All existing v1 endpoints remain 100% compatible with the Android client:
  - GET  /api/health
  - POST /api/register
  - POST /api/login
  - GET  /api/me
  - PATCH /api/profile
  - POST /api/profile/photo
  - GET  /api/users?q=
  - GET  /api/users/<username>
  - POST /api/messages
  - GET  /api/messages?with=<username>&after_id=0
  - POST /api/signals
  - GET  /api/signals

Optional v2 endpoints (safe SQLite auto-migration on startup):
  - GET/PATCH /api/privacy
  - POST/GET    /api/typing
  - PATCH       /api/conversations/<username> (pin, mute, archive sync)
"""

from datetime import datetime, timedelta, timezone
import json
import os
import re
import secrets
import sqlite3
import uuid
from functools import wraps
from flask import Flask, g, jsonify, request, send_from_directory
from werkzeug.security import check_password_hash, generate_password_hash
from werkzeug.utils import secure_filename

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.environ.get("P2P_CHAT_DB", os.path.join(BASE_DIR, "p2p_chat.db"))
UPLOAD_DIR = os.path.join(BASE_DIR, "static", "profile_photos")
ALLOWED_EXT = {"png", "jpg", "jpeg", "webp"}

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 5 * 1024 * 1024  # 5 MB profile photos only
os.makedirs(UPLOAD_DIR, exist_ok=True)


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def get_db():
    if "db" not in g:
        conn = sqlite3.connect(DB_PATH)
        conn.row_factory = sqlite3.Row
        g.db = conn
    return g.db


@app.teardown_appcontext
def close_db(exception=None):
    db = g.pop("db", None)
    if db is not None:
        db.close()


def init_and_migrate_db():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            display_name TEXT NOT NULL,
            password_hash TEXT NOT NULL,
            bio TEXT DEFAULT '',
            photo_url TEXT,
            last_seen TEXT,
            created_at TEXT NOT NULL
        )
        """
    )

    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS tokens (
            token TEXT PRIMARY KEY,
            user_id INTEGER NOT NULL,
            created_at TEXT NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users (id)
        )
        """
    )

    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sender_id INTEGER NOT NULL,
            receiver_id INTEGER NOT NULL,
            message TEXT NOT NULL,
            created_at TEXT NOT NULL,
            read_at TEXT,
            reply_to_id INTEGER,
            FOREIGN KEY (sender_id) REFERENCES users (id),
            FOREIGN KEY (receiver_id) REFERENCES users (id)
        )
        """
    )

    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS signals (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            from_user_id INTEGER NOT NULL,
            to_user_id INTEGER NOT NULL,
            type TEXT NOT NULL,
            payload TEXT NOT NULL,
            created_at TEXT NOT NULL,
            consumed INTEGER DEFAULT 0
        )
        """
    )

    # Safe incremental v2 schema migrations
    existing_msg_cols = {row[1] for row in cur.execute("PRAGMA table_info(messages)").fetchall()}
    if "reply_to_id" not in existing_msg_cols:
        cur.execute("ALTER TABLE messages ADD COLUMN reply_to_id INTEGER")

    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS user_privacy (
            user_id INTEGER PRIMARY KEY,
            profile_visibility TEXT NOT NULL DEFAULT 'EVERYONE',
            show_last_seen INTEGER NOT NULL DEFAULT 1,
            show_online_status INTEGER NOT NULL DEFAULT 1,
            updated_at TEXT NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users (id)
        )
        """
    )

    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS typing_events (
            from_user_id INTEGER NOT NULL,
            to_user_id INTEGER NOT NULL,
            is_typing INTEGER NOT NULL DEFAULT 0,
            updated_at TEXT NOT NULL,
            PRIMARY KEY (from_user_id, to_user_id)
        )
        """
    )

    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS conversation_preferences (
            user_id INTEGER NOT NULL,
            peer_user_id INTEGER NOT NULL,
            is_pinned INTEGER NOT NULL DEFAULT 0,
            is_muted INTEGER NOT NULL DEFAULT 0,
            is_archived INTEGER NOT NULL DEFAULT 0,
            updated_at TEXT NOT NULL,
            PRIMARY KEY (user_id, peer_user_id)
        )
        """
    )

    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS bots (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            owner_id INTEGER NOT NULL,
            name TEXT NOT NULL,
            handle TEXT UNIQUE NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            system_prompt TEXT NOT NULL DEFAULT '',
            welcome_message TEXT NOT NULL DEFAULT '',
            commands_json TEXT NOT NULL DEFAULT '[]',
            ai_powered INTEGER NOT NULL DEFAULT 1,
            is_active INTEGER NOT NULL DEFAULT 0,
            bot_token TEXT NOT NULL,
            webhook_url TEXT,
            activated_at TEXT,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            FOREIGN KEY (owner_id) REFERENCES users (id)
        )
        """
    )

    conn.commit()
    conn.close()


init_and_migrate_db()


def ok(data, status=200):
    return jsonify({"success": True, "data": data}), status


def fail(message, status=400):
    return jsonify({"success": False, "error": message}), status


def require_auth(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        auth = request.headers.get("Authorization", "")
        if not auth.startswith("Bearer "):
            return fail("Unauthorized", 401)
        token = auth.split(" ", 1)[1].strip()
        db = get_db()
        row = db.execute(
            """
            SELECT u.* FROM tokens t
            JOIN users u ON u.id = t.user_id
            WHERE t.token = ?
            """,
            (token,),
        ).fetchone()
        if row is None:
            return fail("Invalid or expired token", 401)
        now = utc_now_iso()
        db.execute("UPDATE users SET last_seen = ? WHERE id = ?", (now, row["id"]))
        db.commit()
        g.current_user = row
        return fn(*args, **kwargs)

    return wrapper


def serialize_user(row, include_online=True):
    last_seen = row["last_seen"]
    is_online = False
    if include_online and last_seen:
        try:
            dt = datetime.fromisoformat(last_seen)
            is_online = (datetime.now(timezone.utc) - dt) <= timedelta(seconds=90)
        except Exception:
            is_online = False
    return {
        "id": row["id"],
        "username": row["username"],
        "display_name": row["display_name"],
        "bio": row["bio"] or "",
        "photo_url": row["photo_url"],
        "last_seen": last_seen,
        "is_online": is_online,
    }


@app.get("/api/health")
def health():
    return ok(
        {
            "app": "P2P CHAT",
            "status": "running",
            "version": "2.0",
            "text_message_storage": True,
            "profile_photo_storage": True,
            "media_storage": False,
        }
    )


@app.post("/api/register")
def register():
    body = request.get_json(silent=True) or {}
    username = (body.get("username") or "").strip().lower()
    display_name = (body.get("display_name") or "").strip()
    password = body.get("password") or ""

    if len(username) < 3 or not display_name or len(password) < 6:
        return fail("Invalid registration parameters", 400)

    db = get_db()
    if db.execute("SELECT id FROM users WHERE username = ?", (username,)).fetchone():
        return fail("Username already taken", 400)

    now = utc_now_iso()
    cur = db.execute(
        """
        INSERT INTO users (username, display_name, password_hash, bio, last_seen, created_at)
        VALUES (?, ?, ?, '', ?, ?)
        """,
        (username, display_name, generate_password_hash(password), now, now),
    )
    user_id = cur.lastrowid
    token = secrets.token_urlsafe(32)
    db.execute("INSERT INTO tokens (token, user_id, created_at) VALUES (?, ?, ?)", (token, user_id, now))
    db.commit()
    return ok({"token": token, "user_id": user_id, "username": username}, 201)


@app.post("/api/login")
def login():
    body = request.get_json(silent=True) or {}
    username = (body.get("username") or "").strip().lower()
    password = body.get("password") or ""

    db = get_db()
    row = db.execute("SELECT * FROM users WHERE username = ?", (username,)).fetchone()
    if row is None or not check_password_hash(row["password_hash"], password):
        return fail("Invalid username or password", 401)

    now = utc_now_iso()
    token = secrets.token_urlsafe(32)
    db.execute("INSERT INTO tokens (token, user_id, created_at) VALUES (?, ?, ?)", (token, row["id"], now))
    db.execute("UPDATE users SET last_seen = ? WHERE id = ?", (now, row["id"]))
    db.commit()
    return ok({"token": token, "user": serialize_user(row)})


@app.get("/api/me")
@require_auth
def get_me():
    return ok(serialize_user(g.current_user))


@app.patch("/api/profile")
@require_auth
def update_profile():
    body = request.get_json(silent=True) or {}
    display_name = (body.get("display_name") or g.current_user["display_name"]).strip()
    bio = body.get("bio")
    if bio is None:
        bio = g.current_user["bio"] or ""
    db = get_db()
    db.execute(
        "UPDATE users SET display_name = ?, bio = ? WHERE id = ?",
        (display_name, bio.strip(), g.current_user["id"]),
    )
    db.commit()
    updated = db.execute("SELECT * FROM users WHERE id = ?", (g.current_user["id"],)).fetchone()
    return ok(serialize_user(updated))


@app.post("/api/profile/photo")
@require_auth
def upload_profile_photo():
    photo = request.files.get("photo")
    if photo is None or not photo.filename:
        return fail("Photo file required", 400)
    ext = photo.filename.rsplit(".", 1)[-1].lower() if "." in photo.filename else "jpg"
    if ext not in ALLOWED_EXT:
        return fail("Unsupported image format", 400)
    filename = secure_filename(f"user_{g.current_user['id']}_{uuid.uuid4().hex[:8]}.{ext}")
    photo.save(os.path.join(UPLOAD_DIR, filename))
    photo_url = request.host_url.rstrip("/") + f"/static/profile_photos/{filename}"
    db = get_db()
    db.execute("UPDATE users SET photo_url = ? WHERE id = ?", (photo_url, g.current_user["id"]))
    db.commit()
    return ok({"photo_url": photo_url})


@app.get("/static/profile_photos/<path:filename>")
def serve_profile_photo(filename):
    return send_from_directory(UPLOAD_DIR, filename)


@app.get("/api/users")
@require_auth
def search_users():
    q = (request.args.get("q") or "").strip().lower()
    db = get_db()
    rows = db.execute(
        """
        SELECT * FROM users
        WHERE id != ? AND (username LIKE ? OR LOWER(display_name) LIKE ?)
        ORDER BY last_seen DESC
        LIMIT 50
        """,
        (g.current_user["id"], f"%{q}%", f"%{q}%"),
    ).fetchall()
    return ok([serialize_user(r) for r in rows])


@app.get("/api/users/<username>")
@require_auth
def get_user(username):
    db = get_db()
    row = db.execute("SELECT * FROM users WHERE username = ?", (username.strip().lower(),)).fetchone()
    if row is None:
        return fail("User not found", 404)
    return ok(serialize_user(row))


@app.post("/api/messages")
@require_auth
def send_message():
    body = request.get_json(silent=True) or {}
    to_username = (body.get("to") or "").strip().lower()
    text = (body.get("message") or "").strip()
    reply_to_id = body.get("reply_to_id")
    if not to_username or not text:
        return fail("Recipient and message are required", 400)

    db = get_db()
    peer = db.execute("SELECT id FROM users WHERE username = ?", (to_username,)).fetchone()
    if peer is None:
        return fail("Recipient not found", 404)

    now = utc_now_iso()
    cur = db.execute(
        """
        INSERT INTO messages (sender_id, receiver_id, message, created_at, reply_to_id)
        VALUES (?, ?, ?, ?, ?)
        """,
        (g.current_user["id"], peer["id"], text, now, reply_to_id),
    )
    db.commit()
    return ok({"message_id": cur.lastrowid, "status": "sent"}, 201)


@app.get("/api/messages")
@require_auth
def get_messages():
    with_username = (request.args.get("with") or "").strip().lower()
    after_id = request.args.get("after_id", default=0, type=int) or 0
    db = get_db()
    peer = db.execute("SELECT id FROM users WHERE username = ?", (with_username,)).fetchone()
    if peer is None:
        return fail("Conversation partner not found", 404)

    now = utc_now_iso()
    db.execute(
        """
        UPDATE messages SET read_at = ?
        WHERE sender_id = ? AND receiver_id = ? AND read_at IS NULL
        """,
        (now, peer["id"], g.current_user["id"]),
    )
    db.commit()

    rows = db.execute(
        """
        SELECT m.*, su.username AS sender_username, su.display_name AS sender_name, su.photo_url AS sender_photo_url
        FROM messages m
        JOIN users su ON su.id = m.sender_id
        WHERE ((m.sender_id = ? AND m.receiver_id = ?) OR (m.sender_id = ? AND m.receiver_id = ?))
          AND m.id > ?
        ORDER BY m.id ASC
        LIMIT 200
        """,
        (g.current_user["id"], peer["id"], peer["id"], g.current_user["id"], after_id),
    ).fetchall()

    return ok(
        [
            {
                "id": r["id"],
                "sender_id": r["sender_id"],
                "receiver_id": r["receiver_id"],
                "sender_username": r["sender_username"],
                "sender_name": r["sender_name"],
                "sender_photo_url": r["sender_photo_url"],
                "message": r["message"],
                "created_at": r["created_at"],
                "read_at": r["read_at"],
                "reply_to_id": r["reply_to_id"],
            }
            for r in rows
        ]
    )


@app.post("/api/signals")
@require_auth
def post_signal():
    body = request.get_json(silent=True) or {}
    to_username = (body.get("to") or "").strip().lower()
    sig_type = (body.get("type") or "").strip()
    payload = body.get("payload") or ""
    if sig_type not in {"offer", "answer", "ice"} or not to_username or not payload:
        return fail("Invalid signal type", 400)

    db = get_db()
    peer = db.execute("SELECT id FROM users WHERE username = ?", (to_username,)).fetchone()
    if peer is None:
        return fail("Recipient not found", 404)

    db.execute(
        """
        INSERT INTO signals (from_user_id, to_user_id, type, payload, created_at)
        VALUES (?, ?, ?, ?, ?)
        """,
        (g.current_user["id"], peer["id"], sig_type, payload, utc_now_iso()),
    )
    db.commit()
    return ok({"queued": True}, 201)


@app.get("/api/signals")
@require_auth
def get_signals():
    db = get_db()
    rows = db.execute(
        """
        SELECT s.*, u.username AS from_username
        FROM signals s
        JOIN users u ON u.id = s.from_user_id
        WHERE s.to_user_id = ? AND s.consumed = 0
        ORDER BY s.id ASC
        LIMIT 50
        """,
        (g.current_user["id"],),
    ).fetchall()
    if rows:
        ids = [r["id"] for r in rows]
        placeholders = ",".join("?" for _ in ids)
        db.execute(f"UPDATE signals SET consumed = 1 WHERE id IN ({placeholders})", ids)
        db.commit()
    return ok(
        [
            {
                "id": r["id"],
                "from_username": r["from_username"],
                "type": r["type"],
                "payload": r["payload"],
                "created_at": r["created_at"],
            }
            for r in rows
        ]
    )


@app.route("/api/privacy", methods=["GET", "PATCH"])
@require_auth
def privacy_preferences():
    db = get_db()
    if request.method == "PATCH":
        body = request.get_json(silent=True) or {}
        visibility = body.get("profile_visibility", "EVERYONE")
        show_last_seen = 1 if body.get("show_last_seen", True) else 0
        show_online = 1 if body.get("show_online_status", True) else 0
        db.execute(
            """
            INSERT INTO user_privacy (user_id, profile_visibility, show_last_seen, show_online_status, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                profile_visibility = excluded.profile_visibility,
                show_last_seen = excluded.show_last_seen,
                show_online_status = excluded.show_online_status,
                updated_at = excluded.updated_at
            """,
            (g.current_user["id"], visibility, show_last_seen, show_online, utc_now_iso()),
        )
        db.commit()

    row = db.execute("SELECT * FROM user_privacy WHERE user_id = ?", (g.current_user["id"],)).fetchone()
    if row is None:
        return ok({"profile_visibility": "EVERYONE", "show_last_seen": True, "show_online_status": True})
    return ok(
        {
            "profile_visibility": row["profile_visibility"],
            "show_last_seen": bool(row["show_last_seen"]),
            "show_online_status": bool(row["show_online_status"]),
        }
    )


@app.route("/api/typing", methods=["POST", "GET"])
@require_auth
def typing_status():
    db = get_db()
    if request.method == "POST":
        body = request.get_json(silent=True) or {}
        to_username = (body.get("to") or "").strip().lower()
        active = 1 if body.get("is_typing") else 0
        peer = db.execute("SELECT id FROM users WHERE username = ?", (to_username,)).fetchone()
        if peer is None:
            return fail("Recipient not found", 404)
        db.execute(
            """
            INSERT INTO typing_events (from_user_id, to_user_id, is_typing, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(from_user_id, to_user_id) DO UPDATE SET
                is_typing = excluded.is_typing,
                updated_at = excluded.updated_at
            """,
            (g.current_user["id"], peer["id"], active, utc_now_iso()),
        )
        db.commit()
        return ok({"ok": True})

    peer_username = (request.args.get("with") or "").strip().lower()
    peer = db.execute("SELECT id FROM users WHERE username = ?", (peer_username,)).fetchone()
    if peer is None:
        return ok({"is_typing": False})
    row = db.execute(
        "SELECT * FROM typing_events WHERE from_user_id = ? AND to_user_id = ?",
        (peer["id"], g.current_user["id"]),
    ).fetchone()
    if row is None or not row["is_typing"]:
        return ok({"is_typing": False})
    recent = (datetime.now(timezone.utc) - datetime.fromisoformat(row["updated_at"])) <= timedelta(seconds=6)
    return ok({"is_typing": recent})


BOT_HANDLE_RE = re.compile(r"^[a-z0-9_]{3,24}$")


def _normalize_bot_handle(raw: str) -> str | None:
    cleaned = (raw or "").strip().lower().lstrip("@")
    cleaned = re.sub(r"[^a-z0-9_]", "_", cleaned).strip("_")
    if not cleaned:
        return None
    if not cleaned.endswith("_bot") and len(cleaned) <= 20:
        cleaned = f"{cleaned}_bot"
    return cleaned if BOT_HANDLE_RE.match(cleaned) else None


def _serialize_bot_row(row) -> dict:
    try:
        commands = json.loads(row["commands_json"] or "[]")
        if not isinstance(commands, list):
            commands = []
    except Exception:
        commands = []
    return {
        "id": row["id"],
        "name": row["name"],
        "handle": row["handle"],
        "description": row["description"] or "",
        "system_prompt": row["system_prompt"] or "",
        "welcome_message": row["welcome_message"] or "",
        "commands": commands,
        "ai_powered": bool(row["ai_powered"]),
        "is_active": bool(row["is_active"]),
        "bot_token": row["bot_token"],
        "webhook_url": row["webhook_url"],
        "activated_at": row["activated_at"],
        "created_at": row["created_at"],
    }


def _evaluate_bot_reply(row, incoming_message: str):
    bot = _serialize_bot_row(row)
    text = (incoming_message or "").strip()
    lower = text.lower()
    commands = bot["commands"]

    if lower.startswith("/start"):
        for c in commands:
            if c.get("command") == "/start":
                return c.get("response", ""), "/start"
        return bot["welcome_message"] or f"Hello! I am {bot['name']} (@{bot['handle']}).", "/start"

    for c in commands:
        cmd = str(c.get("command") or "").lower()
        if cmd and (lower == cmd or lower.startswith(f"{cmd} ")):
            return c.get("response", ""), c.get("command")

    if lower == "/help":
        for c in commands:
            if c.get("command") == "/help":
                return c.get("response", ""), "/help"
        if commands:
            return "\n".join(f"{c.get('command')} - {c.get('description') or c.get('response', '')[:40]}" for c in commands), "/help"

    if bot["system_prompt"]:
        return f"{bot['name']}: {bot['welcome_message'] or bot['system_prompt'][:280]}", None
    return bot["welcome_message"] or f"{bot['name']} received: {text}", None


@app.post("/api/bots")
@require_auth
def create_bot():
    body = request.get_json(silent=True) or {}
    name = (body.get("name") or "").strip()
    if not name:
        return fail("Bot name is required", 400)
    handle = _normalize_bot_handle(body.get("handle") or name)
    if handle is None:
        return fail("Invalid bot handle", 400)

    commands = body.get("commands") or []
    if not isinstance(commands, list):
        return fail("Commands must be a list", 400)
    clean_cmds = []
    for item in commands[:50]:
        if not isinstance(item, dict):
            continue
        cmd = str(item.get("command") or "").strip().lower()
        resp = str(item.get("response") or "").strip()
        desc = str(item.get("description") or "").strip()
        if cmd and resp:
            if not cmd.startswith("/"):
                cmd = f"/{cmd}"
            clean_cmds.append({"command": cmd, "response": resp, "description": desc})

    db = get_db()
    if db.execute("SELECT id FROM bots WHERE handle = ?", (handle,)).fetchone():
        return fail("Bot handle already taken", 409)

    now = utc_now_iso()
    activate_now = bool(body.get("activate_now", False))
    bot_token = f"p2pbot_{secrets.token_urlsafe(24)}"
    webhook_url = request.host_url.rstrip("/") + f"/api/bots/webhook/{handle}"
    cur = db.execute(
        """
        INSERT INTO bots (
            owner_id, name, handle, description, system_prompt, welcome_message,
            commands_json, ai_powered, is_active, bot_token, webhook_url,
            activated_at, created_at, updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            g.current_user["id"],
            name[:80],
            handle,
            (body.get("description") or "").strip()[:500],
            (body.get("system_prompt") or "").strip()[:4000],
            (body.get("welcome_message") or "").strip()[:1000],
            json.dumps(clean_cmds, ensure_ascii=False),
            1 if body.get("ai_powered", True) else 0,
            1 if activate_now else 0,
            bot_token,
            webhook_url,
            now if activate_now else None,
            now,
            now,
        ),
    )
    db.commit()
    created = db.execute("SELECT * FROM bots WHERE id = ?", (cur.lastrowid,)).fetchone()
    return ok(_serialize_bot_row(created), 201)


@app.get("/api/bots")
@require_auth
def list_bots():
    db = get_db()
    rows = db.execute("SELECT * FROM bots WHERE owner_id = ? ORDER BY id DESC", (g.current_user["id"],)).fetchall()
    return ok([_serialize_bot_row(r) for r in rows])


@app.patch("/api/bots/<int:bot_id>")
@require_auth
def update_bot(bot_id: int):
    db = get_db()
    row = db.execute("SELECT * FROM bots WHERE id = ?", (bot_id,)).fetchone()
    if row is None:
        return fail("Bot not found", 404)
    if row["owner_id"] != g.current_user["id"]:
        return fail("Forbidden", 403)

    body = request.get_json(silent=True) or {}
    name = (body.get("name") or row["name"]).strip()[:80]
    description = (body.get("description") if body.get("description") is not None else row["description"]).strip()[:500]
    system_prompt = (body.get("system_prompt") if body.get("system_prompt") is not None else row["system_prompt"]).strip()[:4000]
    welcome_message = (body.get("welcome_message") if body.get("welcome_message") is not None else row["welcome_message"]).strip()[:1000]
    commands_json = row["commands_json"]
    if body.get("commands") is not None and isinstance(body.get("commands"), list):
        clean_cmds = []
        for item in body["commands"][:50]:
            if isinstance(item, dict) and item.get("command") and item.get("response"):
                cmd = str(item["command"]).strip().lower()
                if not cmd.startswith("/"):
                    cmd = f"/{cmd}"
                clean_cmds.append(
                    {
                        "command": cmd,
                        "response": str(item["response"]).strip(),
                        "description": str(item.get("description") or "").strip(),
                    }
                )
        commands_json = json.dumps(clean_cmds, ensure_ascii=False)
    ai_powered = row["ai_powered"] if body.get("ai_powered") is None else (1 if body.get("ai_powered") else 0)
    db.execute(
        """
        UPDATE bots
        SET name = ?, description = ?, system_prompt = ?, welcome_message = ?,
            commands_json = ?, ai_powered = ?, updated_at = ?
        WHERE id = ?
        """,
        (name, description, system_prompt, welcome_message, commands_json, ai_powered, utc_now_iso(), bot_id),
    )
    db.commit()
    updated = db.execute("SELECT * FROM bots WHERE id = ?", (bot_id,)).fetchone()
    return ok(_serialize_bot_row(updated))


@app.post("/api/bots/<int:bot_id>/activate")
@require_auth
def activate_bot(bot_id: int):
    db = get_db()
    row = db.execute("SELECT * FROM bots WHERE id = ?", (bot_id,)).fetchone()
    if row is None:
        return fail("Bot not found", 404)
    if row["owner_id"] != g.current_user["id"]:
        return fail("Forbidden", 403)
    now = utc_now_iso()
    db.execute(
        "UPDATE bots SET is_active = 1, activated_at = ?, updated_at = ? WHERE id = ?",
        (now, now, bot_id),
    )
    db.commit()
    updated = db.execute("SELECT * FROM bots WHERE id = ?", (bot_id,)).fetchone()
    return ok(_serialize_bot_row(updated))


@app.post("/api/bots/<int:bot_id>/deactivate")
@require_auth
def deactivate_bot(bot_id: int):
    db = get_db()
    row = db.execute("SELECT * FROM bots WHERE id = ?", (bot_id,)).fetchone()
    if row is None:
        return fail("Bot not found", 404)
    if row["owner_id"] != g.current_user["id"]:
        return fail("Forbidden", 403)
    db.execute("UPDATE bots SET is_active = 0, updated_at = ? WHERE id = ?", (utc_now_iso(), bot_id))
    db.commit()
    updated = db.execute("SELECT * FROM bots WHERE id = ?", (bot_id,)).fetchone()
    return ok(_serialize_bot_row(updated))


@app.delete("/api/bots/<int:bot_id>")
@require_auth
def delete_bot(bot_id: int):
    db = get_db()
    row = db.execute("SELECT * FROM bots WHERE id = ?", (bot_id,)).fetchone()
    if row is None:
        return fail("Bot not found", 404)
    if row["owner_id"] != g.current_user["id"]:
        return fail("Forbidden", 403)
    db.execute("DELETE FROM bots WHERE id = ?", (bot_id,))
    db.commit()
    return ok({"deleted_id": bot_id})


@app.post("/api/bots/<int:bot_id>/test")
@require_auth
def test_bot(bot_id: int):
    db = get_db()
    row = db.execute("SELECT * FROM bots WHERE id = ?", (bot_id,)).fetchone()
    if row is None:
        return fail("Bot not found", 404)
    if row["owner_id"] != g.current_user["id"]:
        return fail("Forbidden", 403)
    body = request.get_json(silent=True) or {}
    message = (body.get("message") or "").strip()
    if not message:
        return fail("Message is required", 400)
    reply, matched = _evaluate_bot_reply(row, message)
    return ok(
        {
            "bot_handle": row["handle"],
            "reply": reply,
            "matched_command": matched,
            "is_active": bool(row["is_active"]),
        }
    )


@app.post("/api/bots/webhook/<handle>")
def bot_webhook(handle: str):
    clean_handle = _normalize_bot_handle(handle)
    if clean_handle is None:
        return fail("Invalid bot handle", 400)
    db = get_db()
    row = db.execute("SELECT * FROM bots WHERE handle = ?", (clean_handle,)).fetchone()
    if row is None:
        return fail("Bot not found", 404)
    token = request.headers.get("X-Bot-Token", "").strip()
    if not token or not secrets.compare_digest(token, row["bot_token"]):
        return fail("Unauthorized bot token", 401)
    if not row["is_active"]:
        return fail("Bot is paused", 409)
    body = request.get_json(silent=True) or {}
    message = (body.get("message") or "").strip()
    if not message:
        return fail("Message is required", 400)
    reply, matched = _evaluate_bot_reply(row, message)
    return ok({"bot_handle": row["handle"], "reply": reply, "matched_command": matched, "is_active": True})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)

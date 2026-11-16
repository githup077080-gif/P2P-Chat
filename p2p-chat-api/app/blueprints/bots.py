import json
import re
import secrets
from flask import Blueprint, g, request

from app.db import get_db
from app.extensions import limiter
from app.security import require_auth
from app.utils import fail, now_iso, ok

bots_bp = Blueprint("bots", __name__, url_prefix="/api/bots")

BOT_HANDLE_RE = re.compile(r"^[a-z0-9_]{3,24}$")


def _normalize_handle(raw: str) -> str | None:
    cleaned = (raw or "").strip().lower().lstrip("@")
    cleaned = re.sub(r"[^a-z0-9_]", "_", cleaned).strip("_")
    if not cleaned:
        return None
    if not cleaned.endswith("_bot") and len(cleaned) <= 20:
        cleaned = f"{cleaned}_bot"
    if not BOT_HANDLE_RE.match(cleaned):
        return None
    return cleaned


def _parse_commands(raw_commands) -> tuple[list[dict] | None, str | None]:
    if raw_commands is None:
        return [], None
    if not isinstance(raw_commands, list):
        return None, "commands must be a list"
    if len(raw_commands) > 50:
        return None, "commands cannot exceed 50 items"

    normalized: list[dict] = []
    for item in raw_commands:
        if not isinstance(item, dict):
            return None, "Each command must be an object"
        cmd = str(item.get("command") or "").strip().lower()
        if not cmd:
            continue
        if not cmd.startswith("/"):
            cmd = f"/{cmd}"
        cmd = cmd.split()[0][:32]
        resp = str(item.get("response") or "").strip()[:2000]
        desc = str(item.get("description") or "").strip()[:200]
        if not resp:
            return None, f"Command {cmd} requires a response"
        normalized.append({"command": cmd, "response": resp, "description": desc})
    return normalized, None


def _build_webhook_url(handle: str) -> str:
    base = (request.host_url or "").rstrip("/")
    if base:
        return f"{base}/api/bots/webhook/{handle}"
    return f"/api/bots/webhook/{handle}"


def _serialize_bot(row: dict) -> dict:
    try:
        commands = json.loads(row.get("commands_json") or "[]")
        if not isinstance(commands, list):
            commands = []
    except (TypeError, ValueError):
        commands = []
    return {
        "id": int(row["id"]),
        "name": row["name"],
        "handle": row["handle"],
        "description": row.get("description") or "",
        "system_prompt": row.get("system_prompt") or "",
        "welcome_message": row.get("welcome_message") or "",
        "commands": commands,
        "ai_powered": bool(row.get("ai_powered", 1)),
        "is_active": bool(row.get("is_active", 0)),
        "bot_token": row.get("bot_token"),
        "webhook_url": row.get("webhook_url"),
        "activated_at": row.get("activated_at"),
        "created_at": row.get("created_at"),
    }


def _evaluate_bot_reply(bot_row: dict, incoming_message: str) -> tuple[str, str | None]:
    bot = _serialize_bot(bot_row)
    text = (incoming_message or "").strip()
    lower = text.lower()
    commands = bot["commands"]

    if lower.startswith("/start"):
        for c in commands:
            if c["command"] == "/start":
                return c["response"], "/start"
        welcome = bot["welcome_message"] or f"Hello! I am {bot['name']} (@{bot['handle']})."
        return welcome, "/start"

    for c in commands:
        cmd = c["command"].lower()
        if lower == cmd or lower.startswith(f"{cmd} "):
            return c["response"], c["command"]

    if lower == "/help":
        for c in commands:
            if c["command"] == "/help":
                return c["response"], "/help"
        if commands:
            lines = [f"{c['command']} - {c['description'] or c['response'][:40]}" for c in commands]
            return "\n".join(lines), "/help"

    if bot["system_prompt"]:
        return f"{bot['name']}: {bot['welcome_message'] or bot['system_prompt'][:280]}", None
    return bot["welcome_message"] or f"{bot['name']} received: {text}", None


def _get_owned_bot(db, bot_id: int, owner_id: int) -> tuple[dict | None, tuple | None]:
    row = db.fetchone("SELECT * FROM bots WHERE id = ?", (bot_id,))
    if row is None:
        return None, fail("Bot not found", 404)
    if int(row["owner_id"]) != owner_id:
        return None, fail("Forbidden", 403)
    return row, None


@bots_bp.post("")
@require_auth
@limiter.limit("30 per minute")
def create_bot():
    body = request.get_json(silent=True) or {}
    name = str(body.get("name") or "").strip()
    if not name or len(name) > 80:
        return fail("Bot name is required (1-80 characters)", 400)

    handle = _normalize_handle(str(body.get("handle") or name))
    if handle is None:
        return fail("Valid bot handle is required (3-24 lowercase alphanumeric/underscore characters)", 400)

    commands, cmd_err = _parse_commands(body.get("commands"))
    if cmd_err is not None:
        return fail(cmd_err, 400)

    description = str(body.get("description") or "").strip()[:500]
    system_prompt = str(body.get("system_prompt") or "").strip()[:4000]
    welcome_message = str(body.get("welcome_message") or "").strip()[:1000]
    ai_powered = 1 if body.get("ai_powered", True) else 0
    activate_now = bool(body.get("activate_now", False))

    db = get_db()
    existing = db.fetchone("SELECT id FROM bots WHERE handle = ?", (handle,))
    if existing is not None:
        return fail("Bot handle is already taken", 409)

    me = int(g.current_user["id"])
    now = now_iso()
    bot_token = f"p2pbot_{secrets.token_urlsafe(24)}"
    webhook_url = _build_webhook_url(handle)
    activated_at = now if activate_now else None

    bot_id = db.insert_returning_id(
        """
        INSERT INTO bots (
            owner_id, name, handle, description, system_prompt, welcome_message,
            commands_json, ai_powered, is_active, bot_token, webhook_url,
            activated_at, created_at, updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            me,
            name,
            handle,
            description,
            system_prompt,
            welcome_message,
            json.dumps(commands, ensure_ascii=False),
            ai_powered,
            1 if activate_now else 0,
            bot_token,
            webhook_url,
            activated_at,
            now,
            now,
        ),
        commit=True,
    )
    created = db.fetchone("SELECT * FROM bots WHERE id = ?", (bot_id,))
    serialized = _serialize_bot(created)
    return ok({"data": serialized, "bot": serialized}, 201)


@bots_bp.get("")
@require_auth
def list_bots():
    me = int(g.current_user["id"])
    db = get_db()
    rows = db.fetchall(
        "SELECT * FROM bots WHERE owner_id = ? ORDER BY id DESC",
        (me,),
    )
    serialized = [_serialize_bot(r) for r in rows]
    return ok({"data": serialized, "bots": serialized}, 200)


@bots_bp.patch("/<int:bot_id>")
@require_auth
def update_bot(bot_id: int):
    me = int(g.current_user["id"])
    db = get_db()
    row, err = _get_owned_bot(db, bot_id, me)
    if err is not None:
        return err

    body = request.get_json(silent=True) or {}
    name = row["name"]
    if "name" in body and body["name"] is not None:
        candidate_name = str(body["name"]).strip()
        if not candidate_name or len(candidate_name) > 80:
            return fail("Bot name must be 1-80 characters", 400)
        name = candidate_name

    description = str(body.get("description") if body.get("description") is not None else row["description"]).strip()[:500]
    system_prompt = str(body.get("system_prompt") if body.get("system_prompt") is not None else row["system_prompt"]).strip()[:4000]
    welcome_message = str(body.get("welcome_message") if body.get("welcome_message") is not None else row["welcome_message"]).strip()[:1000]

    commands_json = row["commands_json"]
    if "commands" in body and body["commands"] is not None:
        commands, cmd_err = _parse_commands(body["commands"])
        if cmd_err is not None:
            return fail(cmd_err, 400)
        commands_json = json.dumps(commands, ensure_ascii=False)

    ai_powered = row["ai_powered"]
    if "ai_powered" in body and body["ai_powered"] is not None:
        ai_powered = 1 if bool(body["ai_powered"]) else 0

    now = now_iso()
    db.execute(
        """
        UPDATE bots
        SET name = ?, description = ?, system_prompt = ?, welcome_message = ?,
            commands_json = ?, ai_powered = ?, updated_at = ?
        WHERE id = ?
        """,
        (name, description, system_prompt, welcome_message, commands_json, ai_powered, now, bot_id),
        commit=True,
    )
    updated = db.fetchone("SELECT * FROM bots WHERE id = ?", (bot_id,))
    serialized = _serialize_bot(updated)
    return ok({"data": serialized, "bot": serialized}, 200)


@bots_bp.post("/<int:bot_id>/activate")
@require_auth
def activate_bot(bot_id: int):
    me = int(g.current_user["id"])
    db = get_db()
    row, err = _get_owned_bot(db, bot_id, me)
    if err is not None:
        return err

    now = now_iso()
    bot_token = row.get("bot_token") or f"p2pbot_{secrets.token_urlsafe(24)}"
    webhook_url = row.get("webhook_url") or _build_webhook_url(row["handle"])
    db.execute(
        """
        UPDATE bots
        SET is_active = 1, bot_token = ?, webhook_url = ?, activated_at = ?, updated_at = ?
        WHERE id = ?
        """,
        (bot_token, webhook_url, now, now, bot_id),
        commit=True,
    )
    updated = db.fetchone("SELECT * FROM bots WHERE id = ?", (bot_id,))
    serialized = _serialize_bot(updated)
    return ok({"data": serialized, "bot": serialized}, 200)


@bots_bp.post("/<int:bot_id>/deactivate")
@require_auth
def deactivate_bot(bot_id: int):
    me = int(g.current_user["id"])
    db = get_db()
    _, err = _get_owned_bot(db, bot_id, me)
    if err is not None:
        return err

    now = now_iso()
    db.execute(
        "UPDATE bots SET is_active = 0, updated_at = ? WHERE id = ?",
        (now, bot_id),
        commit=True,
    )
    updated = db.fetchone("SELECT * FROM bots WHERE id = ?", (bot_id,))
    serialized = _serialize_bot(updated)
    return ok({"data": serialized, "bot": serialized}, 200)


@bots_bp.delete("/<int:bot_id>")
@require_auth
def delete_bot(bot_id: int):
    me = int(g.current_user["id"])
    db = get_db()
    _, err = _get_owned_bot(db, bot_id, me)
    if err is not None:
        return err

    db.execute("DELETE FROM bots WHERE id = ?", (bot_id,), commit=True)
    return ok({"data": {"deleted_id": bot_id}, "deleted_id": bot_id}, 200)


@bots_bp.post("/<int:bot_id>/test")
@require_auth
def test_bot(bot_id: int):
    me = int(g.current_user["id"])
    db = get_db()
    row, err = _get_owned_bot(db, bot_id, me)
    if err is not None:
        return err

    body = request.get_json(silent=True) or {}
    message = str(body.get("message") or "").strip()
    if not message:
        return fail("Test message is required", 400)

    reply, matched = _evaluate_bot_reply(row, message)
    result = {
        "bot_handle": row["handle"],
        "reply": reply,
        "matched_command": matched,
        "is_active": bool(row.get("is_active", 0)),
    }
    return ok({"data": result, **result}, 200)


@bots_bp.post("/webhook/<handle>")
@limiter.limit("120 per minute")
def bot_webhook(handle: str):
    clean_handle = _normalize_handle(handle)
    if clean_handle is None:
        return fail("Invalid bot handle", 400)

    db = get_db()
    row = db.fetchone("SELECT * FROM bots WHERE handle = ?", (clean_handle,))
    if row is None:
        return fail("Bot not found", 404)

    provided_token = request.headers.get("X-Bot-Token", "").strip()
    if not provided_token:
        auth = request.headers.get("Authorization", "")
        if auth.startswith("Bot "):
            provided_token = auth[4:].strip()
    if not provided_token or not secrets.compare_digest(provided_token, row["bot_token"]):
        return fail("Unauthorized bot token", 401)

    if not bool(row.get("is_active", 0)):
        return fail("Bot is currently paused", 409)

    body = request.get_json(silent=True) or {}
    message = str(body.get("message") or "").strip()
    if not message:
        return fail("Message is required", 400)

    reply, matched = _evaluate_bot_reply(row, message)
    result = {
        "bot_handle": row["handle"],
        "reply": reply,
        "matched_command": matched,
        "is_active": True,
    }
    return ok({"data": result, **result}, 200)

"""Add bots table for server-activated Bot Builder."""

from pathlib import Path
from alembic import op

revision = "0002_add_bots"
down_revision = "0001_initial_schema"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    dialect = bind.dialect.name
    sql_dir = Path(__file__).resolve().parents[2]
    filename = "002_bots_postgres.sql" if dialect == "postgresql" else "002_bots_sqlite.sql"
    sql = (sql_dir / filename).read_text(encoding="utf-8")
    for statement in [s.strip() for s in sql.split(";") if s.strip()]:
        op.execute(statement)


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS bots")

#!/usr/bin/env python3
"""Create local-only random credentials without printing or overwriting secrets."""
from pathlib import Path
import secrets
root = Path(__file__).resolve().parents[1]
owner, app = secrets.token_hex(24), secrets.token_hex(24)
content = f'''# Generated local credentials, never commit.
DB_OWNER_PASSWORD={owner}
DB_APP_PASSWORD={app}
MIGRATION_DATABASE_URL=postgresql://krapiva_owner:{owner}@127.0.0.1:55432/krapiva
TEST_DATABASE_URL=postgresql://krapiva_owner:{owner}@127.0.0.1:55432/krapiva
DATABASE_URL=postgresql://krapiva_app:{app}@127.0.0.1:55432/krapiva
NODE_ENV=development
HOST=127.0.0.1
PORT=3000
LOG_LEVEL=info
MEDIA_ROOT=../work/media
SMTP_HOST=127.0.0.1
SMTP_PORT=51025
'''
with (root / '.env').open('x', encoding='utf-8') as stream:
    (root / '.env').chmod(0o600)
    stream.write(content)
print('Created .env with local credentials; existing files are never overwritten.')

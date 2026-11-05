# Backend — P2 Foundation

Node24/TypeScript/Fastify, PostgreSQL17, private filesystem media и Mailpit.
Состояние этапа и реальные результаты: [PROGRESS](../docs/PROGRESS.md).
Запуск/backup/restore: [DEPLOYMENT_RU](../docs/DEPLOYMENT_RU.md).

Из корня: `python scripts/init_dev.py` один раз для новых local credentials;
`docker compose up -d --wait db mailpit`, `docker compose build api`,
`docker compose run --rm migrate`, `docker compose up -d --wait api`.
API доступен только локально на 53000, Mailpit UI на 58025, PostgreSQL на 55432.
Существующий .env не перезаписывать, volumes не удалять.

Из backend/ при запущенных локальных DB/SMTP:

```sh
npm ci --ignore-scripts --no-fund --no-audit
npm run build
node --env-file=../.env --test dist/test/*.test.js
```

Для host process: `node --env-file=../.env dist/src/main.js` (порт3000).
`.env` не загружается автоматически. Конфигурация описана в `.env.example`;
секреты создаются локально и никогда не попадают в Git/логи.

Работают versioned `/v1` owner profile/checkins/media, проверяемые серверные
сессии, UUID/idempotency keys/versions/cursor paging; OpenAPI генерируется
`npm run openapi`, контракт проверяется тестом. Это не sign-up/login/reset P3.
Synthetic accounts создаёт только явный dev CLI; production seed отсутствует.
Social/map/badges routes ещё не реализованы, возвращается 404.

`/health/live` проверяет процесс, `/health/ready` — серверные зависимости;
не является обещанием готовности всех продуктовых функций. SIGINT/SIGTERM
закрывают HTTP/pool. JSON limit16KiB, image upload8MiB и16M decoded pixels.
Права владельца проверяются на сервере, приватные фото не доступны как static.

Тесты работают с настоящими loopback HTTP/PostgreSQL/SMTP. TEST_DATABASE_URL
предназначен только для local/CI DB; каждый suite создаёт собственную DB.
Runtime drill `node --env-file=../.env test/runtime-check.mjs` временно останавливает
local API для согласованного SQL+media snapshot; не запускать на production.
Повторять проверки только при изменениях или конкретной диагностике.

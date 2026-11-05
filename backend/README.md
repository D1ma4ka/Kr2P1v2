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

## P3: настоящие аккаунты и профиль

Общий Android/iOS контракт: `docs/api/openapi.yaml` (генерируется зарегистрированными
маршрутами; contract test запрещает drift). Все auth operations под `/v1/auth`:
`register`, `resend`, `verify`, `login`, `forgot-password`, `reset-password`, `refresh`.
Код из реального письма вводится в клиент; expiry/consumption/rotation сохраняются
в PostgreSQL. Password 12–128 символов, scrypt-v1; access 1h, refresh 30d;
reset/replay отзывают сессии. Ошибки/лимиты не раскрывают наличие аккаунта.

`GET /v1/me`, `PATCH /v1/me/profile`, `GET /v1/users/{id}` — полный профиль,
version conflicts, HTTPS links, gender/link visibility и независимые avatar/cover.
Публичные/friends media проверяются на каждом прямом запросе. Приватный профиль
другому аккаунту отвечает 404. Никаких Android identifiers в API.
`DELETE /v1/session` отзывает текущую сессию; `POST /v1/me/deletion-request`
сохраняет запрос и 30-day deadline, немедленно закрывает доступ/публичность.
Это запрос, а не сообщение об уже выполненном стирании: production erasure/
legal retention/backups требуют операционного процесса до эксплуатации (ADR-0004).

Dev/test: SMTP_HOST только localhost/127.0.0.1/mailpit, recipient только
`@example.invalid`; письма остаются в Mailpit. Production отдельно конфигурирует
`SMTP_DELIVERY_ENABLED=true`, `SMTP_HOST`, `SMTP_PORT=465|587`, `SMTP_FROM`,
`SMTP_USER`, `SMTP_PASSWORD`; TLS обязателен. Не включать delivery без разрешения
владельца; текущая задача не отправляет внешние письма и не развёртывает production.

Проверки P3: `npm run build`, затем
`node --env-file=../.env --test --test-concurrency=1 dist/test/*.test.js`.
13 настоящих HTTP/SQL/SMTP tests; отдельный negative test подтверждает rollback
при недоступном SMTP, истечение кода и независимость rate budgets разных кодов.
Последовательный запуск suite избегает resource contention локального Mailpit;
реальные concurrent/idempotency/migration cases остаются внутри тестов.

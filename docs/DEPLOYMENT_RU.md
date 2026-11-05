# Локальный backend P2 и перенос в российское окружение

Статус проверок — PROGRESS.md. Локальный запуск проверен, удалённый staging и
production не развёрнуты. Следующие инструкции не разрешают покупку/деплой.

## Новая локальная среда

Node24 (версия в .nvmrc), Docker Engine/Compose. Из корня репозитория:

```sh
python scripts/init_dev.py
chmod 644 infra/postgres/01-runtime-role.sql
docker compose up -d --wait db mailpit
docker compose build api
docker compose run --rm migrate
docker compose up -d --wait api
```

init_dev создаёт случайные локальные пароли в ignored `.env` (0600), никогда
не перезаписывает существующий файл. Не выполнять `docker compose config`
в публичный лог: разрешённые env substitutions содержат пароли.
В управляемой Codex-среде выбрать локальный Docker socket по runtime skill;
`CODEX_PROXY_CERT` передаётся build secret, не копируется в image. Без proxy
используется пустой backend/build-support/empty-ca.crt; TLS verification не отключается.

- API loopback: `http://127.0.0.1:53000/health/live`, `/health/ready`.
- PostgreSQL loopback55432; runtime role krapiva_app без DDL.
- Mailpit UI: `http://127.0.0.1:58025`; SMTP51025, только synthetic recipients.
- Media: private Docker volume, metadata/owner/hash в PostgreSQL.
- OpenAPI: `docs/api/openapi.yaml`, versioned `/v1`; bearer sessions из SQL.

Ни один сервис не доступен из Интернета по этому Compose. GitHub — только код.
Миграции не исполняются автоматически API-процессом. Отдельный runner с owner
credentials применяет ordered SQL, SHA ledger и advisory lock; ошибка шага
откатывает этот шаг. Applied SQL не редактировать; новый номер миграции.

## Локальная проверка после разрешения

Из `backend/` (сначала запущены PostgreSQL и Mailpit):

```sh
npm ci --ignore-scripts --no-audit --no-fund
npm run build
node --env-file=../.env --test dist/test/*.test.js
npm audit --audit-level=high
```

Результаты конкретных запусков — stages/P2.md; не повторять проверки без причины. Tests создают
собственные `krapiva_test_<uuid>` DB и удаляют только их. Не передавать адреса
реальных серверов. `TEST_DATABASE_URL` — local migration role, не API runtime.
OpenAPI генерируется `npm run openapi`, contract test проверяет точное совпадение.

Явные dev CLI после сборки:

```sh
node --env-file=../.env dist/src/operations/provision.js
node --env-file=../.env dist/src/operations/test-mail.js
```

Первый сохраняет два synthetic accounts и session tokens в закрытый ignored
`work/dev-accounts.json`, не печатает токены и не перезаписывает файл. Сессии
живут сутки. Это инструмент тестирования, не реализация sign-up/login P3.
Второй отправляет реальное письмо в локальный SMTP-приёмник, не наружу.
Повторное provision при существующих users/file отказывает. Runtime drill теперь
создаёт отдельную synthetic batch и `work/runtime-accounts-<uuid>.json` при
каждом явно запущенном испытании: старые данные/токены не затираются. Не удалять
реальные данные ради теста.

## Persistence / restore — уже проверено

`node --env-file=../.env test/runtime-check.mjs` из backend: synthetic checkin
и фото через HTTP, запрет DDL runtime role, пауза API writes, pg_dump+копия media,
restart DB/Mailpit/API, проверка session/ACL/photo hash/mail, pg_restore в новую
изолированную тестовую БД и повторное чтение фото/связи/ACL. PASS.
Сценарий сохраняет fixture data в local volumes и backup под ignored work/.
Он останавливает только локальный API на время снимка; не выполнять на production.
`RESULT.json` backup не содержит токенов; dump/accounts файлы чувствительные
даже для synthetic данных. Не загружать их в GitHub.

Обычная безопасная остановка: `docker compose stop api db mailpit`.
Возобновление после разрешения: `docker compose up -d --wait db mailpit api`.
**Не использовать `down -v`: это уничтожит volumes.**

## Перенос на разрешённый РФ staging/VPS (ещё NOT RUN)

До запуска владелец согласует российский хостинг/хранение персональных данных,
бюджет, DNS/TLS, почту и legal. P2 не готов для публичного product traffic:
полные sign-up/verification/reset/rotation и social ACL появляются в P3/P6.
Переезд сохраняет image, SQL migrations и OpenAPI. Не менять Android бизнес-код.

1. Подготовить отдельный Linux с Docker, закрыть PostgreSQL/SMTP/media от внешней
   сети, создать runtime и migration roles раздельно. Только runtime получает
   SELECT/INSERT/UPDATE/DELETE; owner credentials не передавать API контейнеру.
2. Передать проверенный image (image ID/commit в журнале); не использовать
   mutable latest. На целевой машине запускать API как node/UID1000, read-only
   filesystem, private writable media volume, no-new-privileges, cap-drop ALL,
   loopback binding 127.0.0.1:3000. Автоматической публикации image в registry нет.
3. Секретный env file вне Git: NODE_ENV=production, HOST=0.0.0.0, PORT=3000,
   DATABASE_URL runtime role, DB_POOL_MAX с учётом общего бюджета соединений,
   MEDIA_ROOT=/data/media. Для удалённой БД sslmode=verify-full, при частном CA
   sslrootcert указывает на read-only certificate mount. URL options ограничены,
   `ssl=false` и несколько sslmode отвергаются. Не отключать проверку сертификата.
4. Обеспечить российские media/backups и одинаковый private media mount для API.
   Для нескольких хостов сначала реализовать/проверить S3 adapter или общий FS,
   затем scale-out; независимые локальные volumes между репликами недопустимы.
5. Выполнить согласованный backup текущих DB+media, проверить restore отдельно;
   остановить writers на время согласованного снимка. Запустить image с командой
   `node dist/src/db/migrate.js` и отдельным MIGRATION_DATABASE_URL. После успеха
   вернуть runtime env, запустить API и проверить /health/ready (ledger hashes,
   actual schema access и media filesystem). Не мигрировать из каждого replica.
6. Настроить разрешённый TLS reverse proxy на approved DNS. Передавать только
   к loopback API; принимать TLS1.2/1.3, ограничить upload примерно8MiB и время
   запроса, rate-limit неавторизованные запросы на proxy, не логировать query,
   Authorization, Cookie или request body. trustProxy=false в API; не принимать
   произвольные client IP headers как достоверные. Открыть наружу только HTTPS.
7. Текущий SMTP adapter намеренно local/test-only и отказывает production.
   Production SMTP/TLS/credentials/verification links реализовать и проверить
   в P3 до реальных аккаунтов. Mailpit и synthetic provisioning не публиковать.
8. Собрать staging с KRAPIVA_STAGING_API_BASE_URL=https://<approved-dns>/,
   выполнить настоящий A20 E2E только при доступном сервере/клиенте. Сейчас это
   BLOCKED/NOT RUN; генерация BuildConfig на example.invalid не доказывает E2E.
9. Перед эксплуатацией нужны мониторинг HTTP5xx/latency, DB connections/slow
   queries, свободного места media/DB, возраста успешных backup и restore drill.
   Пороги устанавливаются после измерений на выбранной машине; 500k capacity
   не подтверждена. Эти operational controls — P8/P9 до public production.

## Backup, rollback и recovery

Рабочий локальный образец: `backend/test/runtime-check.mjs`, проверенные реальные
pg_dump/pg_restore и копирование media при остановленном API. Снимки содержат
чувствительные данные; owner-only permissions, закрытое хранение и шифрование
backup с отдельным управлением ключами обязательны до эксплуатации (P8).
Retention/RPO/RTO согласовать после измерения размера/скорости восстановления.
Не считать локальную копию на том же диске аварийным off-site backup.

Процедура восстановления: остановить writers → создать **новую** пустую DB →
pg_restore с exit-on-error → восстановить media в отдельный private volume →
проверить migrations hashes, роли, session/ACL и photo hashes через отдельный
API instance → переключить конфигурацию только после принятия результата.
Восстановление поверх работающей production DB без разрешения не выполнять.
Автоматических destructive down migrations нет. Предпочтителен roll-forward;
старый image может требовать старую схему: при несовместимости использовать
согласованный restore/switch, не просто запускать старый бинарник на новой БД.

При потере подтверждения COMMIT media-файлы сохраняются: исход commit может быть
неизвестен. Для reconciliation остановить writers, сделать backup и сравнить
storage_key/thumbnail_key всех media_assets с private файловым каталогом.
Отсутствующий referenced file — restore из backup, а не удаление metadata.
Unreferenced файлы сначала карантинировать после проверки возраста и отсутствия
in-flight upload; удаление/retention — отдельная проверенная операция P8.
Если delete прерван, deleted_at уже закрывает новые чтения/attachments, повтор
того же owner DELETE безопасно завершает unlink. Тест сбоя диска проверяет это.

LOCAL_BACKEND_VERIFIED означает только проверенную локальную foundation;
STAGING_VERIFIED и PRODUCTION_DEPLOYED требуют отдельного фактического результата.

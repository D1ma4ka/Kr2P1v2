# Локальный backend P2 — WIP, не production runbook

Работа остановлена владельцем по лимиту («22 65 остановись»). Продолжение только
после новой команды. Проверенные команды ниже сохраняют точку продолжения;
это не разрешение автоматически повторять сборки/тесты. P2 ещё INCOMPLETE.

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
используется пустой /dev/null; TLS verification не отключается.

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

9/9 PASS уже получен в этой сессии; не повторять без причины. Tests создают
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
Повторное provision при существующих users/file отказывает; безопасный refresh
тестовых сессий пока не реализован. Не удалять реальные данные ради теста.

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

## Невыполненные эксплуатационные задачи

Полный РФ VPS/TLS/SMTP/storage/secret-management runbook, encrypted remote
backups/retention/restore automation, мониторинг и capacity tests — НЕ приняты.
До реальных схемных изменений нужен backup и проверка restore; предпочтителен
roll-forward. Destructive down не предоставлен. При неизвестном исходе COMMIT
media-файлы не удаляются: reconciliation/orphan policy надо допроверить.
Private local FS требует общего mount/S3 adapter перед несколькими API-хостами.
Один PostgreSQL и API здесь не доказывают 500k concurrent или registered capacity.
Account workflows/rotation/full privacy grants остаются P3/P6. Не публиковать
текущий foundation как готовый пользовательский сервис.

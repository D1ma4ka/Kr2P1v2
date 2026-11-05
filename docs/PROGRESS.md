# P2 — продолжение 5 ноября 2026

Владелец разрешил завершить только P2 с `2c5e52d189197a378d4313b8abaf3d70af57db5b`.
Ветка `codex/krapiva-2-p2`; local HEAD совпадает с полученным GitHub FETCH_HEAD.
P2.1–P2.4 не повторяются. Текущая работа: незавершённый P2.5 — review ошибок,
негативные проверки, CI, переносимая конфигурация адреса API и документация.
**Новые пороги:** недельный остаток <25% либо пятичасовой <50% → остановка,
сохранение GitHub и отчёт. Снимок владельца: 100% недельного, 63% пятичасового.
Инструмента непрерывного измерения нет; нужны ручные обновления. P3 не разрешён.
Ниже — исторический отчёт остановки; его прежние пороги заменены указанными выше.

Review update: readiness проверяет hash ledger, доступ к таблицам и media FS;
URL DB options ограничены (TLS override/duplicates отвергаются), media directory
fsync после записи/удаления. Два новых targeted negative tests PASS, build PASS.
Runtime drill теперь каждый раз создаёт новую synthetic batch и закрытый файл,
не зависит от истёкших вчерашних токенов и не перезаписывает прежние fixtures.
Следующий checkpoint: backend CI с настоящими DB/SMTP и restore drill.

Backend full suite после review: 11/11 PASS. Android env configuration added,
assembleDebug/lintDebug PASS; dev/staging/release constants проверены на
reserved DNS, не E2E staging. Следующее действие: подтвердить GitHub CI на
чистом runner и завершить матрицу/отчёт P2. Новая сборка UI/P3 не начата.

---

# STOP — P2 WIP, остановка по лимиту и команде владельца

Ветка: `codex/krapiva-2-p2`. Последнее сообщение владельца о лимитах:
**«22 65 остановись»**. Один остаток ниже порога; владелец явно остановил работу.
После этого не запускались новые сборки/тесты/разработка. Получен только результат
уже запущенного runtime drill; далее документация, Git и остановка dev сервисов.
Продолжение **только по новой команде «Продолжай»**. P3 не начинать.
P0/P1 DONE. **P2 INCOMPLETE / WIP**, не повторять выполненные подэтапы.
SHA этого WIP-коммита: `git log -1`; полный SHA в итоговом отчёте владельцу.

## Что уже сохранено и проверено

- `5b34497`: v1.3 source checker, 12 исходников/180 требований/29 экранов/43
  запланированных E2E; исходные AGENTS и оба архива сохранены.
- `c52b330`: PostgreSQL17, полная foundation schema, transactional/checksummed
  migrations с lock, bounded pool, runtime role без DDL, local Compose DB.
- `d1f17aa`: реальный `/v1` owner API, постоянные hash-only sessions/revocation,
  profile fields, verified positions paging, checkin CRUD, durable idempotency,
  optimistic versions, DB-wide per-account budget, OpenAPI/ACL tests.
- Текущий WIP: private filesystem media с metadata в SQL, нормализация JPEG,
  thumbnail, EXIF removal, limits, owner ACL, checkin attachments/deletion;
  SMTP Mailpit с persistent volume, non-root Docker image/Compose API,
  local-only synthetic accounts/mail CLI, DB config validation и runtime drill.

## Фактические результаты до остановки

- `npm run build` — PASS (TypeScript).
- `node dist/src/http/export-openapi.js` — контракт обновлён из route schemas.
- `node --env-file=../.env --test dist/test/*.test.js` — **9/9 PASS**, 0 skipped,
  ~2.23 s. Реальный PostgreSQL/HTTP/SMTP, не in-memory substitutes.
- `npm audit --audit-level=high` — **0 vulnerabilities** на момент проверки.
- `docker compose build api` — PASS; pinned base images, TLS verification on,
  build proxy CA только transient secret, runtime USER node.
- `docker compose run --rm migrate`, `docker compose up -d --wait api` — PASS.
- Уже запущенный `node --env-file=../.env test/runtime-check.mjs` завершился PASS:
  runtime DB role не может DDL; restart API+PostgreSQL+Mailpit сохраняет accounts,
  sessions, checkin, photo bytes и письмо; isolated pg_restore + копия media
  восстанавливают ту же фотографию/attachment и owner ACL. Результат прочитан
  при сохранении после команды остановки; повторный прогон не запускался.
- `git diff --check` — PASS до финального сохранения документов.
- Android не менялся/не собирался. GitHub CI для этих коммитов НЕ запускался.
  Это не прохождение всех продуктовых A1–A43 и не capacity test на 500k users.

## Что осталось для завершения только P2

1. Проверить статус/remote, прочитать AGENTS, этот файл и stages/P2.md.
2. Доработать P2 review и документацию: DEPLOYMENT_RU сейчас WIP, актуализировать
   `.env.example`, backend/root README, architecture/matrix/roadmap/readiness.
3. Обновить CI: текущий P1 job вызывает npm test без PostgreSQL/SMTP/env,
   новые integration tests требуют настоящие сервисы. Добавить их запуск,
   миграции/contract/runtime checks; не запускать Android без необходимости.
   Source job `git diff --check` на всю историю также должен учитывать
   побайтово сохранённые оригиналы ТЗ/архивов (trailing blank line в источнике).
4. Проверить недостающие негативные случаи DB config, media/storage failures,
   миграций/runtime readiness и границы схемы; исправления — отдельный коммит.
   Не повторять уже пройденные проверки без изменения/конкретной причины.
5. Решить P2-часть переносимого `API_BASE_URL`: Android BuildConfig пока не
   содержит env URL; UI/API client остаются P3. Нельзя считать FR-118 DONE.
6. После нужных проверок оформить итог P2, commit/push, остановиться перед P3.

Открытых падений в выполненных финальных тестах нет; P2 не завершён из-за
остановки и перечисленных проверок/CI/docs. Account signup/verification/reset,
полная field ACL/social grants, каталог/moderation UI, карты, статистика и
badge engine ещё не реализованы: соответствующие P3–P7, не заглушки.
Проверенные токены — реальные серверные сессии synthetic accounts, созданные
только явным dev CLI; это НЕ production login/регистрация.

## Данные и быстрый возврат к работе

Локально `.env` (0600, Git ignored), `work/dev-accounts.json` (секретные synthetic
session tokens, Git ignored), `work/backups/<uuid>/` (synthetic SQL dump/media/
RESULT.json, Git ignored). Не печатать и не коммитить их содержимое.
Docker volumes `krapiva-dev_db-data`, `krapiva-dev_media-data`,
`krapiva-dev_mail-data` сохраняются; dev контейнеры остановлены для экономии.
Никогда не использовать `down -v` для обычной остановки.
После разрешения: `docker compose up -d --wait db mailpit api` использует уже
собранный локальный image; при новой среде — инструкции DEPLOYMENT_RU.
CLI credentials живут сутки; после истечения нужен явный безопасный dev-session
refresh, не подмена production auth. Повторное provision намеренно не затирает
существующий файл. Backup SQL содержит только synthetic data, не GitHub storage.
Media FS adapter рассчитан на один storage mount; scale-out требует общего
storage/S3 adapter. При потере подтверждения COMMIT файлы сохраняются, чтобы
не удалить потенциально записанное фото; orphan cleanup/reconciliation и
production retention ещё требуют runbook/проверки. TLS/РФ hosting/production
SMTP/мониторинг/нагрузка не приняты; никаких расходов/production/merge не было.

---

## История текущей сессии (не актуальная команда на продолжение)

# P2 — активная точка продолжения

Владелец разрешил только P2 по ТЗ v1.3. Ветка `codex/krapiva-2-p2`,
база `44fb42680e936bf9f1c2b2fc2b19c063dd6928ec`. P0/P1 DONE, P2 IN PROGRESS.
План небольших коммитов и критерии: `stages/P2.md`.
P2.1: source checker адаптирован к двум версиям; PASS (12 исходников,
180 требований, 29 экранов, 43 запланированных сценария). Это не тест приложения.
P2.2 DONE: PostgreSQL17, SQL schema, checksum ledger/transaction/advisory lock,
bounded pool, random local credentials, runtime role without DDL. Build PASS;
реальный PostgreSQL test PASS: concurrent/repeated migrate, rollback of failed
DDL, checksum drift, FK/unique/rating/GTIN constraints. Пустая dev DB мигрирована.
При первом старте исправлены права читаемости init SQL (600 → 644), без потери
volume. P2.3 DONE: `/v1` owner API, sessions+revoke, profiles, verified position page,
checkin CRUD, durable idempotency/version checks, DB-wide per-account budget.
Real HTTP+SQL test PASS with two accounts; OpenAPI validation/drift test PASS;
migration and process regression PASS. No public sign-up/reset/social routes.
Следующий шаг P2.4: private media, SMTP и контейнерный API.

Лимиты: достоверного API нет. Владелец сообщил «69 и 73» в этой сессии —
оба выше порогов; это снимок, не непрерывное измерение. При недельном остатке
<50% или пятичасовом <25% прекратить новую разработку/тесты, сохранить и
отправить прогресс. Не переходить на платные кредиты. После P2 остановиться;
P3, UI refinement и iOS не разрешены. Исторические отчёты ниже не актуальный статус.

---

# Актуальная точка продолжения — ТЗ v1.3

**P0 DONE, P1 DONE, P2 NOT STARTED. iOS PLANNED P10–P13 (MUST).**
Последняя задача: только прочитать/принять полный архив v1.3 и референс,
сверить с текущим кодом, обновить документы и сохранить в GitHub.
Код/CI/scripts/assets P1 и AGENTS не менялись; новых сборок нет.
Текущая ветка codex/krapiva-2-p1, база документационного обновления fbe95ba.
SHA последнего коммита документации: `git log -1` и итоговый отчёт пользователю.

## Следующая работа — только после команды владельца на P2

1. Прочитать AGENTS, этот файл, TECHNICAL_SPECIFICATION v1.3,
   UPDATE_v1.3, IMPLEMENTATION_PLAN, PLATFORM_PARITY; проверить remote/status.
2. Адаптировать scripts/verify_p0.py к версиям manifest/новому количеству ID:
   сейчас прежняя проверка несовместима с новой документацией. В этой задаче
   скрипт/CI намеренно не менялись; не считать старый PASS доказательством v1.3.
3. Только в разрешённом P2: SQL migrations/постоянная БД, API/OpenAPI для обеих
   платформ, media отдельно, локальный test SMTP, portable config/deploy.
4. Не начинать P1.1 или iOS код в P2. Новые tokens/logo/nav/home — будущий
   согласованный UI-блок P3 (P1.1); motion P3; charts/tags P5; карта/shops P7.
5. Завершив согласованный этап, проверки/commit/push/отчёт и остановка.

Полная матрица: 180 требований (135 FR/45 NFR), 174 MUST/5 SHOULD/1 LATER,
29 экранов, 43 E2E (все NOT RUN). NFR-001 DONE относится к сохранённой P1 сборке;
новые дизайн/motion/map/iOS MUST ещё не реализованы. Пользовательских API/БД
пока нет. Ни Android-релиз, ни весь проект не объявляются готовыми.

## iOS видимый статус

P10/P11/P12/P13: **PLANNED**, проверенных iOS артефактов нет. Mac/Xcode,
iPhone/Apple Developer/ASC/signing/карты — условия будущей проверки, а не
исследованные сейчас блокеры. Следующее действие — владелец разрешает этап;
после P9 предложить P10. План/владельцы действий и артефакты — ROADMAP.md.
Не переписывать Android под KMP сейчас. Android готов + iOS не принята =
весь двухплатформенный продукт INCOMPLETE. Новый запрос на P2 пока не получен.

Пороги остатка ≤25% пятичасового или ≤50% недельного лимита сохраняются;
проценты недоступны инструментам. При обнаруженном пороге — сохранить
WIP/точку продолжения и остановиться до команды. Ниже сохранена история P1.

---

# KraPiva 2.0 — точка продолжения

Дата: 2026-11-04. Ветка: `codex/krapiva-2-p1`.
Последний проверенный CI коммит кода/конфигурации: `69ed3e9cb856c67059e8b5e5f670e0d133f054ec`.
Финальный коммит отчёта содержит только документацию; его SHA — `git log -1`.

## Текущее состояние

**P0 DONE, P1 DONE. P2 не начат. Работа остановлена до команды владельца.**
В следующей сессии прочитать AGENTS и этот файл, проверить Git status/remote.
Только после нового разрешения начинать P2; повторять P0/P1 не требуется.

## Лимиты и порядок остановки

При остатке **≤25% пятичасового** или **≤50% недельного** лимита прекратить
разработку, сохранить все относящиеся изменения, обновить PROGRESS, commit/push
рабочей ветки и остановиться. Продолжение только по команде владельца.
API/инструмента чтения этих процентов в текущей сессии нет: автоматический
контроль процентов не подтверждён. Не выдавать оценку по времени за остаток квоты.
После каждого этапа также commit/push, отчёт и остановка до команды владельца.

## Что готово

- P0: восемь исходных документов с SHA-256, инвентаризация, решения,
  исходная матрица 82 требований (сейчас актуализирована до 180), тест-план. Коммит `cf4d28a`.
- P1: новая Android Kotlin/Compose/Material 3 основа (splash, стартовый экран,
  диалог «О KraPiva»); оригинальные SVG/PNG, adaptive/monochrome icons и токены.
- HTTP server Node/TypeScript/Fastify: liveness, проверка конфигурации,
  реальные ошибки/лимиты тела, безопасные логи, SIGTERM; четыре теста.
- ADR стека/навигации, воспроизводимые команды, CI и документация.
- Основной код P1 `1bb2d35`, прежняя WIP-точка `f36bfe5`.
  Исправление SDK bootstrap `8ce428e`, lint/report policy `69ed3e9cb856c67059e8b5e5f670e0d133f054ec`.

## Проверки и исправления последней сессии

CI: https://github.com/D1ma4ka/Kr2P1v2/actions/runs/41888047279
для `69ed3e9cb856c67059e8b5e5f670e0d133f054ec` — **все три job SUCCESS**:
source-and-assets, backend, android. Android assembleDebug/lintDebug:
BUILD SUCCESSFUL, 2m55s, **0 errors, 4 warnings**. Backend включает четыре
HTTP/config/process теста и npm audit. Проверены исходные документы,
контраст/ресурсы/wrapper и diff.

Причина прежнего CI failure: `sdkmanager: command not found` (exit127).
Исправлено явной установкой Android tools через setup-android v3.2.2,
закреплённые SHA и CLI 12266719. Следующий run выявил четыре version advisories,
которые прежняя политика warningsAsErrors превращала в ошибки. Теперь lint
errors остаются fatal, предупреждения видимы в полном CI-логе, проверки не
отключены и baseline не добавлен. Изменение описано в ADR-0002.
Локальные сборки и тесты прошлой сессии повторно не запускались.
Финальный коммит только документации помечен `[skip ci]`: код уже проверен.

## Оставшиеся ограничения и задачи

- До релиза проверить совместимость и обновить targetSdk35 и AndroidX:
  Compose BOM2025.04.01, activity-compose1.10.1, core-splashscreen1.0.1.
  Это четыре предупреждения CI, не результат security audit.
- Эмулятор/телефон, TalkBack и функциональные screenshots NOT RUN;
  аппаратное ускорение `/dev/kvm` в рабочей среде отсутствует.
- БД, auth, каталог и социальная сеть пока не реализованы; A1–A12 NOT RUN.
  Никаких фиктивных production API/данных нет.
- NFR-001 DONE; полный NFR-008 требует migration/contract проверок P2/P8.
  Остальной статус — REQUIREMENTS_MATRIX и RELEASE_READINESS.

## Следующий этап — P2 (после отдельного разрешения)

SQL/постоянная БД, миграции, OpenAPI и настоящие интеграционные CRUD проверки.
План: IMPLEMENTATION_PLAN, продуктовый baseline: TECHNICAL_SPECIFICATION.
Не переходить автоматически к P2 из-за завершения P1.

Черновой PR: https://github.com/D1ma4ka/Kr2P1v2/pull/4
Main/AGENTS сохранены. Merge, production, покупки и публикация не выполнялись.
Debug APK и ключи не коммитятся; локальная APK/hash описаны в stages/P1.md.

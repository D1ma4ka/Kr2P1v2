# KraPiva 2.0 — P2 DONE, STOP

Дата: 5 ноября 2026. ТЗ v1.3. **P0/P1/P2 DONE; P3 NOT STARTED.**
Ветка `codex/krapiva-2-p2`. Последний проверенный код/config — `2d11942`;
Android проверен на `f81e10a` (после него Android не менялся).
Финальный коммит отчёта содержит только docs, его SHA — `git log -1`/отчёт
владельцу. Работу продолжать только после новой команды. Следующий этап P3,
включая согласованный v1.3 UI refinement; не запускать его автоматически.

## Лимиты

Последняя инструкция владельца: **недельный остаток <25% или пятичасовой <50%**
→ прекращение новых задач/сборок/тестов, безопасные commit/push/отчёт/остановка.
API текущих квот нет. Ручные сообщения этой сессии: «100 63», «66 58», «30 53».
Последние два числа без подписей сохранены буквально; автоматического контроля
нет. При последнем сообщении проверки уже завершились; дальше только сохранение.
Не переходить на платные кредиты, не создавать агентов и не начинать P3.

## Реализованная foundation

- PostgreSQL17, SQL entities §6/15, FK/check/unique/indexes, private media metadata;
  ordered/checksummed migrations с транзакциями/lock; runtime role без DDL.
- `/v1` OpenAPI, owner profile/checkin/media API, persistent hash-only bearer
  sessions и revoke/TTL, cursor limits, DB rate budget, idempotency/version conflicts.
- Photo normalization/EXIF removal/thumbnail, size/pixel limits, owner access,
  attachments/max4, deletion/tombstone/retry; files вне SQL.
- Mailpit: реальный локальный SMTP и persistent mail volume. Dev-only explicit
  synthetic accounts/mail tools; нет production seed или login imitation.
- Non-root Docker/Compose, переносимый env, проверенный restart/isolated restore,
  readiness по schema hashes/table access/media, generic errors и безопасные логи.
- Android только API_BASE_URL/окружения/debug-local network policy. UI P1 не
  переделывался. Реальный клиентский sign-up/login/API wiring — P3.

## Доказательства

- Local TypeScript build + **11/11 tests PASS**, включая новые negative DB/TLS/
  storage cases, настоящий SQL/HTTP/SMTP, IDOR/concurrency/idempotency/contract.
- GitHub backend **SUCCESS** (2d11942):
  https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42047987386
  11 tests, audit0 vulnerabilities, Docker build, migrations, startup,
  restart API/DB/Mailpit, isolated pg_restore+media copy и session/ACL/photo hashes.
- GitHub Android+source **SUCCESS** (f81e10a):
  https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42047237008
  assembleDebug/lintDebug: 0 errors, 4 version warnings; исходники/ресурсы PASS.
- После CI-only fix source **SUCCESS**, Android корректно не повторён:
  https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42047987563
- Local Android assembleDebug/lintDebug PASS, staging/release constants generation
  PASS, missing production URL и HTTP production configuration отвергаются.
- Source checker PASS: v1.0/v1.3 hashes, 180 requirements,29 screens,43 scenarios.
- Draft PR (без merge): https://github.com/D1ma4ka/Kr2P1v2/pull/5, base P1 branch.

## Оставшееся / ограничения

Открытых ошибок выполненных проверок нет. Локальный Foundation не равен продукту
или production. 500k+ registered/1M/3M — цели, capacity/load не измерялись.
P3: signup/verification/reset/rotation, полная field ACL/avatar/cover/links, клиент,
secure cache; P4–P7: полноценные catalog/social/map/badges/stats workflows.
A19 local backend PASS; A20 staging BLOCKED (нет разрешённого сервера/клиентского
E2E), остальные полные product scenarios NOT RUN. iOS P10–P13 PLANNED/MUST.
До релиза: обновление targetSdk/AndroidX и deprecated Node20 actions, совместимость,
device/TalkBack, production hardening/monitoring/retention/S3 и нагрузка P8/P9.
Исходные AGENTS/spec/assets сохранены. Ни production/merge/покупок/публикации нет.

## Продолжение без потери времени

Прочитать AGENTS, этот файл, stages/P2.md, IMPLEMENTATION_PLAN и актуальное ТЗ.
Проверить branch/HEAD/remote. **Не повторять P0–P2**; ждать разрешения на P3.
Backend README/DEPLOYMENT_RU описывают запуск и restore. Для нового задания
при необходимости пересобрать local image: он может быть старше проверенного
в GitHub кода. Не выдавать старый image за результат текущего source commit.

Local .env и закрытые synthetic token files/backups только в ignored work/;
не печатать их и не коммитить. Runtime drill создаёт новую synthetic batch на
каждый явный запуск, не зависит от вчерашней сессии. Volumes сохранены, dev
контейнеры остановлены. Не применять down -v для обычной остановки.
Подробная история предыдущих остановок: stages/P2_HISTORY.md.

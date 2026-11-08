# KraPiva 2.0 — P6 WIP, аварийная остановка

7 ноября 2026, Europe/Istanbul. Продолжение разрешено владельцем после аварийного
сохранения `b929b7e854cf79c933a8ae55729de433df265605`. P0–P5 не повторяются.
Ветка: `codex/krapiva-2-p3`. P5 baseline: `b2cc8441d3b9e0f226bf8e552a9010c6f94a46ab`.
Draft PR: https://github.com/D1ma4ka/Kr2P1v2/pull/6 — без merge.
**Работа остановлена по прямой команде владельца. P6 НЕ ЗАВЕРШЁН и не принят. P7 НЕ НАЧАТ.**

## Реализация

- PostgreSQL: поиск видимых пользователей, взаимные заявки/accept/reject/cancel,
  remove, лента друзей с фото, чужие профили и доступная зрителю статистика.
- Лайки PUT/DELETE с уникальностью, комментарии с UUID/безопасным повтором и
  удалением только своего. Keyset pagination, terminal request replay без
  восстановления удалённой дружбы, tombstone комментария без воскрешения.
- Server ACL для profile/checkin privacy, hidden/deleted/suspended, direct reads,
  comments и media. SQL locks сериализуют social writes и изменение прав/unfriend.
- Android: вход из home/profile/own checkin, поиск/заявки/друзья/лента/профиль/
  обсуждение; loading/empty/error/offline/retry, подтверждение удаления.
  KraPiva Dark, существующие компоненты и общая навигация сохранены.
- Social content/photo не записываются в disk cache; очистка stop/dispose/failure,
  повторный запрос при foreground/refresh. Черновик комментария и его retry UUID
  сохраняются в account scope при recreation; logout очищает scope.
- Additive migration008, общий OpenAPI, ADR-0007. Нет заглушек социальных операций,
  production fixtures или отдельного серверного состояния для платформ.

## Подтверждённые проверки

Backend CI https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42398072924
на `7ad1584379af9604d691e1b64c31a0277b200a3f`: **SUCCESS**.
Полный **21/21 PASS**,20.54s, npm audit **0 vulnerabilities**, non-root container,
миграции, PostgreSQL/SMTP/media restart и isolated pg_restore PASS.
Два аккаунта social test зарегистрированы через HTTP→SMTP→verification→login.
Проверены общая дружба/лента/лайки/комментарии/restart, IDOR, privacy/media,
конкурентные повторы и реальный lock wait при privacy/unfriend. Runtime restore
сохраняет social UUID/feed/counts/comment, private photo bytes и прежние session ACL.
Backend после этого прогона не менялся (кроме README).

Локальные source/design/whitespace checks PASS; это не замена runtime тестов.
Android CI https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42398404892
на `60a1d16e72204b9868ceaf3df7490faac7c57730`: последнее наблюдение до STOP —
build/lint завершены, запуск Android35 emulator. **ИТОГ НЕ ПРОВЕРЕН**.
До получения результата PASS не заявляется. Ожидается11 instrumentation tests
(прежние9 + social2), JVM8, build/lint, cold session restore и sensor check.

## Исправленные проблемы и история приёмки

- Первый Android CI42371748373: build/lint PASS, runtime7/10. Native permission
  dialog click произошёл до стабилизации; новый comment был за пределами lazy
  viewport; verify CTA перекрывался IME. Добавлены native idle/targeted retry,
  lazy scroll и явное закрытие клавиатуры без системного Back.
- Direct-entry effect сбрасывал comment draft при recreation. Теперь явный
  переход имеет token, а recreation повторяет ACL read без сброса ввода.
- Новый retention test исправлен по действующему API: POST Idempotency-Key,
  DELETE version. Серверная схема не ослаблялась.
- CI42398072939 build/lint PASS, затем отменён новым push до UI suite, чтобы
  проверить исправленный тест. Это не успешная Android-приёмка.
- Аварийная остановка сохранена коммитом b929b7e [skip ci]; разработка возобновлена
  только после новой команды владельца. Подробнее: [P6](stages/P6.md).

## Осталось и точная следующая задача

**Точная следующая задача после команды «Продолжай»: получить результат уже
запущенного CI42398404892.** Затем исправить только выявленные дефекты, проверить
P6 screenshots. После PASS обновить REQUIREMENTS_MATRIX/QA/ROADMAP/PARITY/README,
сохранить final docs/visual evidence в GitHub, сообщить SHA и **STOP перед P7**.

Physical phone/TalkBack и оптический scanner NOT RUN, RU staging/VPS не подключён.
FR-068 расширенные блокировки/жалобы SHOULD отложены; обязательная moderation
FR-090 по матрице P7/P8 не начата. iOS P10–P13 NOT STARTED; общий API совместим.
Realtime/push revoke не заявляется: доставленные bytes не отзываются, следующие
HTTP requests всегда проверяют права. Офлайн social content намеренно недоступен.

Платные ресурсы, публичный сервер, APK publish, main merge/force push не выполнялись.
Точные пятичасовые/недельные остатки недоступны; значения не выдумываются.
При сигнале владельца о ≤25%/≤50% — сохранение/commit/push/STOP.


## Аварийное сохранение

Разработка и ожидание CI прекращены по новой прямой команде владельца.
Код уже отправлен в GitHub: последний code checkpoint60a1d16e72204b9868ceaf3df7490faac7c57730.
Незакоммичены были только5 документов: README, IMPLEMENTATION_PLAN, PROGRESS,
ROADMAP и stages/P6. Они сохраняются WIP-коммитом с [skip ci], без новой сборки.
Итог существующего CI после STOP не запрашивался; новые тесты/сборки/исследования
не запускались. Подтверждение исправлений Android runtime и visual review ещё
остаются открытыми. QA/PARITY/финальные статусы матрицы нужно закончить после PASS.
Локальные ignored logs/artifacts и credentials не удалялись и не включаются в Git.
**STOP — ждать команды владельца «Продолжай».**


## Возобновление 8 ноября 2026

Продолжение разрешено владельцем. Ветка чистая, remote97d4153 совпал с local.
Получен итог существующего CI42398404892: Android10/11 PASS, оба новых social
теста PASS, включая реальный HTTP/SMTP двух аккаунтов и Activity recreation/logout.
Единственный failure — CatalogIntegrationTest: coordinate click native permission
попал в underlying manual barcode form (снимок p4-scanner-denied уже содержит
invalid error; permission grant в logcat успешен). Исправляется только test input:
реальный accessibility ACTION_CLICK кнопки native dialog вместо координат.
Добавлены explicit denied permission и отсутствие случайной barcode error assertions.
Backend21/21 и restore остаются проверенными, backend code не меняется.
Следующая задача: CI исправленного теста, visual review и окончательная документация.
Предыдущий STOP снят новой командой; P7 не начат.

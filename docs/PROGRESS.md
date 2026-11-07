# KraPiva 2.0 — P6 WIP, аварийная остановка

7 ноября 2026, Europe/Istanbul. **Разработка остановлена по прямой команде владельца.**
P6 НЕ ЗАВЕРШЁН и не принят. P0–P5 сохранены; P7 не начат.
Ветка: `codex/krapiva-2-p3`. Проверенный P5 baseline:
`b2cc8441d3b9e0f226bf8e552a9010c6f94a46ab`.
Draft PR6: https://github.com/D1ma4ka/Kr2P1v2/pull/6 — без merge.
Финальный WIP этой остановки — HEAD ветки, сообщение с `[skip ci]`.

## Сохранённая реализация

- Настоящий PostgreSQL backend: поиск видимых пользователей, взаимные заявки,
  accept/reject/cancel/remove, лента друзей, profile content/statistics по ACL,
  уникальные лайки, комментарии/create/delete с UUID и безопасными повторами.
- Additive migration008, общий iOS-neutral OpenAPI, ADR-0007. Никаких фиктивных
  социальных операций/production пользователей, старого каталога или платных сервисов.
- Проверки текущей profile/checkin privacy, hidden/deleted/suspended состояния,
  direct media и комментариев. SQL locks защищают конкурентную смену прав/unfriend.
- Android: home/profile/own-checkin переходы, поиск/заявки/друзья/чужой профиль,
  лента с фото, лайки/комментарии/delete, KraPiva Dark и состояния ошибок/сети.
  Social data/images не сохраняются в disk cache; stop/dispose/failure очищают их.
- Реальный Android test двух аккаунтов добавлен в существующий acceptance suite.

Сохранённые в GitHub контрольные точки до остановки:
`b30de95c1a75a685f43bc7de3e454009472afbfa` (backend/API/tests/ADR),
`bb0f898288d25fe893ca44db3753d1c6cb88dd31` (Android/UI/test).
Финальный WIP дополнительно сохраняет SQL concurrency hardening/tests, UI photo
loading/error и устранение no-op detail button, scroll/network ожидания теста и docs.
Файлы: backend/src/social/, backend/migrations/008_social.sql, backend/test/social.test.ts,
android/app/src/main/java/ru/krapiva/app/social/, android/app/src/androidTest/java/ru/krapiva/app/social/,
интеграционные изменения account/diary/routes/readiness, docs/api/openapi.yaml,
[ADR-0007](decisions/ADR-0007-social.md), [операции](social/OPERATIONS.md), [P6](stages/P6.md).

## Что действительно проверено до STOP

- Backend checkpoint: `npm run build`, OpenAPI generation, полный suite **20/20 PASS**
  с PostgreSQL17.7/Node24.22.1/Mailpit1.27.8. Включает19 прежних tests и новый P6.
- Два аккаунта зарегистрированы через HTTP/SMTP verification/login, дружба/лента/
  лайки/комментарии сохраняются после restart; privacy/media/IDOR/retry проверены.
- После SQL hardening: build + targeted **2/2 social tests PASS**. Реальные lock waits,
  privacy/unfriend→404 без записи, comments timestamp ties/cursor bounds/suspension.
  Полный suite после этих правок не повторялся; **21/21 не заявляется**.
- npm audit: **0 vulnerabilities**, включая полный audit до остановки.
- verify_p0/check_foundation и whitespace прошли до последних WIP правок.
- CI42371748373 на bb0f898: source и Build/lint завершились, последнее наблюдение —
  запуск Android35 emulator. Итог runtime **НЕ ПРОВЕРЕН**; повторно после STOP не читался.
  https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42371748373
- Backend workflow42371748340: integration **SKIPPED**, только changed-components
  SUCCESS. Не считать его подтверждением backend/container/restore приёмки P6.
- Последние Android WIP изменения **НЕ СОБРАНЫ/НЕ ПРОВЕРЕНЫ**.

## Осталось и известные риски

1. Получить результат уже запущенного Android CI, разобрать ошибки; P6 UI пока не принят.
2. Проверить direct-entry SocialApp при Activity recreation: LaunchedEffect(initialCheckin)
   повторно вызывает open и может сбросить ввод комментария. Runtime не проверен,
   исправление до STOP не выполнено.
3. Проверить финальное WIP состояние необходимыми backend/Android checks, включая
   миграцию/container/restore и UI двух аккаунтов; visual review/screenshots ещё не приняты.
4. Завершить обновление REQUIREMENTS_MATRIX/QA/ROADMAP/PLATFORM_PARITY/README после
   фактической приёмки. Их прежние P6 PLANNED/NOT RUN сейчас не означают PASS.
5. Physical phone/TalkBack и российский VPS NOT RUN. FR-068 расширенные reports/blocking
   SHOULD отложены; FR-090 moderation/admin по утверждённой матрице P7/P8 не начинался.

**Точная следующая задача после команды «Продолжай»: прочитать итог существующего
CI42371748373 и сопоставить ошибки с сохранённым WIP; затем проверить сохранение
ввода SocialApp при recreation. До команды не запускать разработку или проверки.**

## Ограничения и сохранность

Новые сборки/тесты после аварийной остановки не запускались; WIP `[skip ci]`.
Merge/force-push/публичный сервер/публикация APK/платные ресурсы не выполнялись.
Локальные credentials, npm cache, logs и SQL/SMTP volumes сохранены вне Git;
`work/backend-p6-tests.log` содержит результат полного20-test прогона.
Точные остатки квот недоступны, проценты не выдумываются; остановка по сигналу владельца.
Обязательный iOS P10–P13 **NOT STARTED**; следующий владелец действия — владелец проекта.
Общая KraPiva2.0 и P6 не объявляются DONE. **STOP — ждать «Продолжай».**

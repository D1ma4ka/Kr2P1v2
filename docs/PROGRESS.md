# KraPiva 2.0 — P3, приёмка в процессе

ТЗ v1.3. P0/P1/P2 DONE. P3 разрешён владельцем 5 ноября 2026,
ветка `codex/krapiva-2-p3` от `bf995357098df505965dd4425afca6f5eeff4810`.
Код и проверки сохранены в GitHub, последний checkpoint `5c9c1b6`.
Draft PR: https://github.com/D1ma4ka/Kr2P1v2/pull/6, base P2, без merge.
Ни P4, ни production/публикация/покупки не разрешены.

## Лимиты текущего запроса

Пятичасовой остаток **≤25%** или недельный **≤50%** — немедленно прекратить
разработку/новые проверки, сохранить WIP commit/push/отчёт и остановиться.
API реальных остатков нет. Владелец контролирует вручную. Значения предыдущей
P2 сессии не являются текущими остатками; из времени проценты не вычисляются.

## Текущий результат

- Реальные register/SMTP verify/login/reset/resend, scrypt, hash-only access/
  rotating refresh, revoke/TTL/replay/rate limits; additive migration003.
- Полный server profile, displayName/fields/HTTPS links, version conflict,
  avatar/cover, owner/friends/public/gender/link/media ACL и deletion request.
- Android HTTP ViewModel/forms, Keystore AES-GCM atomic session, account-scoped
  encrypted SQLite profile/photos, honest offline state/save/cancel/logout.
- KraPiva Dark, original mark/logo/icons, linear nav composition (будущие
  destinations disabled), liquid gravity/accelerometer/orientation/clamp/
  lifecycle/no-sensor/reduced-motion, без forced delay.
- Platform-neutral OpenAPI; iOS MUST P10–P13, Android не переписывается в KMP.

## Проверки и текущие ограничения

Backend local full suite **13/13 PASS**, source/asset guard PASS,
Android assembleDebug/assembleDebugAndroidTest/lint/unit PASS (2 unit tests,
0 lint errors/2 unused resource warnings). Предыдущий код b235cea прошёл
GitHub backend+container/restart/restore (42105074211) и Android/source (42105074471).
Новый CI для 5c9c1b6 ещё выполняется: backend42107791977, Android42107791966.

Local software emulator установлен и запущен; direct adb-shell позволил
поставить APK без записи в home. System UI/system ANR делают runtime недостоверным;
эмулятор остановлен. Fec9fae CI build/lint/unit прошли, emulator step BLOCKED
из-за KVM group. Новая CI конфигурация запускает только emulator process в
существующей группе устройства, не меняя ACL/accounts/security settings.
Расширенная UI suite и screenshot artifacts ещё НЕ объявлены PASS.

Физический sensor/TalkBack, RU staging и весь продукт P4–P13 ещё не приняты.
Отчёт и точная история — stages/P3.md; backend/P2 доказательства не повторяются.
Инструменты, локальные synthetic .env/token/media/logs только в ignored work/.
API/DB/Mailpit локальны; volumes не удалять (`down -v` не нужен).

## Продолжение

Дождаться конкретного CI результата, исправлять только причины провалов,
обновить матрицу/QA/P3 evidence и итоговый SHA; затем commit/push и STOP.
Завершение P3 не даёт разрешения на P4. Реальных квот не выдумывать.

# П8 — автоматическая стабилизация подготовлена · 12 ноября 2026

P8.1 добавил autosave офлайн-черновиков, сохранение черновиков владельца после
logout и обязательный `/v1/positions/similar` перед публикацией чекина. Подробности:
[P8.1 handoff](P8.1_HANDOFF.md), [stage report](stages/P8.1.md).

Выполнена отдельная ветка `codex/krapiva-p8` поверх П7 `43af2ce`. Исправлены
повторяемые чтения Android только для GET/HEAD, восстановление после повреждённой
сессии, локальный logout без сети, безопасное чтение URI и security headers API.
Публичные API, миграции и пользовательские данные не менялись.

**Backend 38/38 PASS**, Android JVM 12/12, `assembleDebug`,
`assembleDebugAndroidTest`, `lintDebug`, OpenAPI, provenance/foundation guards и
`npm audit --omit=dev` PASS. Миграции 001–010 применены/проверены в существующем
локальном volume без удаления данных. Debug APK сохранён в
`work/p8/krapiva-p8-debug.apk`; SHA-256 и ручной чек-лист — в
[P8 handoff](P8_HANDOFF.md) и [отчёте этапа](stages/P8.md).

**Статус: P8 automatic stabilization PASS; device/visual acceptance PENDING.**
Телефон, `adb`, `/dev/kvm`, instrumented/runtime, MapLibre gestures/tiles,
optical scan, TalkBack, 320dp, screenshots, staging/production и iOS в этой
среде недоступны и не объявляются пройденными. П9 не начинался.

# П7 — реализация подготовлена · 10 ноября 2026

По прямой команде владельца выполнен П7 поверх `e8f4ba6` (П6.1 candidate).
Код: `c1c51cfea4a1e99d829e983dd5aba0e5a6fe41bd`, ветка `codex/krapiva-p7`. Нативная MapLibre-карта/кластеры/bbox/список/карточки и
привязка места к чекину; 10 server-driven badge rules/прогресс/3 ordered pins/отзыв;
публичные жалобы и защищённые venue/report/role/audit API. Пять вкладок и дизайн П6.1 сохранены.

**Backend 38/38, Android JVM 12/12, debug APK/androidTest APK/build/lint PASS.**
Миграция010 применена локально после backup; isolated restore PASS, исходные
аккаунты/чекины/медиа сохранены. Production и платные сервисы не затрагивались.
MapLibre/OkHttp notices включены в APK; публичные OSM tiles только для limited dev.

**Статус: IMPLEMENTED CANDIDATE, device/visual acceptance PENDING.**
Нет KVM/подключённого устройства; native gesture/cluster runtime, обе темы,
TalkBack/320dp/offline/permission/recreate и новая Android регрессия NOT RUN.
OSM endpoint блокирует CONNECT proxy403 этой среды; доступность из РФ не проверена.
Реальный справочник не заполнен: только явные dev/testOnly fixtures, Craft Republic
без внешнего подтверждения не выдаётся за проверенное настоящее место.

[Отчёт П7](stages/P7.md), [операции и устройство](places/OPERATIONS.md),
[машинное свидетельство проверок](places/VERIFICATION.json),
[ADR-0010](decisions/ADR-0010-P7-map-badges-moderation.md).
Полная приёмка П6.1 остаётся PENDING. Исторический STOP перед П8 заменён текущей
передачей П8 выше; следующая точка — device/visual acceptance, П9 не начинать.
Ниже сохранены исторические статусы предыдущих этапов; прежний запрет начала П7
заменён новой прямой командой, их результаты не являются runtime-проверкой новой карты.

---

# П6.1 — полный кандидат реализации · 10 ноября 2026

Реализован весь согласованный объём П6.1 по новой команде владельца, без промежуточной остановки на главной. Android: `333085b`; backend: `7a16b0a`; ветка `codex/p6-1-recovery`.

- Две темы и общая навигация; каталог/коллекция/лента/профиль/форма чекина обновлены на реальных API.
- Подтверждённый сервером success, новые splash/пена/пузырьки, skip/reduced motion; 8000 уникальных сообщений.
- Подтверждение почты шестью цифрами с persistent attempt limit/expiry/email binding; перенос e-mail, совместимость прежних token API и password reset.
- Android build/test APK/lint/JVM: PASS, 10 JVM, 0 lint errors / 6 warnings. Backend: 22/22 PASS. Новый локальный HTTP→SMTP→verification→login PASS. Source/provenance/assets PASS.

**Статус: реализация кандидата подготовлена, device/visual acceptance PENDING.** Новых реальных UI screenshots нет, instrumented/TalkBack/320dp/full restart NOT RUN. Это не production/release и не завершение всего продукта. Следующий шаг — общая проверка на устройстве, затем визуальная приёмка. П7 не начинался.

Полные изменения, доказательства, APK для USB reverse и честные визуальные отклонения: [P61_COMPLETE_CANDIDATE.md](design/P61_COMPLETE_CANDIDATE.md). Ниже — история прежнего home-only состояния, а не актуальный список незавершённой реализации.

---

## История предыдущих состояний

# П6.1 — восстановление и безопасная передача · 10 ноября 2026

## Новая сессия: сборки восстановлены, Android runtime BLOCKED

Продолжено с `3122f0ca83b8f8d2173678b403befa669d21b5ac` в `codex/p6-1-recovery`. Реализация первого этапа не менялась. **Свежие `assembleDebug` и `assembleDebugAndroidTest` PASS**, обе подписи APK проверены; APK находятся в ignored `work/p61-resume/`. Все 14 макетов прошли SHA-256/size-проверку, `check_foundation.py` PASS. Backend build/migrations/readiness и HTTP login/home/feed/friends/author на новой локальной синтетической фикстуре PASS. Старых `.env`, фикстур и volumes в этой машине не было; существующие данные не удалялись.

**Runtime/визуальная приёмка NOT RUN:** нет `/dev/kvm` (включая проверку вне песочницы) и подключённого устройства. Медленный эмулятор не запускался. Нет новых runtime screenshots и доказательств успешных UI-сценариев; причина старого `Process crashed` не установлена. Lint/JVM suite повторно не запускались, код не менялся. Этап не завершён; П7 и остальные экраны не начинались. Нужна среда с аппаратным ускорением или доступное устройство для адресного `HomeIntegrationTest` и оставшихся проверок, затем screenshots обеих тем и приёмка владельца.

Новые доказательства, APK hashes, команды запуска и ограничения: [отчёт продолжения](design/p61-evidence/resume-2026-11-10.md). Ниже сохранено историческое состояние передачи.

**Текущая ветка передачи: `codex/p6-1-recovery`.** Код первого этапа уже существует в `2ba71ef0c2425960c7b50e802a36bcdfc708ed03`. Дизайн-система, обе темы, общая навигация и главная на реальных API реализованы в исходниках. **IN PROGRESS / визуальная приёмка BLOCKED**, весь П6.1 не завершён.

Полная матрица экранов, список файлов, происхождение документации, артефакты, ограничения и точка продолжения: [P6_1_HANDOFF.md](P6_1_HANDOFF.md). Все 14 утверждённых PNG сохранены в Git с [manifest](design/p61-approved/manifest.json): семь видов экранов × две темы.

Прежние build/lint/8 JVM tests PASS; инструментальная проверка оборвалась (`Process crashed`), реальных новых screenshots нет. [Доказательства](design/p61-evidence/build.log). При текущем аудите сборки, приложение и тесты не запускались; Android/backend код не менялся. Перенесена совместимая документация из docs/p6-1-design-handoff на cdb3be4; локальный отчёт сохранён без перезаписи. APK и приватные фикстуры остаются только в прежнем workspace, подробные пути и hashes — в handoff.

Следующий шаг по отдельной команде: адресная runtime/визуальная проверка уже написанной главной на исправном эмуляторе/устройстве, затем приёмка владельца. **STOP; П7 не начинать.**

---

Ниже сохранён исторический отчёт П6; его результаты не подтверждают новую реализацию П6.1.

# KraPiva 2.0 — P6 завершён, STOP перед P7

8 ноября 2026, Europe/Istanbul. **Обязательные функции P6 реализованы,
автоматическая приёмка PASS. P0–P5 сохранены. P7 НЕ НАЧАТ.**
Ветка: `codex/krapiva-2-p3`. P5 baseline: `b2cc8441d3b9e0f226bf8e552a9010c6f94a46ab`.
Draft PR: https://github.com/D1ma4ka/Kr2P1v2/pull/6 — без merge.
Последний проверенный code/test commit: `299558e530177a100275c08d15e59f5b2f3c303e`.
Финальный docs/visual-only commit с [skip ci] — HEAD; SHA сообщается владельцу.

## Что реализовано

- Настоящие PostgreSQL поиск видимых пользователей, взаимные заявки/accept/reject/
  cancel/remove, лента друзей с фото, чужие профили и доступная зрителю статистика.
- Уникальный лайк PUT/DELETE, комментарии с UUID и безопасным повтором, удаление
  только своего; cursor pagination, terminal request и comment tombstone без дублей.
- Profile AND checkin privacy, hidden/deleted/suspended, права прямого доступа к
  записям/комментариям/медиа. SQL locks для конкурентных privacy/unfriend и записей.
- Android home/profile/own-checkin entry, поиск/друзья/заявки/лента/профиль/обсуждение;
  KraPiva Dark, loading/empty/error/offline/retry, подтверждение удаления.
- Social data/photos не сохраняются в disk cache; очистка stop/dispose/failure,
  revalidation на resume/refresh. Comment draft/retry UUID переживают Activity
  recreation; явный новый переход сбрасывает ввод, logout очищает account scope.
- Additive migration008, общий Android/iOS-neutral OpenAPI, ADR-0007.
  Нет заглушек, production fixtures, платных ресурсов или отдельной БД для платформ.

## Пройденные проверки

Backend: https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42398072924
`7ad1584379af9604d691e1b64c31a0277b200a3f`: **21/21 PASS**,20.54s, audit0.
Build/OpenAPI, migrations, non-root container, PostgreSQL/SMTP/media restart и
isolated pg_restore PASS. Backend code после этого прогона не менялся.
Два аккаунта social test зарегистрированы через HTTP→SMTP→verification→login;
проверены graph/feed/like/comment/restart, IDOR/privacy/direct media, concurrent
retries и actual PostgreSQL lock wait→privacy/unfriend denial без вставки записи.
Restore сохраняет social UUID/feed/counts/comment, private photo bytes и session ACL.

Android: https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42463380380
`299558e530177a100275c08d15e59f5b2f3c303e`: **SUCCESS,11/11 device tests PASS**,
0 skipped/failed,4m55s. Build/lint/unit8/source/assets PASS. Прежние9 tests сохранены.
SocialIntegrationTest: реальные signup/SMTP/API двух аккаунтов, UI invite/accept/
remove/feed/photo/like/comment/delete, второй клиент проверяет изменения и отзывает
видимость; первый после refresh теряет доступ. Radio-off→error, чужого disk cache нет.
SocialRetentionTest: actual recreation сохраняет ввод/model, новый entry сбрасывает,
logout очищает scope. Cold process→Keystore→API profile/editing PASS. Actual sensor
motion PASS (+10/−4px). Новые тесты после успешной приёмки не запускались.

Семь P6 screenshots проверены; [baseline](design/p6-review.jpg) и
[hash manifest](design/p6-screenshot-manifest.json). Источник42398404892, social2/2
PASS; production social UI после него не менялся. Полный финальный прогон — выше.
Финальные verify_p0.py / check_foundation.py / git diff --check: PASS.

## Исправленные ошибки и сохранность

Direct-entry effect сбрасывал comment при recreation — исправлен navigation token.
UI tests: lazy comment scroll и закрытие IME; native permission coordinate click
попадал в ручную форму сканера — заменён реальным accessibility action конкретной
системной кнопки. Actual denied/granted permission и готовая камера проверяются.
Test fixture соблюдает исходный контракт POST Idempotency-Key / DELETE version.
API/ACL/assertions не ослаблялись. Все11 device tests после исправлений PASS.

Аварийные STOP сохранены b929b7e и97d4153 [skip ci]; работа продолжалась только
после новых команд владельца. Checkpoints b30de95/bb0f898/7ad1584/08386c0/60a1d16/
90f65ca/299558e отправлены в эту же ветку. Полный перечень файлов и доказательств:
[отчёт P6](stages/P6.md), [операции](social/OPERATIONS.md).

## Осталось за пределами обязательного P6

Известных незакрытых функциональных ошибок P6 в проверенных сценариях нет.
Physical phone/TalkBack/physical optical scan NOT RUN; RU VPS/staging не подключён.
FR-068 extended blocking/reports SHOULD отложены; mandatory moderation FR-090
по утверждённой матрице P7/P8. iOS P10–P13 NOT STARTED; A38 parity ещё NOT RUN.
Realtime/push revoke не заявляется: bytes уже доставленного ответа не отзываются,
каждый следующий HTTP проверяет актуальные права. Social offline намеренно закрыт.
Это не production launch и не завершение всего KraPiva2.0.

**Точная следующая задача: ждать команды владельца.** После отдельного разрешения
определить следующий этап по ТЗ/ROADMAP; P7 и VPS самостоятельно не начинать.
Публичный сервер, APK release, main merge/force push и платные сервисы не выполнялись.
Credentials, DB volumes, временные screenshots/logs/backups остаются вне Git.
Точные остатки лимитов недоступны; при сигнале ≤25%/≤50% — safe save/STOP.
**STOP — ожидание следующей команды владельца.**

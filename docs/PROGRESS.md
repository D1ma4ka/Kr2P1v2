# Чекины и уточнения UX · автоматическая приёмка

Ветка `codex/krapiva-checkin-polish`, база `72bfa63`, проверенные исходники
`52e65c7ecccdb87f8e98b83595bc4e0640ac820f`. Форматы подачи записываются
отдельно. Два чекина одного выпуска дают 2 записи и 1 уникальное пиво.
Исправлены редактирование, история поиска и уведомления. Добавлены
«В календарь», «Мои места» и сравнение упаковок. Миграция 014 добавляет
сохранённые заведения в данные аккаунта.

Android debug и инструментальный APK, lint, JVM 32/32 и backend 44/44 PASS.
Приёмка на эмуляторе и телефоне открыта. Проверки, хеш APK и точка продолжения
в [документе передачи](KRAPIVA_CHECKIN_POLISH_HANDOFF.md).

---

# Обновление Android UX · реализация и автоматическая приёмка

Ветка `codex/krapiva-ux-refresh`, база аудита `c956b2d`, проверенный
исходный коммит `6e4b11bf6795469696f62694869f5b75186ca035`.
Реализованы системная тема, крапивный фон, обновлённые поиск и личные
разделы, статистика профиля, настройки, геолокация, карта и местные
открытия. Подтверждение локальных значков выполняется сервером.

Сборка Android debug и инструментального APK, lint и JVM 31/31 PASS.
Backend 43/43 PASS на изолированных PostgreSQL и Mailpit. Визуальная и
инструментальная приёмка на устройстве открыта. Таблица девяти пунктов,
хеш локального debug APK, порядок наполнения и точка продолжения —
в [документе передачи](KRAPIVA_UX_REFRESH_HANDOFF.md).

---

# P8.4 — кандидат F1–F10 сохранён, ручная приёмка PENDING

Рабочая ветка `codex/krapiva-p8.4-offline`, база P8.3 `da92ee5`.
Каждая функция сохранена отдельным commit/push с проверкой remote SHA; есть
промежуточные checkpoints. Без force-push/main merge/изменений backend.
Android assembleDebug/lintDebug **PASS**, 27 JVM tests **PASS**,
assembleDebugAndroidTest **PASS**. Instrumented executed/визуальная приёмка/
update-over-install **PENDING**: нет устройства/KVM и прежнего debug signing key.
Новые локальные данные AES-GCM + owner scope; SQLite v3→v4 additive.
[Полный отчёт и матрица](stages/P8.4.md), [ручная приёмка и установка](P8.4_ACCEPTANCE.md),
[будущая синхронизация](P8.4_FUTURE_SYNC.md), [метаданные APK](verification/p8.4/manifest.json).
[Скачать P8.4 debug APK](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/43723838252/artifacts/14165733301) — Actions 43723838252 SUCCESS, private GitHub sign-in, до 22.11.2026 17:28 Europe/Istanbul.
Подпись отличается от скачиваемого P8.3: обновление требует прежнего ключа; данные не удалять.
Исторические результаты ниже сохранены. П9 не начинался.

---

# P8.3 A5–A10 · 15 ноября 2026

Ветка `codex/krapiva-p8.2`, база `f7de215` с завершёнными A1–A4.
Welcome использует оригинальный налив/пену/пузырьки, gravity/accelerometer и
fallback с lifecycle/reduced motion. Миграция 012 добавляет уникальный nullable
handle; UUID/hashes/сессии/история/фото/достижения сохранены. Старые аккаунты
могут выбрать handle после входа либо продолжить через «Позже».
Email/handle/@handle используют тот же scrypt и sessions. Новый пароль от 8
Unicode символов, без изменения пробелов/регистра. Усилен узор обеих тем,
порядок полей и A4 states сохранены. Код офлайн-черновиков P8.1 не менялся.

Backend 42/42, JVM 22/22, debug/test APK, lint и foundation PASS.
Device runtime/physical tilt/visual/keyboard/large font acceptance NOT RUN:
устройства нет. Все instrumented проверки только скомпилированы.
Production/внешняя почта не разворачивались, локальный Mailpit проверен.
Каждый этап commit/push. [Отчёт, APK и продолжение](stages/P8.3.md).
П9 не начат.

---

# PATCH P8.2 · 15 ноября 2026

Ветка `codex/krapiva-p8.2` от последнего `fix/auth-theme-motion` (`5771075`).
Регистрация объясняет блокирующие поля, весь ряд 18+ нажимается; пароли не
обрезаются. Восстановление через email-bound шестизначный код, с scrypt,
10 минутами, пятью попытками и resend/cooldown. Новая миграция 011 аддитивная.
Существующие hashes/legacy tokens сохраняются. Официальный logo на auth,
нижняя надпись удалена, sensor-aware пиво на реальной загрузке вместо auth.

Backend **40/40**, JVM **18/18**, `assembleDebug assembleDebugAndroidTest
testDebugUnitTest lintDebug` **PASS**, lint 0 errors/9 warnings в прежних файлах.
Runtime API с рабочей DB role: `/health/ready` 200. Письма фактически получены
в локальном Mailpit; внешняя доставка и аккаунт владельца в другой БД
**не проверены**. Android runtime/визуальная приёмка/датчик **NOT RUN**:
подключённых устройств нет, `/dev/kvm` отсутствует.

Каждый checkpoint сохранён commit/push, force-push не использовался.
Сборка `0.1.0-p8.2`, APK вне Git. [Отчёт, список файлов и продолжение](stages/P8.2.md).
Полная приёмка P8.2 остаётся открытой до проверки на устройстве. П9 не начат.

---

# Исправления авторизации, темы и motion · 14 ноября 2026

Отдельная задача владельца поверх актуального П8.1, ветка `fix/auth-theme-motion`.
Source commit `11f7b577a34187454d91e29604fdd907472bc99b`. В письме подтверждения
один шестизначный OTP; убраны технические подсказки auth и уточнён пароль.
Графитовый DARK с тонкой векторной крапивой, живое пиво на auth/loading и общий
foreground-only sensor controller с инерцией/fallback/reduced motion.

Backend **39/39**, Android JVM **17/17**, debug/test APK, lint (0 errors/7 прежних
warnings), provenance/foundation и подпись APK **PASS**. Android UI/visual/TalkBack
и физический датчик **NOT RUN**: программный эмулятор без KVM не завершил загрузку
за 10 минут, попытка остановлена. Полная Android → API цепочка не заявляется.
Исходники сохранены; APK вне Git. П9, production, платные сервисы и merge не выполнялись.

[Полный отчёт, файлы, доказательства и получение APK](stages/AUTH_THEME_MOTION.md).

---

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

## 20 ноября 2026 — аварийная остановка независимого аудита П8.4

Ветка audit/krapiva-qa-product-20261120. База продукта 83403b4. Сохранены прогон backend 42/42, 12 замечаний, матрица ТЗ, UX-аудит, реальные кадры до входа и 22 продуктовые гипотезы. Приёмка авторизованных экранов и итоговая оценка открыты. [Промежуточный отчёт](audits/KRAPIVA_FULL_QA_REPORT.md) · [Инструкция восстановления](../CODEX_RECOVERY.md). STOP до новой команды.

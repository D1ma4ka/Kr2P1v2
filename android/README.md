# Android — P3

Kotlin/Compose/Material 3, Android 8.0+ (minSdk 26), SDK35. P1/P2 сохранены.
Реальные регистрация/подтверждение/вход/сброс, profile editor, avatar/cover,
privacy, session rotation/logout/deletion request и read-only offline cache.
Все операции идут в общий backend, фиктивных ответов в приложении нет.
Catalog/checkin/social/venues — P4–P7; их нижние назначения пока disabled.

## Сборка

Полный JDK21, SDK platform35/build-tools35.0.0, Gradle wrapper8.11.1.
`ANDROID_HOME`, `GRADLE_USER_HOME`, `ANDROID_USER_HOME` могут указывать в
локальный ignored `work/`; пути не в исходниках. Proxy и доверенный CA задаются
JVM standard properties, TLS verification не отключается.

```sh
./gradlew --no-daemon assembleDebug assembleDebugAndroidTest lintDebug testDebugUnitTest
```

Package `ru.krapiva.app.debug`, version `0.1.0-p3`. APK и debug signing keys
только локально/ignored, публикации и production signing нет. Lint ошибки
блокируют сборку, baseline/suppressions не добавлялись. Актуальные результаты:
[отчёт P3](../docs/stages/P3.md), [план QA](../docs/QA_TEST_PLAN.md).

## Настоящий локальный API

Из корня: `python3 scripts/init_dev.py` (один раз), `docker compose up -d --wait db mailpit`.
Из backend/: `npm ci --ignore-scripts --no-fund --no-audit`, `npm run build`,
`node --env-file=../.env dist/src/db/migrate.js`, затем
`PORT=53000 node --env-file=../.env dist/src/main.js`.
Не показывайте .env/token/password в логах. Полный portable container запуск —
[DEPLOYMENT_RU](../docs/DEPLOYMENT_RU.md).

Debug по умолчанию `http://10.0.2.2:53000/`; host loopback переопределяется
`KRAPIVA_DEV_API_BASE_URL`. E-mail в dev — `name@example.invalid`, реальное письмо
попадает в Mailpit `http://127.0.0.1:58025`. Код вставляется в Android-форму,
не обходится локальным флагом. Staging/release требуют явных HTTPS DNS URLs
(`KRAPIVA_STAGING_API_BASE_URL`, `KRAPIVA_PROD_API_BASE_URL`), HTTP отклоняется.
Production SMTP не включён, внешним адресатам dev/test ничего не отправляют.

## Runtime проверки

С работающими API/DB/Mailpit и API35 эмулятором:

```sh
./gradlew --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true connectedDebugAndroidTest
cd ..
python3 scripts/check_android_cold_start.py
# Только изолированный emulator с synthetic test data:
adb shell pm clear ru.krapiva.app.debug
python3 scripts/check_android_motion.py
```

По умолчанию `P3AcceptanceSuite` выполняет layout/motion перед account flow.
Последний проверяет signup/18+/SMTP verify/login, save/cancel, native photo
picker cancel/select, независимые avatar/cover, view/remove (server404),
шифрование/isolation, offline/retry, server logout, reset и login новым паролем.
Сессия сохраняется для host cold check: `force-stop` → MainActivity → настоящий
API profile + enabled editing. После него очищаются только synthetic app data
для отдельной проверки accelerometer→rendered liquid slope. Нужен Pillow11.3.0.

`BeerHeroTest` проверяет active sensor handles 1→0→1→0, loop/static pixels и
left/right/static states. Synthetic screenshots сохраняются через MediaStore
в `/sdcard/Download/KraPivaP3/`; host checks — в ignored `work/screenshots/`.
CI сохраняет отчёты/PNG/contact sheet/manifest на 7 дней, без APK/ключей/.env.
Долговечный принятый baseline и ограничения — `docs/design/VISUAL_BASELINE.md`.
Точный результат не выводится из одного факта успешной компиляции тестового APK.

## Архитектура

`MainActivity` — system splash/bootstrap; `account/AccountScreen.kt` — Compose;
`AccountViewModel` — состояния; `AccountApi` — HTTP; `AccountStore` — Keystore и
SQLite. Design tokens и liquid physics — `designsystem/`, ADR-MOTION описывает
lifecycle/rotation/fallback. Все исходные field/API contracts платформенно
нейтральны; iOS обязательна P10–P13, в текущем P3 не пишется.

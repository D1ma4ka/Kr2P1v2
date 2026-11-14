# Авторизация, тёмная тема и анимация пива

14 ноября 2026, Europe/Istanbul. Отдельная команда владельца на три доработки,
не П9. Ветка `fix/auth-theme-motion`, база `2f39fa1` (код П8.1 `bd47f34`,
дополнительный upstream commit меняет только workflow выдачи APK).
Проверенный commit исходников: `11f7b577a34187454d91e29604fdd907472bc99b`.

Реализация и доступные автоматические проверки завершены. Android runtime и
визуальная приёмка **NOT RUN**: единственная попытка программного эмулятора
без KVM не завершила загрузку за 10 минут. Установка APK на устройство,
instrumented tests, реальные жесты и датчик на физическом телефоне не заявляются.

## Регистрация и шестизначный код

В исходном письме одновременно отправлялись шестизначный код и прежний
43-символьный token, тогда как Android уже ожидал шесть цифр. В новых письмах
подтверждения теперь один код: **ровно шесть ASCII-цифр**, с сохранением ведущих
нулей. Отдельные пароль аккаунта (12–128 символов) и reset-token не смешиваются
с подтверждением email. Подсказка пароля: «Пароль · От 12 символов».
Технический текст о локальной среде и `@example.invalid` убран из auth-форм.

Сохранены crypto.randomInt, scrypt-хеширование, email binding, 10 минут,
пять попыток с атомарной фиксацией в SQL, минутный cooldown и auth budgets.
Повторная отправка заменяет OTP; успешное подтверждение потребляет OTP и старые
verification tokens. Старый `/v1/auth/verify` оставлен для ранее выданных tokens,
но новые письма больше не предлагают их вводить. Новые версии используют
`/v1/auth/verify-code`. Схема/API и миграции не менялись.

Android фильтрует ввод через `EmailVerificationCode`, принимает максимум шесть
ASCII-цифр и включает подтверждение только для полного кода. Имеющийся переход
REGISTER → VERIFY → LOGIN сохраняет email. Пароль не сохраняется между этими
экранами: после подтверждения пользователь входит обычным способом.

Локальная SMTP-защита продолжает разрешать только синтетические адреса
`@example.invalid`. Production mail delivery, SMTP credentials и production
контур не изменены. Для проверки используются настоящий PostgreSQL и Mailpit.

## Тема и motion

- Тёмный фон `#1B211E`, согласованные зелёно-графитовые поверхности и границы.
  Цвета светлой палитры сохранены; общий native splash приведён к новому фону.
- `NettleBackdrop` — статические тонкие векторные листья с alpha 0.085 за
  содержимым. Узор есть только в DARK. Общая обёртка применена к auth, главной,
  профилю, каталогу, дневнику, ленте, местам, достижениям и splash.
- Сохранён существующий сосуд концепции C. `ClubBeer` теперь показывает
  живую жидкость, пену и пузырьки на welcome/login/register/verify/reset/loading.
- Общий `LiquidAnimation` обслуживает новый и прежний `BeerHero`: gravity sensor,
  fallback на accelerometer, low-pass входа, damped spring и предел ±8°.
  Без датчика сохраняются мягкие колебания и движение пузырьков.
- Частота обновления не выше 30 Гц; sensor subscription и coroutine работают
  только при RESUMED. Pause/dispose останавливают их; resume продолжает поверхность
  с прежнего угла. Respect system reduced motion и существующая кнопка skip.
  Датчики локальные, значения не логируются и не сохраняются.
- Фоновое пиво существующего checkin success сохраняет конечный reveal и не
  создаёт постоянную sensor subscription. Декорация не перехватывает ввод.

## Изменённые файлы

Полный список 31 исходного/test файла: [changed-source-files.txt](../verification/auth-theme-motion/changed-source-files.txt).

| Область | Основные файлы |
|---|---|
| OTP и письмо | `backend/src/identity/accounts.ts`, `backend/test/verification-code.test.ts`, account/social integration tests |
| Android auth | `account/AccountScreen.kt`, `account/EmailVerificationCode.kt`, `AuthFormTest.kt`, `EmailVerificationCodeTest.kt` |
| Палитра/фон | `designsystem/Theme.kt`, `NettleBackdrop.kt`, native `res/values/colors.xml`, root-обёртки существующих экранов |
| Анимация | `designsystem/LiquidAnimation.kt`, `LiquidMotion.kt`, `ClubMotion.kt`, `BeerHero.kt` |
| Проверки | `ThemeContrastTest.kt`, `LiquidMotionTest.kt`, `BeerHeroTest.kt`, `AccountLayoutTest.kt`, `P3AcceptanceSuite.kt` |

Все Kotlin-пути в таблице относительны `android/app/src/` и соответствующему
`main`, `test` или `androidTest` source set. Изменения документации: этот отчёт,
README/PROGRESS и `docs/verification/auth-theme-motion/` с фактическими журналами.

## Фактически выполненные проверки

| Команда/проверка | Результат |
|---|---|
| `cd backend && npm run build` | PASS, TypeScript |
| Targeted accounts + verification-code tests | **4/4 PASS** |
| `node --env-file=../.env --test --test-concurrency=1 dist/test/*.test.js` | **39/39 PASS**, 0 failed/skipped |
| `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` | **BUILD SUCCESSFUL**, финальный прогон 1m53s |
| JVM | **17/17 PASS**, 0 errors/failures/skipped |
| Lint | **0 errors/fatal, 7 прежних style/unused warnings** |
| `python3 scripts/verify_p0.py` / `check_foundation.py` / `git diff --check` | PASS |
| `apksigner verify --verbose`, ZIP CRC | PASS, APK signature v2 |

Backend проверяет настоящий HTTP → SQL → SMTP/Mailpit → OTP → повторный старт
сервера → login → authenticated profile; email и verified_at проверяются в SQL.
Покрыты ошибочная длина/Unicode, чужой email, leading zeroes, неверный/истёкший/
повторный OTP, concurrent attempts, resend/cooldown и старые выданные credentials.
Полная серверная регрессия также покрывает каталог, diary, social, ACL и P7.

Новые Android UI-тесты компилируются и включены в suite: форма в двух темах,
фильтрация OTP, новая регистрация через Mailpit с сохранением email и HOME,
достижимость формы 320dp/large font, fallback/reduced motion и sensor lifecycle.
**Они не запускались.** Сравнение кадров, визуальная читаемость, фактический ввод
на устройстве и влияние анимации на него остаются непроверенными.

Хронология сборок сохранена: первый общий прогон создал APK, но новый тест инерции
выявил чрезмерное затухание при смене направления. Коэффициент исправлен; второй
прогон PASS. После адресной правки плавного resume и устранения нового lint style
warning финальный прогон PASS. Успешная backend regression повторно не запускалась.

Инструменты: JDK Temurin 21.0.9 с проверенным официальным SHA-256 и `javac`,
Gradle 8.11.1 / AGP 8.9.2 / Kotlin 2.1.20, SDK/build-tools 35, Node 24.22.1,
PostgreSQL 17.7 и Mailpit 1.27.8. JDK/SDK и npm/Gradle-кэши в ignored `work/`.

## APK и получение

Точный файл в подключённой Cloud-среде:

```text
/workspace/Kr2P1v2/android/app/build/outputs/apk/debug/app-debug.apk
```

В файловой панели рабочей среды открыть этот файл и выбрать **Save as…**
в поддерживаемом desktop-клиенте. Для выдачи используется существующий результат
сборки; дополнительных assemble для копирования не требуется.

- Размер: **63 427 586 байт** (около 60.49 MiB).
- SHA-256: `4c29b48453e6a6f569b6e5da5653fc91ea3f337c900deed5295522d6b361c6be`.
- Source commit: `11f7b577a34187454d91e29604fdd907472bc99b`.
- Package: `ru.krapiva.app.debug`, minSdk 26 / targetSdk 35.
- `versionName=0.1.0-p7` — унаследованное значение build config, не номер
  исходного этапа; фактический код — П8.1 плюс текущий commit.
- [Полные метаданные и статусы](../verification/auth-theme-motion/manifest.json).

APK направлен на `http://127.0.0.1:53000/`. Для проверки на телефоне нужен backend
этой же ветки на USB-компьютере и `adb reverse tcp:53000 tcp:53000`.
Mailpit UI на этом компьютере — `http://127.0.0.1:58025`; код берётся из письма
нового синтетического email. Cloud loopback сам по себе недоступен телефону.
Развёртывание/публичный сервер не создавались. Запуск backend — существующий
[runbook](../DEPLOYMENT_RU.md), без изменения production конфигурации.

APK/ключи/credentials/AVD/DB volumes не включены в Git. Пользовательские данные
не удалялись. Платные сервисы, merge, release, production deploy и П9 не выполнялись.

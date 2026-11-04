# Android — P1

Новый Kotlin/Compose/Material 3 проект; Android 8.0+ (minSdk 26), SDK 35.
Сейчас реализованы splash, стартовый экран бренда и диалог «О KraPiva».
Нет запросов в API, авторизации, каталога или имитации социальной сети.
Сетевые и camera permissions добавляются вместе с соответствующей функцией.

## Чистая сборка

Нужны JDK 21 (полный JDK, не только JRE), Android SDK platform 35 и build-tools
35.0.0. Версии AGP/Kotlin/Compose закреплены в Gradle, wrapper 8.11.1 проверяет
SHA-256 дистрибутива. Локальный SDK задаётся через ANDROID_HOME; абсолютные пути
не входят в проект. Установка SDK выполняется стандартными Android tools.

```sh
# Из android/, после установки JDK/SDK:
./gradlew --no-daemon assembleDebug lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`; package
`ru.krapiva.app.debug`, version `0.1.0-p1`. Debug signing — автоматически
созданный локальный ключ Android tooling, не production подпись. APK/ключи
не коммитятся. Release key, публикация и магазин не настраивались.

Gradle использует максимум 2 workers, JVM heap 1536 MiB; автоматических
бесконечных повторов нет. В окружении с HTTP proxy передайте его Gradle/JVM
стандартными system properties и используйте доверенный CA; не выключайте TLS.
GitHub Actions использует предоставленный runner Android SDK и JDK 21.

## Проверки и границы

`lintDebug` останавливает сборку на ошибках (`abortOnError=true`), без baseline
или отключения отдельных проверок. Предупреждения сохраняются в полном CI
отчёте, но не повышаются до ошибок (`warningsAsErrors=false`): в P1 закреплена
связка SDK35/AGP8.9.2, а рекомендации обновить версии требуют отдельной проверки
совместимости. Перед выпуском targetSdk и зависимости необходимо актуализировать.
Начальная сборка обнаружила 7 замечаний ресурсов; они исправлены (см. P1).
Runtime на эмуляторе/телефоне и TalkBack ещё не проверены: в текущей среде нет
аппаратного ускорения `/dev/kvm`; тяжёлый software emulator в P1 не запускался.
Сборка и lint не считаются доказательством runtime-прохода пользовательских сценариев.

## Структура

`MainActivity` — bootstrap/splash. `designsystem/` — цвета, типографика, формы,
отступы. `welcome/` — текущая UI-поверхность. Текст из string resources,
фирменный знак декоративный (не дублирует TalkBack), контент прокручивается,
ширина ограничена на широких экранах, кнопки ≥48dp, шрифт в sp.
Следующие UI/state/domain/repositories/local/remote пакеты создаются при
появлении реального поведения; пустые классы и mock repositories не добавляются.

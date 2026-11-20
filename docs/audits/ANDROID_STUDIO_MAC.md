# Продолжение визуальной проверки на Mac

Используйте изолированный тестовый AVD и локальный backend. Для этого прогона подходит Android Studio с SDK Platform 35, Build Tools 35.0.0, Platform Tools и JDK 21. В Device Manager создайте образ Android 13 или новее с ABI процессора Mac (arm64-v8a на Apple Silicon, x86_64 на Intel). Ускорение эмулятора должно быть доступно через Hypervisor.Framework. Для проверки нижней границы проекта предусмотрен отдельный API 26, если доступен совместимый образ.

1. В отдельной рабочей копии перейдите на ветку аудита, сверьте её с GitHub и проверьте, что `git diff 83403b4 HEAD -- android backend` пуст. Откройте в Android Studio каталог `android`, выберите Gradle JDK 21. Для установки из терминала задайте `ANDROID_HOME` по фактическому пути SDK и добавьте `$ANDROID_HOME/platform-tools` в PATH.
2. Для нового локального стенда выполните команды ниже из корня. Нужны Docker Compose, Node 24 и Python 3. Если стенд уже подготовлен, используйте его существующие .env, Compose-проект и тома. Backend должен слушать loopback. Не переносите рабочую сессию или ключ подписи с другого устройства.
3. Запустите тестовый AVD. Соберите APK и направьте порт приложения в локальный backend. Подставьте серийный номер тестового эмулятора из `adb devices`. Откройте приложение и пройдите регистрацию с синтетическим адресом @example.invalid. Код берётся в Mailpit на http://127.0.0.1:58025. Для подготовленного аккаунта достаточно входа. Сохраняйте только метод, маршрут и статус запросов.
4. После ввода пароля сравните новый POST /v1/auth/login, GET /v1/me и GET /v1/me/home с экраном и количеством собственных записей. Снимайте экран после завершения загрузки. Запишите SHA продукта и APK, API, разрешение, плотность, тему, шрифт и состояние сети. Перед переносом кадров проверьте, что в них нет почты, пароля, кода и персональных фотографий.
5. Пройдите [F1–F10](../P8.4_ACCEPTANCE.md), включая вторую учётку. На API 33+ отдельно проверьте разрешение уведомлений, выключенный канал и реальную доставку. На макетах сравнивайте одинаковые экран, тему и наполнение. Сохраняйте результат нажатия, например восстановленный черновик, возвращённый PNG во внешнем локальном просмотрщике или счётчик после повторной отправки. [Утверждённые макеты](../design/p61-approved)

Подготовка **нового** стенда:

```sh
python3 scripts/init_dev.py
docker compose -p krapiva_qa_mac up -d --wait db mailpit
(cd backend && npm ci --ignore-scripts --no-audit --no-fund && npm run build)
(cd backend && node --env-file=../.env dist/src/db/migrate.js)
(cd backend && PORT=53000 node --env-file=../.env dist/src/main.js)
```

Сборка и установка в отдельном терминале:

```sh
curl -fsS http://127.0.0.1:53000/health/ready
(cd android && KRAPIVA_DEV_API_BASE_URL=http://127.0.0.1:53000/ ./gradlew assembleDebug)
shasum -a 256 android/app/build/outputs/apk/debug/app-debug.apk
adb devices
# Пример для единственного тестового AVD с этим номером
adb -s emulator-5554 reverse tcp:53000 tcp:53000
adb -s emulator-5554 install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n ru.krapiva.app.debug/ru.krapiva.app.MainActivity
# После входа и загрузки главной
mkdir -p work/mac-qa
adb -s emulator-5554 exec-out screencap -p > work/mac-qa/home.png
```

В случае конфликта сертификатов выберите свежий тестовый AVD. По завершении остановите API, эмулятор и контейнеры командой `docker compose -p krapiva_qa_mac stop`. Данные в томах сохраняются.

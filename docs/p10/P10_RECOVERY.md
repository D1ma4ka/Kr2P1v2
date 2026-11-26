# Восстановление P10

Работать в `p10/krapet` репозитория `D1ma4ka/Kr2P1v2`. База приложения `d20e3ec`, интеграция ресурсов `f4297b5`. Текущий опубликованный SHA проверяется командой `git ls-remote origin refs/heads/p10/krapet`.

Локальный запуск использует Node 24, PostgreSQL 17, JDK 21 и Android SDK 35. Порядок подготовки стенда описан в [DEPLOYMENT_RU](../DEPLOYMENT_RU.md). Матрица и текущие подэтапы находятся в [требованиях](P10_REQUIREMENTS.md) и [прогрессе](P10_PROGRESS.md).

Миграция 017 и серверные операции реализованы. Повторить тесты из backend: `npm run build` и `node --env-file=../.env --test --test-concurrency=1 dist/test/*.test.js`. Следующая точка — проверка нативного клиента и импорт стартового каталога.

После миграции 018 импортировать [каталог](P10_ADD_NEW_ITEM.md). Для проверки QR создать одноразовый тестовый набор по [инструкции](P10_ADD_PARTNER_EVENT.md).

Для Android используется вход по существующему API. Собрать из `android/`: `./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`. Debug-адрес эмулятора по умолчанию `http://10.0.2.2:53000/`. Запустить API с `PORT=53000` и применёнными миграциями.

Инструментальный сценарий `ru.krapiva.app.pets.PetAcceptanceTest` запускается только на тестовом окружении. Подготовить QR в файл, затем из корня выполнить `node --env-file=.env tools/p10/device-fixture.mjs путь-к-коду` и передать `work/p10-device-fixture.json` на устройство по адресу `/data/local/tmp/p10-fixture.json`. Это локальный файл с синтетическими данными доступа. После установки обоих APK выбрать класс через `adb shell am instrument -w -r -e class 'ru.krapiva.app.pets.PetAcceptanceTest#realRoomCatalogWalletAndPartnerReward' ru.krapiva.app.debug.test/androidx.test.runner.AndroidJUnitRunner`.


Для APK из [GitHub Actions](https://github.com/D1ma4ka/Kr2P1v2/actions/workflows/p10-debug-apk.yml) адрес сервера — loopback устройства. После запуска backend на рабочем компьютере выполните `adb reverse tcp:53000 tcp:53000` и установите APK через `adb install -r KraPiva-P10-debug.apk`. На эмуляторе работает тот же проброс. Настройки production хранятся отдельно от debug-сборки. Архив Actions содержит SHA-256 и SHA исходного коммита.


## Запуск чистого локального стенда

Из корня репозитория выполните:

```sh
python3 scripts/init_dev.py
docker compose up -d --wait db mailpit
cd backend
npm ci
npm run build
node --env-file=../.env dist/src/db/migrate.js
node --env-file=../.env dist/src/operations/pet-content.js ../assets/pets/items/catalog.json
PORT=53000 node --env-file=../.env dist/src/main.js
```

Проверьте `http://127.0.0.1:53000/health/ready`. Локальное подтверждение регистрации доступно через Mailpit по адресу `http://127.0.0.1:58025/`. Для реального телефона требуется USB-отладка и проброс порта. Подтверждённые данные KraPet используют общий шифрованный кэш Android и его срок хранения 24 часа.

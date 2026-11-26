# Восстановление P10

## Повторная приёмка после `85fe55c`

Стартовая точка `85fe55cd018ba7d2dbe07d6a8fa0868642ebd802` совпала с удалённой веткой. Исправление приложения находится в `981965b4b78a431978cca5c25884f8813a30374f`. Финальная сборка и инструментальный прогон относятся к `8c18716fc82cc3f584d9b29d2bff384920595ea5`. В этот коммит входит поправка реакции после выхода от стола. GitHub Actions завершил `realRoomCatalogWalletAndPartnerReward` на API 35 при 320 dp с настоящим PostgreSQL. На API 26 отдельно прошёл `equippedRoomRendersAllFiveItems`. Результаты, снимки и открытые проверки перечислены в [отчёте QA](P10_QA_REPORT.md).

В архиве `KraPiva-P10-debug` лежит `KraPiva-P10-debug.apk` с SHA-256 `1da76102609a6624dd9342e5824c07a2303132ed99f4424881865ddced906b55`. Сумма, подпись и выравнивание проверены после скачивания. В архиве есть `source-commit.txt` и `SHA256SUMS.txt`. [Скачать APK из GitHub Actions](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/45896927244/artifacts/15154496584)

При следующей приёмке на телефоне проверьте напечатанный QR и обычный GTIN, плавность, память и батарею. Для воспроизведения используйте раздел чистого запуска ниже, затем подготовьте новый синтетический аккаунт. Матрица отмечает 13 полностью закрытых строк из 18. [Матрица](P10_REQUIREMENTS.md)

## Точка остановки по просьбе владельца · 26 ноября 2026

Код остановлен в ветке `p10/krapet` после `e9ac6055519a92e365c04d39b40ec82b522d23cf`. Последнее изменение заменяет предварительную подгрузку коротких горизонтальных списков обычной прокруткой. Backend, Android-клиент, принятые исходники графики, каталог, анимации и снимки сохранены.

Подтверждены 52 серверных теста, 48 JVM-тестов, 11 тестов рига, локальная сборка debug/test APK и `lintDebug`. На предыдущем прогоне API 26 пройдены покупка, угощение, тестовый QR и восстановление комнаты. Дополнительная проверка показала работу офлайн и комплект из пяти предметов в обеих темах.

В финальном повторе с новым синтетическим аккаунтом на `e9ac605` пройдены вход, выбор питомца, публикация и её повтор, затем приложение вернулось в KraRoom. Подтверждения полного прохождения повторного прогона к моменту остановки нет. Продолжить с проверки 2 KC, магазина, угощения, тестового QR и восстановления на этой версии. Открыты проверки физической камеры, метрики слабых устройств и визуальный аудит всех танцевальных и суставных поз и сочетаний одежды.

Последний локальный APK и test APK сохранены вместе с SHA-256 в `/workspace/work/p10-checkpoint/`. [Сборка GitHub Actions из e9ac605](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/45891119804) запущена и к моменту фиксации ещё не предоставила архив. [Готовый APK из 0126917](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/45890268754/artifacts/15150509995) доступен для скачивания.

Работать в `p10/krapet` репозитория `D1ma4ka/Kr2P1v2`. База приложения `d20e3ec`, интеграция ресурсов `f4297b5`. Текущий опубликованный SHA проверяется командой `git ls-remote origin refs/heads/p10/krapet`.

Локальный запуск использует Node 24, PostgreSQL 17, JDK 21 и Android SDK 35. Порядок подготовки стенда описан в [DEPLOYMENT_RU](../DEPLOYMENT_RU.md). Матрица и текущие подэтапы находятся в [требованиях](P10_REQUIREMENTS.md) и [прогрессе](P10_PROGRESS.md).

Серверные миграции 017–019 и нативная KraRoom готовы к запуску по этой инструкции. Повторить тесты из `backend/`: `npm run build` и `node --env-file=../.env --test --test-concurrency=1 dist/test/*.test.js`. Открытые проверки и ссылки на артефакты находятся в [отчёте](P10_QA_REPORT.md).

После миграции 018 импортировать [каталог](P10_ADD_NEW_ITEM.md). Для проверки QR создать одноразовый тестовый набор по [инструкции](P10_ADD_PARTNER_EVENT.md).

Для Android используется вход по существующему API. Собрать из `android/`: `./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`. Debug-адрес эмулятора по умолчанию `http://10.0.2.2:53000/`. Запустить API с `PORT=53000` и применёнными миграциями.

Инструментальный сценарий `ru.krapiva.app.pets.PetAcceptanceTest` запускается только на тестовом окружении. Подготовить QR в файл, затем из корня выполнить `node --env-file=.env tools/p10/device-fixture.mjs путь-к-коду` и передать `work/p10-device-fixture.json` на устройство по адресу `/data/local/tmp/p10-fixture.json`. Это локальный файл с синтетическими данными доступа. После установки обоих APK выбрать класс через `adb shell am instrument -w -r -e class 'ru.krapiva.app.pets.PetAcceptanceTest#realRoomCatalogWalletAndPartnerReward' ru.krapiva.app.debug.test/androidx.test.runner.AndroidJUnitRunner`.

Для APK из [GitHub Actions](https://github.com/D1ma4ka/Kr2P1v2/actions/workflows/p10-debug-apk.yml) адрес сервера — loopback устройства. После запуска backend на рабочем компьютере выполните `adb reverse tcp:53000 tcp:53000` и установите APK через `adb install -r KraPiva-P10-debug.apk`. На эмуляторе работает тот же проброс. Настройки production хранятся отдельно от debug-сборки. Архив Actions содержит SHA-256 и SHA исходного коммита.

Если Android сообщает о несовпадении подписи с установленной debug-версией, используйте отдельный тестовый эмулятор или соберите обновление прежним ключом подписи. Это сохраняет данные установленного приложения.

## Запуск чистого локального стенда

Из корня репозитория выполните:

```sh
python3 scripts/init_dev.py
chmod 644 infra/postgres/01-runtime-role.sql
docker compose up -d --wait db mailpit
cd backend
npm ci
npm run build
node --env-file=../.env dist/src/db/migrate.js
node --env-file=../.env dist/src/operations/pet-content.js ../assets/pets/items/catalog.json
PORT=53000 node --env-file=../.env dist/src/main.js
```

Проверьте `http://127.0.0.1:53000/health/ready`. Локальное подтверждение регистрации доступно через Mailpit по адресу `http://127.0.0.1:58025/`. Для реального телефона требуется USB-отладка и проброс порта. Подтверждённые данные KraPet используют общий шифрованный кэш Android и его срок хранения 24 часа.

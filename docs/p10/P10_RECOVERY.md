# Восстановление P10

## KraRoom · Точка передачи · 27 ноября 2026

Рабочая ветка `p10/kra-room-refinement` репозитория `D1ma4ka/Kr2P1v2`. База — `6d6cba1a5bdef75dee25832df1ced6fbd1de1803` из `p10/krapet`. Android-проверки и новые снимки относятся к `9e74245b8bbec246e87354efbd8a1594ef2afa0a`. Состояние ветки со снимками проверьте командой `git ls-remote origin refs/heads/p10/kra-room-refinement`. [GitHub Actions](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/46060105747) · [Протокол](evidence/kra-room-refinement/instrumentation.txt) · [Снимки](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/46060105747/artifacts/15227869484)

Сборка `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` и проверка подписи и выравнивания прошли. Для повторной визуальной приёмки используйте порядок в workflow. Сначала тест `eachApprovedStarterCanBeAdopted` создаёт питомцев на пяти синтетических аккаунтах. Затем `node --env-file=.env tools/p10/device-fixture.mjs --fund-starters` пополняет их кошельки на изолированном стенде. Сценарий `fittedRoomsShowRealWardrobeAndDecorInBothThemes` покупает и размещает предметы через API и сохраняет снимки. [Workflow](../../.github/workflows/p10-debug-apk.yml)

Физическая камера и замеры слабого телефона остаются открыты с предыдущей приёмки. [Аппаратная проверка](P10_HARDWARE_QA.md)

## Текущая точка остановки · 27 ноября 2026

Рабочая ветка `p10/krapet` репозитория `D1ma4ka/Kr2P1v2`. Последний APK относится к коммиту `b2319610fd8c05a9c32ae8e14602324ab2fd5571`, SHA-256 `408cbdb9aaaf56292eb2a7f54597a03d13f7c3f37f4f842b3ee62b0bd74ac6fb`. Полностью закрыты 16 из 18 строк. Доказательства проверок для этой сборки перечислены в отчёте. [APK](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/46039256659/artifacts/15219418194) · [Протокол пакета](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/46039256659/artifacts/15219393018) · [Отчёт QA](P10_QA_REPORT.md)

Локальный стенд остановлен. Тома `krapiva_p10_db-data` и `krapiva_p10_mail-data` сохранены. На физическом телефоне нужны проверка напечатанных QR и GTIN и замеры слабого устройства. [Аппаратная приёмка](P10_HARDWARE_QA.md) · [Матрица](P10_REQUIREMENTS.md)

Для полного повторного инструментального прогона создайте новый синтетический набор с флагом `--all-starters` у `tools/p10/device-fixture.mjs`. Повторную выдачу проверяйте после прохождения выбора всех пяти питомцев. Сценарий холодного запуска выполняется после основного и проверки тем с временно отключённым `adb reverse`. После него проброс порта восстанавливается. [Порядок сценариев](../../.github/workflows/p10-debug-apk.yml)

## Предыдущая точка остановки · 27 ноября 2026

Рабочая ветка `p10/krapet` репозитория `D1ma4ka/Kr2P1v2`. Приёмка начата с `ca633ea5d6fc96665938e67db3b2aced275fa92a` после `git fetch`. Зафиксированы посадка одежды и аксессуаров, контраст системной строки и `celebrate` при первой партнёрской выдаче.

`KraPiva-P10-debug.apk` собран из `5e123e171569a82a18148f74d03c54e6f39d30b8`. SHA-256 APK `8d2e543f758b1f5dfab53d729414ec957c058d969d77a69118016cae627a8d53`. В скачанном из Actions архиве сверены `source-commit.txt`, подпись, выравнивание и сумма. Этот APK прошёл оба сценария `PetAcceptanceTest` на API 35 при 320 dp с настоящим PostgreSQL. [Скачать APK](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/45998540519/artifacts/15196988697) · [Протокол пакета](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/45998540519/artifacts/15197496863) · [Отчёт QA](P10_QA_REPORT.md)

Повторный прогон выполняйте на свежем синтетическом аккаунте. После `realRoomCatalogWalletAndPartnerReward` на API 35 запустите `systemStatusBarMatchesRoomThemeAcrossRecreation` с тем же `/data/local/tmp/p10-fixture.json`. Первый сценарий сохраняет светлую тему для обычного запуска во втором. Оптические QR и GTIN, холодная загрузка, плавность, память, фон и батарея требуют физического телефона. Замеры программного API 26 сохранены в отчёте. [Матрица](P10_REQUIREMENTS.md)

Удалённая ветка проверяется через `git fetch origin` и `git ls-remote origin refs/heads/p10/krapet`. Локальный стенд остановлен, тома `krapiva_p10_db-data` и `krapiva_p10_mail-data` сохранены. Запустите их через `docker compose -p krapiva_p10 up -d --wait db mailpit` и обновите каталог текущим импортом. Команды для нового стенда приведены ниже.

## Предыдущая приёмка после `85fe55c`

Стартовая точка `85fe55cd018ba7d2dbe07d6a8fa0868642ebd802` совпала с удалённой веткой. Исправление приложения находится в `981965b4b78a431978cca5c25884f8813a30374f`. Сборка и инструментальный прогон этого этапа относятся к `8c18716fc82cc3f584d9b29d2bff384920595ea5`. В этот коммит входит поправка реакции после выхода от стола. GitHub Actions завершил `realRoomCatalogWalletAndPartnerReward` на API 35 при 320 dp с настоящим PostgreSQL. На API 26 отдельно прошёл `equippedRoomRendersAllFiveItems`. Результаты и снимки перечислены в [отчёте QA](P10_QA_REPORT.md).

SHA-256 APK этой сборки `1da76102609a6624dd9342e5824c07a2303132ed99f4424881865ddced906b55`. [Предыдущий APK](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/45896927244/artifacts/15154496584)

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

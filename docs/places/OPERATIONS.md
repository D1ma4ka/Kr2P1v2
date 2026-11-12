# П7: локальная проверка карты, достижений и модерации

Только development/test. Production, деплой и платные API не запускались.
П8 automatic stabilization выполнена локально; device/visual/staging acceptance
остаётся PENDING. Актуальная передача: [docs/P8_HANDOFF.md](../P8_HANDOFF.md).
Архитектурные решения и ограничения: [ADR-0010](../decisions/ADR-0010-P7-map-badges-moderation.md).

## Запуск

Использовать существующую `.env`, не печатать её и не перезаписывать. Node 24,
PostgreSQL 17, Mailpit; базовый runbook — [DEPLOYMENT_RU](../DEPLOYMENT_RU.md).
Перед миграцией сохранить DB/media; применять миграции от migration role,
запускать HTTP от runtime role. Из `backend/`:

```sh
npm ci
npm run build
node --env-file=../.env dist/src/db/migrate.js
PORT=53000 node --env-file=../.env dist/src/main.js
```

Миграция 010 additive: не заменяет существующие таблицы/UUID. Старые координаты
без `coordinates_source` автоматически не объявляются проверенными и не попадают
на карту. Их можно подтвердить защищённым versioned PUT. Для появления настоящих
заведений оператор должен внести проверенные данные с источником. Production seed
пуст; ни реальные компании, ни фотографии, ни рейтинги не выдуманы.

Явная dev-fixture из корня (после backup и миграции, только loopback dev DB):

```sh
node --env-file=.env scripts/prepare_p7_fixture.mjs
```

Скрипт отказывается перезаписывать `work/p7/android-fixture.json` (0600, ignored),
создаёт отдельный тестовый аккаунт, пять синтетических позиций и 12 testOnly мест
для clustering. Это не настоящие организации и не геодезически проверенные точки.
Пример Craft Republic из FR-109 сохранён отдельно как **testOnly без координат/фото**:
его текущий адрес/существование внешне не подтверждены. Public production API
не показывает testOnly и не разрешает прикреплять их к новым чекинам.
Fixtures не вызываются миграциями/startup; существующие данные не удаляются.

## Android и тайлы

MapLibre Native 11.13.5 / BSD-2-Clause. SDK инкапсулирован в `NativeVenueMap.kt`.
Debug использует `https://tile.openstreetmap.org/{z}/{x}/{y}.png` **только для
ограниченной ручной локальной проверки**, с attribution, app User-Agent, HTTP cache
32 MiB и `prefetchZoomDelta=0`. Нет bulk/offline-download API. Соблюдать актуальную
[OSMF Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/).
Авторство данных — [OpenStreetMap contributors / ODbL](https://www.openstreetmap.org/copyright).
SDK/данные/тайловый сервис имеют разные лицензии; MapLibre не предоставляет тайлы.

Настройки сборки:

| Переменная | Использование |
|---|---|
| KRAPIVA_DEV_MAP_TILES | Debug HTTPS z/x/y template, default ограниченный OSM dev |
| KRAPIVA_DEV_MAP_ATTRIBUTION | Видимая attribution debug-провайдера |
| KRAPIVA_MAP_TILES / KRAPIVA_MAP_ATTRIBUTION | Явно разрешённая собственная/лицензированная подложка staging/release; default пуст |
| KRAPIVA_DEV_API_BASE_URL | Default emulator `http://10.0.2.2:53000/`; phone `http://127.0.0.1:53000/` с adb reverse |

Публичный OSM endpoint запрещён как staging/release fallback. При отсутствии
конфига или ошибке подложки UI показывает объяснение и рабочий API-список.
Платный провайдер/ключ не подключён. Доступность из РФ и production SLA не проверены.
В этой облачной среде запрос к tile.openstreetmap.org блокирует CONNECT proxy403;
это не доказательство недоступности сервиса в России. Не обходить policy среды.

Для устройства с локальным backend на той же машине, где подключён USB:

```sh
cd android
KRAPIVA_DEV_API_BASE_URL=http://127.0.0.1:53000/ ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb reverse tcp:53000 tcp:53000
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb push ../work/p7/android-fixture.json /data/local/tmp/p7-android-fixture.json
adb shell am instrument -w -e class ru.krapiva.app.places.P7IntegrationTest ru.krapiva.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Cloud loopback не является адресом backend на компьютере владельца: нужен локальный
запуск этой ветки или отдельно разрешённый защищённый туннель. Команда install здесь
документирует будущую проверку, в облаке она не выполнялась. Не запускать старую полную
эмуляторную suite на личном телефоне: в ней есть emulator-specific guards/настройки.
Тесты используют только `example.invalid` аккаунт и синтетические чекины из fixture.
Файл содержит пароль тестового аккаунта: не коммитить, не отправлять в логи.

В UI: Главная/Коллекция/Профиль → «Места и карта». Ручной город доступен без location
permission; запрос coarse location возникает только по «Рядом со мной» и прекращается
после результата/таймаута. Фильтры общие для списка/карты. «Список в области карты»
можно снять, чтобы найти места без координат. Кластер показывает фактическое число
точек SDK и раскрывается нажатием. Метка → краткая карточка → «Подробнее» → страница
с provenance/реальными viewer-visible агрегатами → «Чекин в этом месте» → позиция.
В редакторе можно выбрать другое известное место или неподтверждённый текст.

Ручные A17: отклонить разрешение, выбрать город; выключить сеть/тайлы и убедиться
в ошибке и fallback-списке; вернуть сеть и повторить запрос; проверить обе темы,
320dp, крупный шрифт, TalkBack, background/recreate и сохранность черновика.
Эти проверки пока **NOT RUN**. Не заменять их статичной картинкой или компиляцией.

## API

Все маршруты требуют действующую bearer session; общие коды ошибок и schemas —
[OpenAPI](../api/openapi.yaml). `id` — UUID. Не печатать bearer tokens.

| Маршрут | Операция |
|---|---|
| GET /v1/venues | `q`, `city`, `type=bar/beer_shop`, optional bbox, limit≤50, scoped cursor |
| GET /v1/venues/map | bbox обязателен; только verified coordinates/provenance; ≤500 + truncated |
| GET /v1/venue-cities | Подтверждённые города, count и рассчитанный центр; ≤50 |
| GET /v1/venues/:id | Адрес/источники/разрешённое фото, рейтинг только из видимых чекинов |
| GET /v1/me/badges | Все активные правила и подтверждённый сервером прогресс |
| PUT /v1/me/badge-pins | `{ids:[…]}` — ≤3 earned UUID в порядке отображения |
| GET /v1/users/:id/badges | Только earned pins после profile ACL; чужой полный прогресс не раскрывается |
| PUT /v1/reports/:id | `{subjectType,subjectId,reason}`, idempotent UUID; изменённый retry409 |
| GET /v1/moderation/venues | Moderator/admin, paged q/city/type/bbox/status |
| GET /v1/moderation/venues/:id | Moderator/admin: pending/verified/hidden metadata/version |
| PUT /v1/moderation/venues/:id | Moderator/admin: создать version0 или изменить текущую version |
| GET /v1/moderation/reports | Moderator/admin, status pending/resolved/rejected, keyset |
| POST /v1/moderation/reports/:id/decision | Moderator/admin: `{version,action:hide/restore/dismiss,reason}` |
| GET /v1/moderation/audit | Moderator/admin, optional entityId и keyset |
| POST /v1/moderation/users/:id/role | Admin only: `{expectedRole,role,reason}`, без self-change |

Venue PUT требует все поля из OpenAPI: name/type/city/country/address/source,
latitude/longitude/coordinatesSource (парные null допустимы), imageAssetId/imageSource,
rightsConfirmed, testOnly, verified, version. Нельзя прикрепить произвольное чужое
фото: новое должно принадлежать редактору; другой модератор может сохранить уже
одобренное фото этой карточки. Без подтверждения прав изображение отклоняется.
Скрытая карточка восстанавливается решением по жалобе, не обычным PUT.

Жалобы только на доступный публичный user/checkin/comment/venue/position; лимит
20 новых жалоб/час/user плюс общий API budget. Hide/restore/dismiss и изменения
venues/roles записываются в audit. Restore требует hold прежнего состояния и не
воскрешает deleted/deletion-request/merged/pending контент. User hide отзывает
сессии; restore не активирует отозванные сессии. Возвращённый пользователь входит заново.
Для позиции используется существующий непубличный `rejected`, для места `hidden`;
чекины/комментарии используют `hidden_at`. Назначать реальные роли через public UI нельзя.

Расчёт значков lazy перед badge/stat reads/pins, в БД с user advisory lock. Цели
версии1 описаны в ADR; можно добавлять правила существующих метрик без изменения
клиента. Прогресс capped target; удаление/скрытие/изменение основания отзывает earned
и pins при следующем подтверждённом чтении. Отключённые правила исключаются из
выдачи и статистики; старые закрепления автоматически не возвращаются.

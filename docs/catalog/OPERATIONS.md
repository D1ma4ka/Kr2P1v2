# P4 — каталог и модерация

Все маршруты требуют bearer-сессию активного пользователя 18+.
Полный контракт: [OpenAPI](../api/openapi.yaml), решение: [ADR0005](../decisions/ADR-0005-catalog.md).
Каталог production начинает пустым; автоматических seed/import/network lookup нет.

## Чтение и поиск

- `GET /v1/positions`: `q` (до 120 знаков, до восьми слов), `breweryId`,
  `styleId`, `packaging`, `abvMin`, `abvMax`, `sort=newest|name|abv`,
  `limit=1…50`, `cursor`. Все слова должны встретиться в нормализованном общем
  тексте name/brewery/style; порядок слов не важен. NFKC/lower/ё→е/пунктуация.
- `GET /v1/positions/{id}` возвращает canonicalId и requestedId. Merged ID
  продолжает работать; pending/rejected невидимы. Не кэшировать редирект навсегда.
- `GET /v1/breweries`, `GET /v1/breweries/{id}` и
  `GET /v1/breweries/{id}/positions`; список её позиций использует тот же поиск.
- `GET /v1/styles` — только стили, реально присутствующие в проверенном каталоге.
  Никаких фиктивных styles/countries/images для неизвестных значений.

`nextCursor=null` завершает список. Для позиции курсор связан с запросом,
смена фильтров требует сброса; чужой/повреждённый cursor → 400. UUID разрешает
одинаковое имя/ABV/время без потерь на границе страниц. ABV null показывается как
неизвестное, не как ноль; при sort=abv идёт перед известными значениями, при
ABV-фильтре исключается. Редактирование сортируемых полей между страницами может
переместить запись: обновить список, клиент удаляет повторы по id. Это не snapshot
транзакция на время пользовательского просмотра. OFFSET и total-count не нужны.

## Предложение → проверка

`POST /v1/position-proposals`: name/breweryName/source обязательны,
JSON body limit64KiB для предложений (учтён UTF-8), поля по схеме ограничены.
requestId — клиентский UUID для безопасного повтора. Неизвестные данные — null
или пропущены; ABV 0…100, IBU 0…1000, volumeMl 1…100000. breweryId может ссылаться
на уже проверенную пивоварню; brand отдельно от brewery и style.

`GET /v1/me/position-proposals` показывает только собственные заявки/решения.
`PATCH /v1/position-proposals/{id}` разрешён автору только pending с текущей
version. Предыдущая версия сохраняется в proposal_revisions. 409 сохраняет форму
у пользователя; обновить список, повторно открыть заявку и сверить изменения.
Нельзя выдавать принятие заявки за проверку и публичное создание позиции.

Фото сначала `POST /v1/media`; прикрепление требует imageAssetId, imageSource,
rightsConfirmed=true. Нормализация/ограничения/EXIF удаления прежнего media pipeline
сохраняются. До принятия файл приватен. Внешние URL не скачиваются. В карточке
выдаётся только approved, не deleted/hidden, привязанное к verified объекту фото.
`PUT /v1/moderation/breweries/{id}/image` позволяет модератору прикрепить собственную
загрузку с источником и подтверждением прав. Без фото — собственный KraPiva mark.

## Защищённые операции модератора

Роль moderator/admin проверяется в SQL на каждом запросе; пользователь не может
назначить её себе через API. Назначение реальных ролей и production deployment
требуют отдельного решения владельца. Dev CI создаёт исключительно synthetic
moderator в локальной БД; не копировать его учётные данные в staging/production.

1. `GET /v1/moderation/position-proposals` — курсорная очередь pending.
2. `GET /v1/moderation/position-proposals/{id}/duplicates` — до 20 кандидатов по
   нормализованным названию+пивоварне; сверить packaging/volume/edition/variant.
3. `POST /v1/moderation/position-proposals/{id}/decision`: version, reason,
   action `accept|reject|link`. Accept создаёт проверенные brewery/release/position
   транзакционно. При существующей пивоварне выбрать breweryId, при том же выпуске
   releaseId. Кандидат в дубли требует link к positionId или явного allowDuplicate
   с причиной различия. Разные реальные упаковки не объединять автоматически.
4. `POST /v1/moderation/positions/{id}/merge` с targetId/reason — явное ручное
   слияние дублей. Checkins/wishlist/mappings/proposals переходят к canonical ID;
   версии чекинов увеличиваются, aliases остаются; циклы отклоняются. ServingFormat
   не создаёт новую позицию и не является основанием для искусственного дубля.

Решения, причины и actor сохраняются в audit_events; произвольный пользователь
не может читать аудит или очередь. Конфликт verified GTIN → 409 и rollback всей
операции, без частично созданной позиции. Повтор уже принятого решения → 409.
Модерация serializes через transaction advisory lock; для больших потоков P8
следует измерить contention перед изменением locking, не удалять ограничения.

## Коды

`GET /v1/barcodes/lookup?kind=gtin&code=...` принимает EAN8/UPC-A/EAN13/GTIN14,
валидирует ASCII digits и контрольную цифру и дополняет нулями слева до GTIN14.
Примеры checksum живут в unit tests; это не seed-каталог.
Camera UPC-E раскрывается ZXing в UPC-A до общей проверки.

Состояния: found (с canonical position), not_found, non_beer с источником,
unsupported_qr. Некорректная длина/checksum → 422 INVALID_GTIN. Сетевая ошибка
не является not_found. Непроверенные mappings не участвуют в поиске.

`kind=qr` всегда отдельное unsupported_qr; цифровой QR не считается GTIN,
URL не запускается. Android распознаёт unsupported_qr локально: содержимое QR
не отправляется на сервер. QR business payload/schema не утверждены: отдельный контракт
в будущем, не создавать 3 млн реальных QR или сущность без спецификации.

Только moderator/admin может записать доказанный non-beer:
`PUT /v1/moderation/barcode-exclusions` с code/source; существующий beer mapping
защищён 409. `DELETE /v1/moderation/barcode-exclusions/{code}` с reason исправляет
решение; обе операции аудируются. Unknown никогда автоматически не становится non-beer.

## Android и офлайн

Каталог доступен из нижней навигации. Поиск и фильтры выполняются сервером;
LazyColumn + keyset page20, карточки, пивоварни, собственные заявки и photo picker.
Камера запрашивается только в сканере, обязательного camera hardware нет;
отказ/закрытие/ошибка оставляют ручной ввод и поиск. CameraX lifecycle,
KEEP_ONLY_LATEST, frame.close в finally, ограничение обработки ~5fps, ZXing
встроен (нет платного SDK/model download). Decoder проверен на четырёх ориентациях.

AccountStore v1→v2 добавляет зашифрованный AES-GCM SQLite read-cache: до 120
страниц/карточек, до 100 thumbnails, TTL карточек 24h. Данные привязаны к userId,
logout/switch очищает диск и отдельный ViewModelStore; поздняя фото-загрузка не
может заново записать кэш другого аккаунта. Offline read помечен, статус мог
измениться; barcode lookup требует сети. 403/404 удаляет соответствующую копию.
Автоматической очереди публикации и сообщения об успехе при offline нет.
Фото и личные данные, переданные тестами, не входят в Git.

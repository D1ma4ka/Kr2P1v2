# P5 — дневник и пользовательские расчёты

Решение: [ADR-0006](../decisions/ADR-0006-diary-statistics.md).
HTTP/JSON/UUID контракт один для Android/iOS, bearer активного аккаунта обязателен.

| Маршрут | Назначение |
|---|---|
| POST /v1/checkins | Повтор разрешён; Idempotency-Key UUID отличает новую запись от retry |
| GET /v1/me/checkin-requests/{requestUUID} | Owner-only восстановление resource ID после потери ответа; неизвестный → checkinId:null |
| GET/PATCH/DELETE /v1/checkins/{id} | Только owner, optimistic version; чужое/hidden/deleted →404, старая версия→409 |
| GET /v1/checkins | Только свой дневник: q/name/body/style, styleId, breweryId, positionId, from/to, ratingMin/Max1–20, unrated, hasPhoto, venueId/venue, sort, limit≤50/cursor |
| GET /v1/taste-tags | 12 субъективных labels/UUID из SQL, не свойства пива |
| GET /v1/me/unique-positions | DISTINCT positionId: q/styleId/unknown/breweryId, sort=name/latest/rating, limit/cursor |
| GET /v1/me/wishlist | Собственный список, те же фильтры и stable pagination |
| GET/PUT/DELETE /v1/me/wishlist/{positionId} | Canonical alias, уникальная пара owner+position, идемпотентные writes |
| GET /v1/me/statistics | Total/distinct/rated/unrated/sum/average,20 rating bins, styles/breweries by distinct position, реальные earned badges |
| GET /v1/me/home | Реальные personal statistics + последние5 чек-инов |
| GET /v1/positions/{id}/statistics | visibleToViewer aggregates + personal отдельно; profile/checkin/privacy/status gates |

Checkin fields backward compatible: обязательные positionId/ratingQuarters|null/
body/occurredAt/visibility сохранены. Дополнительно servingFormat/venueId или
venueText,tagIds≤12,mediaIds≤4. PATCH positionId не меняет. Фото загружаются прежним
POST /v1/media, binary image8MiB max, sanitize/EXIF removal/thumbnail; payload
чекина ссылается только на собственные активные assets, атомарно с tags/полями.
Отвязка фото перед DELETE asset; attached asset delete→409. Неизвестный venueId
не принимается: только verified; до P7 можно ввести свободный текст места.

Quarter integers исключают float step ошибки. Значение17=4.25; null≠0. Summary
передаёт точные sumQuarters строкой и rated count, average числом/null. UI HALF_UP2.
Не усреднять средние позиций вместо всех оценённых чекинов. Style breakdown
включает unknown отдельной группой; доли UI от всех уникальных, знаменатель подписан.
Повторы SKU меняют total/histogram, но не unique/style counts. После delete/merge
счётчики пересчитываются из активных строк; stale materialized counters нет.

Сортировка diary: date_desc/date_asc по occurredAt, rating_desc/rating_asc по
coalesce(rating_quarters,-1), затем UUID. NULL при ascending перед оценёнными,
при descending после них. Unique/wishlist sort=name/latest/rating; average null
с тем же sentinel. Курсор связан с owner/filters/sort; несовместимый/повреждённый
или невозможная дата→400, не SQL500. При редактировании между страницами нужен
refresh; клиент удаляет повторные ID. Snapshot pagination не обещается.

Migration007 не переписывает001–006: индексы user/date/rating/position/trigram,
checkin_tags reverse и12 taste labels. P4→P5 upgrade test сохраняет IDs/history/
legacy idempotency hashes. Rollback приложения возможен с additive schema,
но старый клиент не знает новых полей. Не удалять007/данные ради rollback; SQL
backup/restore — прежний runbook. Tag labels не содержат сторонних каталогов.

Android SQLite cache v2→v3: таблица diary_drafts с encrypted JSON и максимум20
черновиками; явное сохранение, без автоматической отправки/вытеснения. Офлайн
разрешены чтение ранее загруженных страниц (общий лимит120,TTL24h) и редактирование
черновика; запись в PostgreSQL подтверждается только успешным HTTP. Logout/
смена аккаунта очищают сессию/cache/drafts/photos и отменяют retained scope.
Photo picker сохраняет доступ URI при поддержке Android; если доступ потерян,
пользователь выбирает фото заново, успех не имитируется. Неприкреплённые upload
assets остаются owner-private; retention cleanup относится к операционному P8.

# Каталог: измерения и развитие

Цель P4 — расширяемый поиск десятков тысяч позиций. Это не заявление о
готовности обслуживать миллион пользователей или 500k concurrent.

PostgreSQL17, pg_trgm GIN по нормализованной проекции catalog_search, B-tree
newest/name/ABV и brewery/style. У barcode_mappings partial UNIQUE(gtin) WHERE
status='verified'. Проверенный GTIN находит единственную позицию без scan/OFF API.
Фильтры и пагинация на сервере, лимит50, стандартный authenticated SQL budget120/min,
statement_timeout5s, max8 search words/120chars. Короткие/частые подстроки могут
дать последовательный/большой bitmap scan; это надо учитывать при дальнейшем tuning.

Миграция005 обновляет проекцию пакетно с transition tables на statement, в той
же транзакции. Важны индексы ссылок и единый commit каталога/поиска. При массовом
исправлении пивоварни блокировка и обновление всех её позиций ожидаемы; импорт
проводить ограниченными партиями, не выключая constraints/trigger.

Воспроизводимый **disposable synthetic** benchmark (только localhost development):

```sh
cd backend
npm run build
node --env-file=../.env dist/src/operations/catalog-benchmark.js
```

Создаёт отдельную случайно именованную test DB, применяет миграции, генерирует
50000 синтетических позиций и 3000000 GTIN с checksum; удаляет только эту БД.
Никогда не пишет в DATABASE_URL. TEST_DATABASE_URL требует право CREATE DATABASE.
Размеры регулируются CATALOG_BENCH_POSITIONS (100…100000), CATALOG_BENCH_CODES
(100…3000000). Все источники помечены synthetic. JSON сохраняется в
`docs/performance/P4-catalog-benchmark.json`: среда, generation time, планы,
30 последовательных warm-cache samples, p50/p95/max. Запросы каталога включают
реальный repository и JOIN карточки; barcode sample — SQL lookup. Не HTTP-load.

Первый запуск row-trigger/3M single INSERT превысил statement_timeout180s при
подготовке: не успешный результат. После этого добавлены migration005 и партии
100k codes. Первую ошибку не скрывать за последующим успешным замером.

До больших публичных нагрузок (P8/P9):

- Реальный RU staging с согласованной инфраструктурой; измерить HTTP p95/p99,
  concurrent readers/writers, pool contention, cache hit ratio, GIN pending list,
  долгие короткие запросы, обновления brewery, рост WAL/backup/media.
- При необходимости tune indexes/GIN и pool/pgbouncer по метрикам, добавить
  query budgets и наблюдаемость без PII. Read replicas только с явно учтённым lag.
- При нескольких API replicas заменить local media на общий проверенный storage;
  session/account/catalog остаются общими, код iOS использует тот же /v1 API.
- Поиск вынести в отдельный движок лишь при измеренном пределе PostgreSQL;
  versioned projection/outbox и rebuild должны сохранять canonical UUID/ACL.
- Проверить backup/restore, schema upgrade/rollback, disk capacity и retention;
  миллион аккаунтов/миллионы QR не объявлять выдерживаемыми по SQL smoke.

## Измеренный результат P4

PostgreSQL17.7, Node24.22.1, контейнер с quota4 CPU/16GiB. 50000 positions +
3000000 codes подготовлены за123.8s. Warm-cache последовательный SQL/repository
smoke, 30 samples на вид запроса: GTIN p95=2.25ms; selective search p95=3.40ms;
newest/name/ABV p95=4.28/4.09/3.48ms. Bitmap Index Scan использует GIN поиска,
GTIN — partial unique B-tree, name — covering index-only scan. Полные планы и
сырые итоговые агрегаты — JSON рядом; эти результаты не включают HTTP/мобильную
сеть, конкуренцию пользователей или worst-case common/short terms.

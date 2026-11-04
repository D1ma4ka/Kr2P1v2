# ADR-0003 — PostgreSQL и платформенно-нейтральный backend P2

Статус: принято разработчиком для локального P2 по ТЗ v1.3 §6/15/17.
Дополняет ADR-0002, не разрешает production или расходы.

## Решения

- Сохраняем Node24/TypeScript/Fastify, модульный монолит. PostgreSQL17:
  транзакции, UUID, FK, partial indexes, row/advisory locks. SQLite проще
  локально, но хуже отражает конкурентную запись нескольких API; MySQL
  пригоден, но не даёт преимущества перед выбранным SQL/partial indexes.
- `pg`, параметризованный SQL без ORM: явные запросы/индексы/EXPLAIN.
  Bounded pool, connection/statement/lock/idle-transaction timeouts. SQL не
  зависит от Android/iOS; стабильные UUID, UTC timestamps, ratingQuarters.
- Ordered SQL migrations, одна транзакция на миграцию, SHA-256 ledger,
  advisory lock на весь запуск; изменение уже применённой миграции — ошибка.
  Миграции запускает оператор отдельно, не каждая API-реплика. Runtime role
  без DDL; migration role отдельно. Roll forward; destructive down запрещён
  по умолчанию. Перед изменением данных — backup и проверенный restore.
- UUIDv4 стабильные identifiers; cursor `(created_at,id)` и owner indexes.
  DB-held bearer sessions (SHA-256 только случайных 256-bit токенов), TTL и
  revocation. P2 test accounts создаются явным local CLI; регистрация,
  password hashing/reset/rotation/verification workflow — P3. Нет login mock.
- Owner-only profile/diary/media primitives P2. Для чужих private IDs — 404;
  unauthenticated — 401. Social visibility grants реализуются в P6;
  P2 никому не открывает private data. API по умолчанию не публикует private
  гендер/email или storage key. Full profile field ACL — P3.
- `Idempotency-Key` UUID на создание чекина, fingerprint тела и durable
  запись в той же транзакции; другой payload — 409, retries не дублируют.
  `version` для PATCH/DELETE, атомарное WHERE version, stale write — 409.
- Media вне SQL; интерфейс storage + локальный private filesystem adapter,
  normalized JPEG/thumbnail, bounded decoded pixels, удаление EXIF. Для
  нескольких API экземпляров нужен общий storage/S3 adapter перед scale-out;
  локальные независимые каталоги не поддерживают корректный scale-out.
- Настоящий SMTP в Mailpit, loopback UI, persistent volume. Dev CLI не
  доставляет внешним адресатам. Production TLS/SMTP/РФ размещение требуют
  отдельной проверки. Ни одного автоматически созданного реального каталога.

## Схема и границы

Модель §6 плюс optional gender/visibility, links, taste tags, venue types,
idempotency. Soft-delete checkins и FKs RESTRICT защищают историю; merged
position хранит alias на canonical position, операция merge/пересчёт P4/P5.
QR mappings намеренно не смешиваются с GTIN: бизнес-контент QR не утверждён.
GTIN — 14 цифр с корректной check digit, leading-zero нормализация до записи.
Не используем JSON для графа друзей/связей и денежных сущностей (их нет).
Badge pin slot1..3 + unique(user,slot), checkin photo slot1..4 + owner FK.
Схема social/moderation/badges не означает готовые endpoints/движок.

## Масштаб и ограничения

500k+ registered — цель, не измеренная capacity. Начинаем без Redis, брокера,
partitioning, replicas и PostGIS. Индексы защищают текущие access patterns;
bbox индекс/план и репрезентативная нагрузка — P7/P8 до утверждения масштаба.
Кеш/worker/replicas добавляются по метрикам. Ограничения HTTP/SQL — стартовые
инженерные defaults, не продуктовые обещания latency/throughput.

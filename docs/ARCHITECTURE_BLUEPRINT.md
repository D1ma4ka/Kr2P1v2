# Архитектура — актуальный план v1.3

Оригинальная proposal v1.0 сохранена в handoff/v1.0/docs/ARCHITECTURE_BLUEPRINT.md.
Фактически P1: Kotlin/Compose Android и Node24/TypeScript/Fastify HTTP server
по ADR-0002. Перенос v1.3 не меняет этот код и не начинает схему БД.

Общая платформа: Android (первый) и обязательный iOS клиент (P10–P13) →
один versioned HTTP JSON/OpenAPI → модульный монолит → транзакционная SQL-БД.
Файлы фото хранятся отдельно от SQL, metadata/ACL в БД; dev SMTP — настоящий
локальный приёмник. Конкретные SQL/migrations/storage варианты подтвердить ADR
и реальными тестами P2. Пользовательские данные не хранятся в GitHub.

Модули identity/profile/catalog/checkins/social/stats/badges/venues/media/
moderation/operations. Стабильные positionId и общие аккаунты; idempotency,
FK/unique/transactions, пагинация, migrations/rollback policy. Новый профиль,
вкусовые теги, chart aggregates, venue types и bbox планируются с P2, без
фиктивного заполнения. QR и GTIN разные; QrCodeMapping только при согласованном
QR бизнес-сценарии. Индексы и load plans не подтверждают реальную capacity.

API не импортирует Android/iOS SDK. Клиенты имеют platform adapters для камеры,
датчиков, карт, permissions и secure token storage. KMP vs SwiftUI — ADR перед
P10, не повод сейчас переписывать Android или создавать вторую БД.

Dev/CI/staging/prod отличаются config/secrets/DNS, не бизнес-контрактом.
РФ-провайдеры БД/media/logs/backups/SMTP и бюджет проверяются до эксплуатации.
Модульный монолит сохраняется; кеш/реплики/балансировка — по измерениям.
500k+ registered, 1M accounts/3M synthetic codes — план испытаний, не throughput.

Встроенная карта MUST, provider adapter, bbox/cursor/filter/type/city, clusters
и list-map consistency. MapKit/MapLibre+tiles — кандидаты, не утверждённые
платные услуги. Ключи/лицензия и live integration проверяются P7.
Motion локален; сенсорные данные не сохраняются и не отправляются в backend.

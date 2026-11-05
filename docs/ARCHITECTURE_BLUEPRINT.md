# Архитектура — актуальный план v1.3

Оригинальная proposal v1.0 сохранена в handoff/v1.0/docs/ARCHITECTURE_BLUEPRINT.md.
Фактически P1: Kotlin/Compose Android и Node24/TypeScript/Fastify HTTP server
по ADR-0002. P2 расширяет этот стек PostgreSQL17, SQL migrations и /v1 API по ADR-0003.

Общая платформа: Android (первый) и обязательный iOS клиент (P10–P13) →
один versioned HTTP JSON/OpenAPI → модульный монолит → транзакционная SQL-БД.
Файлы фото хранятся отдельно от SQL, metadata/ACL в БД; dev SMTP — настоящий
локальный приёмник. Реализованы pg pool, checksum migrations, private FileStorage и Mailpit;
реальные SQL/HTTP/media/SMTP проверки описаны в stages/P2.md. Пользовательские данные не хранятся в GitHub.

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

## Фактические границы P2

OpenAPI: docs/api/openapi.yaml, route schemas генерируют контракт; tests
проверяют drift. Identity — hash-only persistent bearer sessions; owner-only
profile/checkin/media API, version checks, idempotency, cursor <=50, per-account
DB budget120/min. Public signup/reset/rotation P3, social grants P6. Остальные
домены представлены SQL foundations, не фиктивными endpoints. Схема —
backend/migrations/001_foundation.sql и 002_request_budgets.sql.

Два секретных DB role config: runtime без DDL; migrations runner отдельно.
Image USER node, read-only root, scoped writable media mount. Readiness
проверяет hashes/SQL tables/media access; liveness только процесс. Media files
fsync + directory sync; неопределённый COMMIT сохраняет files для reconciliation.
Запуск переносится env/image/volumes; production hardening/runbook — DEPLOYMENT_RU.
Android BuildConfig dev/staging/release address не меняет бизнес-контракт;
сам API client не входит в P2 и появится в P3.

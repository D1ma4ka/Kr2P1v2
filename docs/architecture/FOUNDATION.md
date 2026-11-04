# Основа P1

Android и backend собираются независимо. На P1 связи Android→API ещё нет:
приложение показывает фирменный стартовый экран, сервер обслуживает только
реальную проверку живости HTTP. Никаких production mock данных или пустых
методов auth/catalog/social с успешными ответами.

Android: Activity → Compose welcome + designsystem. UI-state диалога локален
и сохраняется через rememberSaveable; domain/data/network слои появятся только
при реализации P3/P4. Сервер: config → app factory → process lifecycle.
Интеграционные HTTP-тесты создают процесс/порт, делают реальные запросы и
останавливают сервер; не используют заглушку транспорта.

Планируемые backend границы: identity/profile/catalog/checkins/social/badges/
venues/media/moderation/operations. Это модули одного процесса, не сервисы.
P2 выберет SQL migration runner, schema и OpenAPI, затем проверит persistent CRUD.
Media/SMTP выбор будет проверен интеграционно, а не отмечен DONE из ADR.

CI — read-only permissions, pinned action SHAs, ограниченные timeout и concurrency.
Запуск по PR при изменении кода/конфигурации/исходных требований; обычный отчёт
этапа не вызывает новую сборку. workflow_dispatch оставлен для обоснованного
ручного запуска. Нет deploy/release/publish steps и внешней доставки писем.

Зависимости: backend package-lock; Android Gradle/AGP/Kotlin/Compose версии
фиксированы, wrapper JAR и distribution SHA проверяются. Полный license/SBOM и
security review Android относится к P8 и не подменяется npm audit.

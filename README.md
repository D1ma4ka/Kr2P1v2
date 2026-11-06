# Kr2P1v2
A modern social platform for craft beer enthusiasts and bars

## KraPiva 2.0

Единый продукт Android + обязательная iOS-версия с общим backend. Android —
первый клиент на Kotlin / Jetpack Compose / Material 3 для
совершеннолетних пользователей: каталог пивных позиций, дневник и социальная сеть
с настоящим сервером и постоянной БД.

**P0–P4 реализованы; автоматическая приёмка P4 PASS. P5 ожидает команды владельца.**
Android auth/profile связан с настоящим сервером: PostgreSQL, migrations, owner API, сессии,
private media, local SMTP и OpenAPI. Account workflows P3 работают через HTTP/SQL/SMTP; социальные
сценарии относятся к P5–P7. Ручная проверка physical phone/TalkBack ещё NOT RUN.
P4 добавил настоящий каталог/search/brewery/proposals/moderation/GTIN scanner;
камера/разрешения/освобождение и offline переходы проверены на эмуляторе;
оптический захват физической этикетки ещё NOT RUN. Это не публичный production сервис.

Текущая точка продолжения и результаты — [прогресс](docs/PROGRESS.md).
Команды запуска и восстановления — [локальный backend](docs/DEPLOYMENT_RU.md),
[backend README](backend/README.md). HTTP API контейнера: localhost:53000;
Mailpit UI: localhost:58025. Нужны локальные DB/SMTP, переменные окружения и
миграции; прежнего standalone `npm start` без БД недостаточно.
Android сборка/инструкции: [Android](android/README.md), [отчёт P1](docs/stages/P1.md).

- [Актуальное ТЗ v1.3](docs/TECHNICAL_SPECIFICATION.md)
- [Решения и границы текущей работы](docs/PROJECT_DECISIONS.md)
- [Инвентаризация репозитория и источников](docs/REPOSITORY_INVENTORY.md)
- [Матрица требований](docs/REQUIREMENTS_MATRIX.md)
- [Последовательность P0–P9](docs/IMPLEMENTATION_PLAN.md)
- [План проверок](docs/QA_TEST_PLAN.md)
- [Отчёт P0](docs/stages/P0.md)
- [Готовность релиза](docs/RELEASE_READINESS.md)

Проверка целостности P0 (Python 3.12+, Git):

```sh
python3 scripts/verify_p0.py
git diff --check
```

Разработка с нуля: старые APK, GTIN, SQLite, фотографии и восстановленный код
не используются. Уникальность определяется `BeerPosition.positionId`, а не стилем
или способом подачи. Никакой имитации серверных операций в рабочем приложении.

Изменения ведутся в рабочей ветке. Правила репозитория: [AGENTS.md](AGENTS.md).
После каждого этапа — проверка, commit/push, отчёт и остановка до сообщения
владельца «Продолжай». P4 разрешён текущим запросом; после отчёта остановиться до команды на P5.

## Актуальные требования v1.3

[Изменения и сравнение](docs/UPDATE_v1.3.md), [roadmap](docs/ROADMAP.md),
[паритет платформ](docs/PLATFORM_PARITY.md). iOS PLANNED P10–P13, обязательна;
готовность Android не равна готовности всего продукта. Новый дизайн и motion
внедряются в P3 без пересоздания P1. Source checker адаптирован к v1.3 (180 ID) с сохранением обоих архивов.

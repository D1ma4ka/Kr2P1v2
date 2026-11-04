# Kr2P1v2
A modern social platform for craft beer enthusiasts and bars

## KraPiva 2.0

Единый продукт Android + обязательная iOS-версия с общим backend. Android —
первый клиент на Kotlin / Jetpack Compose / Material 3 для
совершеннолетних пользователей: каталог пивных позиций, дневник и социальная сеть
с настоящим сервером и постоянной БД.

**P0/P1 завершены; P2 ожидает отдельной команды.** Есть собираемый Kotlin/Compose
клиент со стартовым экраном и настоящий HTTP-сервер с проверкой живости.
Аккаунтов, БД, каталога и социальной сети пока нет; они реализуются в P2–P7.
P1 завершён: Android/backend и проверки исходников/ресурсов прошли CI.
Оставшиеся предупреждения и точка продолжения — [прогресс](docs/PROGRESS.md).

Быстрые проверки (Node 24, JDK 21, Android SDK 35):

```sh
cd backend
npm ci --ignore-scripts --no-fund --no-audit
npm run check
cd ../android
./gradlew --no-daemon assembleDebug lintDebug
```

Запуск сервера: `npm start` из `backend/`; адрес по умолчанию
`http://127.0.0.1:3000/health/live`. Только живость процесса, не readiness БД.
Подробности: [backend](backend/README.md), [Android](android/README.md),
[отчёт P1](docs/stages/P1.md), [дизайн](docs/design/DESIGN_SYSTEM.md).

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
владельца «Продолжай». Следующий этап P2 не начат; перед ним требуется сообщение владельца «Продолжай».

## Актуальные требования v1.3

[Изменения и сравнение](docs/UPDATE_v1.3.md), [roadmap](docs/ROADMAP.md),
[паритет платформ](docs/PLATFORM_PARITY.md). iOS PLANNED P10–P13, обязательна;
готовность Android не равна готовности всего продукта. Новый дизайн и motion
ещё не внедрены, P1 не пересоздаётся. Последнее обновление — только документация.
Проверка `scripts/verify_p0.py` пока привязана к v1.0 (82 ID) и требует
адаптации в следующей разрешённой разработке; см. docs/handoff/README.md.

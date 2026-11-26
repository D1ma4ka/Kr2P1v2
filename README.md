# Kr2P1v2
A modern social platform for craft beer enthusiasts and bars

## KraPet P10

Ветка `p10/krapet` объединяет Android и PostgreSQL-backend P8.7.2 с вкладкой «Питомец». Порядок запуска и установки APK описан в [P10 Recovery](docs/p10/P10_RECOVERY.md), состояние приёмки — в [матрице P10](docs/p10/P10_REQUIREMENTS.md).

## KraPiva 2.0

Единый продукт Android + обязательная iOS-версия с общим backend. Android —
первый клиент на Kotlin / Jetpack Compose / Material 3 для
совершеннолетних пользователей: каталог пивных позиций, дневник и социальная сеть
с настоящим сервером и постоянной БД.

15 ноября: продолжение **P8.3 A5–A10** поверх сохранённых A1–A4:
пиво на welcome с датчиками, уникальные @handle и безопасная миграция 012,
вход по email/handle/@handle, новые пароли от 8 символов и узор в обеих темах.
Backend **42/42**, JVM **22/22**, debug/test APK и lint PASS.
Device/visual acceptance **PENDING**. [Отчёт P8.3](docs/stages/P8.3.md).

15 ноября: PATCH P8.2, ветка `codex/krapiva-p8.2`. Понятная валидация
регистрации, шестизначное восстановление пароля, официальный логотип на auth
и анимация пива на загрузке. Backend **40/40**, Android JVM **18/18**,
debug/test APK и lint PASS. Device/visual acceptance **PENDING**;
внешняя доставка почты не настроена. [Отчёт и APK P8.2](docs/stages/P8.2.md).

14 ноября: поверх П8.1 исправлено неоднозначное письмо подтверждения email,
добавлены графитовая тёмная тема с векторной крапивой и sensor-aware анимация пива
на auth/loading. Backend **39/39**, Android JVM **17/17**, debug APK и lint PASS.
Android UI/visual checks **NOT RUN**: программный эмулятор без KVM не загрузился.
[Результат, изменённые файлы и APK](docs/stages/AUTH_THEME_MOTION.md).

**P0–P8.1 и A1–A4 сохранены. P8.3 automatic checks PASS; device/visual acceptance
PENDING. Текущая ветка `codex/krapiva-p8.2`; П9 не начинался.**
P8.1 добавляет autosave офлайн-черновиков, сохранение черновиков владельца при
logout и обязательную серверную проверку похожих позиций перед публикацией.
Подробности: [P8.1 handoff](docs/P8.1_HANDOFF.md).
П7: нативная карта MapLibre/кластеры/заведения, 10 серверных правил достижений,
закрепления и базовая модерация. Backend38/38, JVM12/12, сборка/lint PASS.
Нативная карта на устройстве ещё не проверена; production не развёрнут.
[Отчёт П7](docs/stages/P7.md) · [запуск/проверки](docs/places/OPERATIONS.md).
Android auth/profile связан с настоящим сервером: PostgreSQL, migrations, owner API, сессии,
private media, local SMTP и OpenAPI. Account workflows P3 и социальные
функции P6 работают через общий HTTP/SQL/SMTP backend. Ручная проверка physical phone/TalkBack ещё NOT RUN.
P4 добавил настоящий каталог/search/brewery/proposals/moderation/GTIN scanner;
камера/разрешения/освобождение и offline переходы проверены на эмуляторе;
оптический захват физической этикетки ещё NOT RUN. Это не публичный production сервис.

P5 добавляет настоящие чекины/дневник, wishlist, unique positions, оценки,
диаграммы/home и encrypted editable drafts. [Операции P5](docs/diary/OPERATIONS.md).

P6 добавляет взаимных друзей, ленту с фото, лайки, комментарии и серверную приватность.
[Операции P6](docs/social/OPERATIONS.md), [проверки P6](docs/stages/P6.md).

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
владельца «Продолжай». П8 автоматическая стабилизация завершена; следующая
точка — ручная device/visual приёмка, затем отдельное решение о П9.

## Актуальные требования v1.3

[Изменения и сравнение](docs/UPDATE_v1.3.md), [roadmap](docs/ROADMAP.md),
[паритет платформ](docs/PLATFORM_PARITY.md). iOS PLANNED P10–P13, обязательна;
готовность Android не равна готовности всего продукта. Новый дизайн и motion
внедряются в P3 без пересоздания P1. Source checker адаптирован к v1.3 (180 ID) с сохранением обоих архивов.

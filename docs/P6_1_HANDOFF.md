# KraPiva 2.0 — передача П6.1 между аккаунтами

## Актуальный аудит доступной среды — 10 ноября 2026, Europe/Istanbul

**Статус: BASELINE SAVED / P6.1 WIP NOT LOCATED / HANDOFF BLOCKED.**
Этот аудит сохраняет доступные исходники П0–П6 и совместимую документацию.
Он **не подтверждает сохранение заявленного владельцем локального WIP П6.1**:
в подключённой среде такого WIP и 14 утверждённых исходников не обнаружено.
Не пересоздавать их и не начинать разработку до восстановления оригиналов.

### Git и безопасная точка передачи

- Рабочая папка: `/workspace/Kr2P1v2`, origin: `https://github.com/D1ma4ka/Kr2P1v2.git`.
- До любых изменений: `work` → `634feca5d14ed0463d9e6ef7268be1b9e23c48ef`;
  только `README.md` и `AGENTS.md`. `git status --porcelain=v2 --untracked-files=all`,
  оба diff, список ignored/untracked и stash пусты. Один worktree. Локального WIP нет.
- Просмотрены reflog и `git fsck --full --no-reflogs`: потерянных объектов не найдено.
  Reflog содержит прежние reset/checkout на initial/main; они не выполнялись этим аудитом
  и сами по себе не доказывают наличие или удаление П6.1.
- Получены все 9 удалённых веток через `git fetch --no-tags origin 'refs/heads/*:refs/remotes/origin/*'`.
  Код: `codex/krapiva-2-p3` → `9dc0769d68a6bc15af347fc17023e7fc55f29018`.
  Документы: `docs/p6-1-design-handoff` → `cdb3be4b3a4afe0277df4ffd255ad4ad123d2575`.
  Другой ветки с подтверждённым П6.1 на момент аудита нет.
- Безопасная ветка: `codex/p6-1-audit-save-2026-11-10`. Создана от указанного
  docs HEAD: он уже потомок code baseline. Это сохраняет весь существующий код и
  все 6 совместимых документов П6.1 без merge, cherry-pick и разрешения конфликтов.
  Исходные удалённые ветки и локальная `work` не изменялись.
- Точный SHA итогового коммита сообщается в отчёте сессии; внутри ветки получить
  `git rev-parse HEAD`. Не записываем самоссылочный SHA в его собственное содержимое.
  Проверка отправки: SHA этой ветки в `git ls-remote --heads origin` должен совпасть.

### Найденные экраны и компоненты

Ниже пути относительно `android/app/src/main/java/ru/krapiva/app/`.
**DONE baseline** означает наличие функционального кода П0–П6 и прежнего отчёта
приёмки; это не новая runtime-проверка. Для всех строк визуальное соответствие
П6.1 **BLOCKED / NOT VERIFIED**, поскольку оригинальные макеты отсутствуют.

| Экраны / компоненты | Файлы / точки входа | Фактический статус |
|---|---|---|
| Запуск и маршрутизация | `MainActivity.kt`, `account/AccountScreen.kt: AccountApp`, `account/AccountViewModel.kt: AccountScreen` | DONE baseline: `KraPivaTheme → AccountApp`, auth/catalog/diary/social связаны |
| Welcome, восстановление сессии, вход, регистрация, verify, forgot/reset | `account/AccountScreen.kt: Welcome, AuthForm`, `account/AccountViewModel.kt`, `designsystem/BeerHero.kt` | DONE baseline; существующие формы и loading/error, без нового оформления П6.1 |
| Профиль, редактор, настройки, avatar/cover/full photo, privacy | `account/AccountScreen.kt: Profile, ProfileEditor, Settings`, `account/AccountApi.kt`, `account/AccountStore.kt` | DONE baseline; выбора темы нет |
| Каталог, поиск, фильтры, карточка позиции | `catalog/CatalogScreen.kt: CatalogApp, CatalogList, Filters, Detail, BeerPositionCard, CatalogImage`, `CatalogViewModel.kt`, `CatalogRepository.kt` | DONE baseline; реальные API/кеш/ошибки, Dark |
| Список/карточка пивоварни, выбор стилей | `catalog/CatalogScreen.kt`, `catalog/CatalogViewModel.kt: BREWERIES, BREWERY, STYLES` | DONE baseline; не создавать заново |
| Сканер и ручной ввод кода | `catalog/ScannerScreen.kt`, `BarcodeDecoder.kt`, `Gtin.kt` | DONE baseline; camera permission/fallback; physical optical scan по прежнему отчёту NOT RUN |
| Предложения позиции и их список | `catalog/ProposalForm.kt`, `catalog/CatalogScreen.kt`, `CatalogViewModel.kt: PROPOSAL, PROPOSALS` | DONE baseline; формы и photo picker |
| Главная и агрегаты | `diary/DiaryScreen.kt: DiaryApp, DiaryList, Summary`, `DiaryViewModel.kt: HOME` | DONE baseline; реальные статистика/recent/CTA, старое оформление |
| История, уникальные позиции, wishlist, черновики, выбор позиции | `diary/DiaryScreen.kt`, `DiaryViewModel.kt: DIARY, UNIQUE, WISHLIST, DRAFTS, PICK` | DONE baseline; фильтры/списки/empty/offline; слово «Дневник» сохранено |
| Детали/создание/редактирование чекина, фото и rating/tags | `diary/DiaryScreen.kt: CheckinDetail`, `diary/DiaryEditor.kt`, `DiaryViewModel.kt` | DONE baseline; не заменять формами-заглушками |
| Статистика и drilldown | `diary/DiaryScreen.kt: StatisticsScreen, StatBar, ProfileDiarySummary, PositionDiaryActions`, `DiaryDates.kt`, `DiaryNumbers.kt` | DONE baseline; style/brewery/rating и текстовые counts |
| Лента, поиск людей, друзья, заявки, чужой профиль | `social/SocialScreen.kt: SocialApp, SocialCheckin, SocialPhoto`, `SocialViewModel.kt: SocialPage` | DONE baseline; API, privacy и состояния сохранены |
| Обсуждение, лайки и комментарии | `social/SocialScreen.kt`, `social/SocialViewModel.kt: DETAIL` | DONE baseline; формы, retry/pagination, собственное удаление |
| Общие кнопки, поля, карточки, нижняя навигация | `account/AccountScreen.kt: Action, Input, NavigationMark`, `catalog/CatalogScreen.kt: CatalogAction, CatalogInput`, `diary/DiaryScreen.kt: DiaryButton, DiaryCard` | PARTIAL относительно П6.1: компоненты существуют, но используют старые Dark-цвета, доработки не подтверждены |
| Dark tokens, typography, shapes, liquid motion | `designsystem/Theme.kt`, `BeerHero.kt`, `LiquidMotion.kt`, `assets/brand/tokens.json` (от корня repo) | DONE baseline / PARTIAL П6.1: фиксированная `darkColorScheme`, жёсткие `KraPivaColors` |
| Светлая тема, выбор и сохранение темы | `designsystem/Theme.kt`, `account/AccountScreen.kt: Settings`, `AccountStore.kt` | NOT IMPLEMENTED в доступном baseline: `lightColorScheme`/theme preference отсутствуют; другой WIP неизвестен |
| Удаление «Дневник» из брендированного UI | `diary/DiaryScreen.kt` (строки 47, 83, 90, 215) | NOT IMPLEMENTED в доступном baseline; исходники не менялись в этом аудите |
| Исторический standalone welcome | `welcome/WelcomeScreen.kt: WelcomeScreen` | Файл есть, вызовов в `android/app/src` не найдено; не считать текущим стартовым экраном |
| Бейджи, заведения/бары, карта П7 | disabled «Бары» в `diary/DiaryScreen.kt`; этап `docs/stages/P6.md` | NOT STARTED функциональность П7; счётчик значков на home не доказывает реализацию П7 |

### Макеты и ресурсы

- Подтверждено **0 из 14** утверждённых исходных макетов П6.1. Это означает,
  что они не найдены здесь, а не что владелец их не создавал/не утверждал.
- Проверены рабочая копия включая ignored/untracked, доступные каталоги
  `/workspace/shared`, `/workspace/library-files`, `/workspace/scratch`, а также
  поиск профильных файлов в `/workspace`, `/tmp`, `/codex`, `/app`, `/mnt`, `/opt`.
  Другие аккаунты, вложения старых чатов и прежний workspace этим не проверены.
- Просмотрены деревья всех 9 remote branches. В p1/p2 есть **14 растровых файлов**,
  в p3/docs — **18**. Это старые logo/mark/avatar/mipmap и материалы P3–P6;
  совпадение числа 14 не делает их комплектом П6.1.
- Найдены `assets/brand/{mark,logo,avatar}.{svg,png}`, `tokens.json`, Android
  vector/mipmap ресурсы; `docs/design/reference/krapiva-dark-concept.png` — ранний
  референс. `p3-android-baseline.jpg`, `p4-review.jpg`, `p5-review.jpg`, `p6-review.jpg`
  и соответствующие JSON — исторические runtime baseline, не новые макеты.
- Полный перечень доступных frontend/resources/design файлов с размером и SHA-256:
  [`design/p6-1-audit-inventory.json`](design/p6-1-audit-inventory.json).
  PNG dimensions записаны для найденных PNG. Имена/хеши/размеры/темы и карта
  «макет → экран» для отсутствующих 14 файлов **не установлены**, не выдуманы.

### Проверки, сохранение и ограничения

В этой сессии выполнены только Git-аудит, чтение кода/документов, поиск файлов,
вычисление SHA-256 и проверка diff. `git fsck --full --no-reflogs` завершён без
ошибок; docs HEAD является потомком code baseline. Перед коммитом выполнены
`git diff --check` (PASS), сверка SHA-256 всех 66 файлов inventory (PASS) и проверка
неизменности `android/backend/assets/infra/scripts` относительно code baseline (PASS).
Новых build/lint/unit/instrumented/browser запусков нет, приложение не запускалось.

Прежние Android 11/11 instrumented + 8 unit и backend 21/21 PASS остаются
**данными отчёта П6**, не результатом этого аудита. Источники:
`docs/stages/P6.md`, CI 42463380380 / 42398072924. Physical/TalkBack/RU staging
ограничения сохранены. CI заново не инициирован; workflows используют PR/manual,
этот checkpoint — только push новой ветки с `[skip ci]`, без PR/merge.

Незакоммиченных пользовательских изменений для checkpoint в доступной среде нет.
Сохраняются документы аудита и уже существующий baseline; нельзя обещать, что этим
сохранена отсутствующая частичная работа П6.1. Никакие исходники, API, SQL, тесты,
assets, секреты или APK не добавлялись и не изменялись.

### Следующий единственный шаг — восстановить исходный WIP

В **старой сессии/среде**, где делался П6.1, найти фактический каталог и выполнить
read-only status/diff/log, инвентаризацию 14 оригиналов; затем сохранить тамошние
правки в отдельный checkpoint commit/push без reset/clean/force. После этого
сопоставить checkpoint с этой audit-веткой, не замещая код старыми документами.
Нужны доступ к прежней среде либо её Git checkpoint и оригиналы макетов.
**Не продолжать реализацию и не регенерировать макеты. STOP после текущего сохранения.**

---

## Исторический remote handoff до этого аудита

Ниже сохранён предыдущий документ. При расхождениях текущий аудит выше имеет
приоритет для фактов доступной среды; неизвестный WIP другой среды остаётся неизвестным.

**Проверено по GitHub:** 10 ноября 2026. Это **снимок удалённого репозитория**, а не аудит локальной/временной среды предыдущего Codex.
**Статус:** HANDOFF NEEDS LOCAL RECONCILIATION. **Нельзя утверждать, что П6.1 ещё не написан в старой среде; нельзя утверждать, что частичная реализация уже сохранена в GitHub.**

## Репозиторий и ветки

- Репозиторий: `D1ma4ka/Kr2P1v2` (private).
- **Рабочий код:** `codex/krapiva-2-p3` на `9dc0769d68a6bc15af347fc17023e7fc55f29018` (последний проверенный remote HEAD на 10.11.2026; перед продолжением проверить снова).
- **Документация нового UI:** `docs/p6-1-design-handoff`, первоначально создана от того же commit; в ней находятся дополнение к ТЗ v1.3, план и `docs/stages/P6.1.md`. Это отдельная docs-ветка; изменения **не слиты** в рабочую ветку.
- `main` нельзя использовать как базу для возобновления незавершённого П6.1: рабочие П0–П6 находятся в `codex/krapiva-2-p3`. Draft PR #6 без merge.
- Никаких новых веток/PR с подтверждённым сохранённым кодом П6.1 при этой проверке не обнаружено; **внесённые локально или только в интерфейсе Codex изменения недоступны для удалённой проверки**.

## Подтверждённый функциональный baseline — НЕ ПЕРЕСОЗДАВАТЬ

| Этап | По опубликованным отчётам | Проверки / ограничения |
|---|---|---|
| П0–П2 | подготовка, Kotlin/Compose Android foundation, backend с PostgreSQL/OpenAPI и локальным SMTP | CI/миграции; реальный RU staging/production отсутствует |
| П3 / П1.1 | регистрация/почта/сессии, профиль и фото; KraPiva Dark, бренд и сенсорная анимация | автоматизированные Android/backend проверки PASS |
| П4 | каталог, поиск, пивоварни, предложения, камера и GTIN | автоматические PASS; физический optical scan NOT RUN |
| П5 | чекины и фотографии, дневник, wishlist, уникальные позиции и статистика | автоматические Android/backend проверки PASS |
| П6 | взаимные друзья, заявки, лента, реакции, комментарии, приватность/ACL | backend 21/21 (CI 42398072924), Android 11/11 instrumented + 8 unit (CI 42463380380) PASS |
| П7 | бейджи, заведения, карты, модерация | NOT STARTED |
| П10–П13 | обязательный iOS-клиент | NOT STARTED |

Источники: `README.md`, `docs/PROGRESS.md`, `docs/IMPLEMENTATION_PLAN.md`, `docs/stages/P3.md`–`P6.md`, `docs/QA_TEST_PLAN.md` **в ветке `codex/krapiva-2-p3`**. Это отражение выполненных CI и отчётов, **не запуск приложения в этой сессии**.

## П6.1 — согласованное задание

Переработать **существующий Android-фронтенд** по утверждённой концепции **C**: 14 ранее созданных изображений (тёмная и светлая версии), полностью **без слова «Дневник»** в соответствующем брендированном оформлении. Источник макетов — результаты предыдущей сессии Codex. Не предполагать, что 14 картинок = 14 самостоятельных экранов. Требуется фактический audit файлов, названий, разрешений и связки «макет → экран → тема». Не регенерировать согласованные макеты при наличии исходных файлов.

**Текущая точка неопределённости:** владелец сообщает, что Codex уже выполнил **часть работы П6.1**. На момент проверки remote HEAD рабочей ветки не изменился после П6. Следовательно, статус реально выполненного П6.1 **неизвестен без доступа к текущему Codex workspace**: могла быть подготовка, файлы, правки или незакоммиченные изменения. Нельзя ставить `NOT STARTED` локальным правкам и нельзя переписывать их. Отдельная документация, добавленная 10.11, имеет статус PLANNED и **не доказывает отсутствие WIP**.

Дополнение к техническому заданию/критерии: `docs/stages/P6.1.md` в ветке `docs/p6-1-design-handoff`. Изменено прежнее правило «только Dark»: для П6.1 обе темы обязательны, выбор темы и состояние должны работать в приложении.

## САМОЕ ВАЖНОЕ: восстановление фактического WIP из Codex

**Первое действие должно выполняться в той самой рабочей среде Codex, где пользователь уже делал П6.1.** Новый ChatGPT без доступа к ней не сможет сам восстановить невыложенные файлы.

1. **Сначала только чтение:** определить точный `pwd`, репозиторий, текущую ветку/HEAD, `git remote -v`, `git status --short`, `git diff --stat`, `git diff --cached --stat`, `git log -n 12 --oneline --decorate`; просмотреть незакоммиченные и untracked исходники/ресурсы, включая игнорируемые дизайн-файлы (не раскрывать секреты и персональные данные).
2. Сверить remote `origin/codex/krapiva-2-p3`, docs-ветку и локальные изменения; выяснить, какие файлы П6.1 создавались/правились и какие существующие Compose-экраны обновлены. **Не делать `git reset --hard`, `git clean`, `git stash drop`, `git pull --rebase` или `checkout` поверх незакоммиченного WIP.**
3. Проверить присутствие **всех 14** утверждённых изображений, исходных assets, light/dark theme, navigation/flows, текущего кода и имеющихся тестов. Отделить `DONE/IN PROGRESS/NOT STARTED/BLOCKED`; называть точные пути, коммиты и артефакты, не делать выводов по одному README.
4. **Сохранить текущий WIP, не переписывая:** при наличии локальных правок оформить отдельную ветку `codex/p6-1-recovery` (или иной уникальный `codex/p6-1-*`), безопасный checkpoint commit + push в этот же private repo. Не коммитить `.env`, API keys, токены, приватные данные, огромные бинарные файлы/кэши, чужие фото. Не перетирать удалённые ветки и не делать force push. Если нет прав или не удаётся отправить — честно сообщить.
5. В **ветке с реальными исходниками WIP** обновить `docs/P6_1_HANDOFF.md` и `docs/PROGRESS.md`: текущее состояние, таблица «экран/файл/реализованное/не готово», связанные assets, пройденные тесты, блокеры, точный следующий маленький шаг, branch+commit SHA. Сначала сравнить с docs-веткой; **переносить только совместимые документы**, не делать слепой merge и не замещать новые локальные изменения устаревшими.
6. **STOP** после сохранения/отчёта. Не продолжать реализацию, не начинать П7, не запускать тестовый цикл только ради отчёта. При следующем запуске новый Codex должен читать сохранённый фактический handoff на ветке WIP.

## Защитные ограничения

- Не повторять П0–П6, не переписывать backend/API/SQL, не менять бизнес-логику без отдельного согласования.
- Запрещены платные сервисы/покупки, публикация APK, production deployment, force push, main merge, удаление истории/данных.
- В отчёте различать: факты GitHub/коммиты, факты локального незакоммиченного состояния, подтверждённые тесты, предположения и неустановленное.
- Сохранять ресурсы и логичность работы: не гонять циклы сборок, при достижении ранее согласованных стоп-порогов (≤25% остатка пятичасового лимита или ≤50% недельного лимита) ограничиться безопасным сохранением и STOP. Если точные лимиты неизвестны, не выдумывать их.
- Новый аккаунт должен иметь доступ к private repo. Если отдельный локальный Codex workspace недоступен, автоматически «забрать» оттуда WIP невозможно; потребуется checkpoint из старой сессии.

## Точка входа нового аккаунта

1. Прочитать `docs/P6_1_HANDOFF.md` и `docs/stages/P6.1.md` из `docs/p6-1-design-handoff`, затем текущие `AGENTS.md`, `README.md`, `docs/PROGRESS.md` и ТЗ рабочей ветки.
2. Найти последнюю ветку `codex/p6-1-*` с проверенным recovery checkpoint (если её создал предыдущий Codex).
3. Сверить SHA/наличие файлов, **не начинать с нуля**; стартовать с зафиксированного следующего шага и только по новой команде владельца.

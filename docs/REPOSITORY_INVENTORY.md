# Инвентаризация P0

Проверено 2026-11-04 через локальный Git и подключённый GitHub API.

## Репозиторий

- URL: https://github.com/D1ma4ka/Kr2P1v2
- GitHub repository ID: `1445326100`; видимость: `private`; ветка по умолчанию: `main`.
- Подключение подтверждает `pull=true`, `push=true`; секреты не читались и не выводились.
- Исходная локальная ветка `work`: `69f9d4c420d9a2155fdedc54bdb8dbe9cfa69468`, только README.
- Актуальный remote main: `634feca5d14ed0463d9e6ef7268be1b9e23c48ef`.
- Рабочая ветка: `codex/krapiva-2-p0`, создана от актуального `origin/main`.
- До изменений `git status --short` пуст. Пользовательских незакоммиченных изменений нет.

## Удалённые ветки на момент начала

| Ветка | SHA | Состав |
|---|---|---|
| main | 634feca5d14ed0463d9e6ef7268be1b9e23c48ef | README.md, AGENTS.md |
| docs/agent-constitution | 9919766c7e274317bf01585f856c57dfc9e50c43 | README.md, AGENTS.md |
| recovery/legacy-apk | ce749960263fdb1d3bedd08f64e8d19641a81224 | 14 файлов, перечень ниже |

Получен инвентарь дерева recovery, без checkout/слияния и без выполнения кода:

```text
.gitignore
AGENTS.md
README.md
audit/00-executive-summary.md
audit/10-risks-and-open-questions.md
docs/APK_ANALYSIS.md
docs/PROJECT_CONTEXT.md
docs/RECOVERY_PLAN.md
docs/RECOVERY_STATUS.md
input/README.md
legacy/README.md
tools/README.md
tools/apk_inventory.py
tools/tests/test_apk_inventory.py
```

## Базовое дерево main

| Путь | Байты | Git blob |
|---|---:|---|
| AGENTS.md | 25711 | 40d42b007bf09458b73e4c3c6e7b28aa25c12a71 |
| README.md | 71 | 32c8d2e343f6e902a6c1d987d361eac1c0cd769b |

Вложенных AGENTS нет. `.gitignore`, исходники Android/backend, тесты, CI, APK,
SQLite, фотографии и `input/krapiva-0.2.0-local.apk` в main отсутствуют.
Очистка tracked legacy: **NO-OP**, список удалений пуст. Отсутствующий APK не
запрашивается, поскольку текущая задача — новая разработка.

## Входной пакет

`KraPiva_2.0_Codex_Handoff_Package.zip`, 52 300 байт, восемь Markdown-документов.
Состав, точные размеры и SHA-256 — [MANIFEST.json](handoff/MANIFEST.json).
Все восемь документов прочитаны; копии сохранены без текстовых правок.
Приведённые внутри пакета относительные ссылки относятся к исходной структуре;
соответствие новым путям задаёт manifest. Мастер-промпт хранится как справочный
материал, а не как новый файл правил агента.

## Инструменты и ограничения

Git 2.52.0, Python 3.12.14, OpenJDK 21.0.12.1, Node.js 24.22.1,
Docker CLI 29.9.2 обнаружены. `gradle`, `adb`, `sdkmanager`, `psql` не найдены в
PATH; это не доказывает отсутствие SDK вне PATH. Команда `go version` не
подтвердила Go toolchain (`Go: Unknown option: version`); имя `go` нельзя
считать доказательством наличия компилятора. Docker daemon не проверялся.

Разрешён доступ Git HTTPS; репозиторий прочитан успешно. Сетевая политика
окружения ограничена списком хостов, включая основные package registries.
Production-секреты/SMTP/VPS и Android-эмулятор не проверены. Доступность сборок
проверяется в P1, не подменяется наличием executables. P0 — документационный
этап, поэтому сборки Android/backend и продуктовые тесты здесь NOT RUN.

## Воспроизведение инвентаризации

```sh
git status --short --branch
git remote -v
git ls-remote origin
git for-each-ref --format='%(refname:short) %(objectname)' refs/heads refs/remotes
git ls-tree -r --long 634feca5d14ed0463d9e6ef7268be1b9e23c48ef
git ls-tree -r --long origin/recovery/legacy-apk
git --version
python3 --version
```

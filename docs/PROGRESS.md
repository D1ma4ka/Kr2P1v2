# KraPiva 2.0 — P5 реализован, автоматическая приёмка PASS

7 ноября 2026, Europe/Istanbul. P0–P4 сохранены. P6 не начат.
Ветка: `codex/krapiva-2-p3`, P4 baseline `24aae823c83a9829864a3bbf827382b82dea3a71`.
Android code/test checkpoint: `0f06c9dedb92c93255aa513c9ab3beb1a081c2f0`.
Backend code/test checkpoint: `a7910a3e4b38d73e80dbb622d1841a9e68249b5d`.
Итоговый docs/baseline-only commit — HEAD ветки; GitHub push проверен.
Draft PR: https://github.com/D1ma4ka/Kr2P1v2/pull/6 (base P2, без merge).

## Реализовано

- Реальные чекины PostgreSQL: повторные записи со своим UUID, quarter rating
  0,25…5 или null, дата/заметка/подача/место/видимость,12 субъективных taste tags,
  до4 собственных фото. Atomic writes, версии/409, idempotency и recovery UUID.
- Личный дневник: поиск/фильтры по стилю/датам/оценке/фото/месту, четыре сортировки
  с bounded keyset; просмотр/редактирование/soft delete только собственных записей.
- Total active checkins и COUNT(DISTINCT positionId), средняя только по rated,
  unknown style отдельно; unique list/filter/drilldown и синхронный wishlist.
- Интерактивные bars стилей/пивоварен по unique и20-bin histogram по чекинам,
  unrated отдельно. Real personal/visible-to-viewer position aggregates с ACL.
- Home/profile: реальные счётчики/средняя/последние записи/ссылки; навигация
  Главная/Каталог/Чекин/Профиль. Значки показывают только существующий SQL count,
  engine P7 и бары пока не реализованы, фиктивных записей/оценок нет.
- KraPiva Dark, native multiple-photo picker, local date/time с UTC API,
  retained editor/recreation; encrypted bounded cache TTL24h и20 editable drafts.
  Черновик не отправлен до подтверждения HTTP; logout очищает cache/drafts/scope.
- Additive migration007, P4→P5 upgrade сохраняет историю/ID/старые request hashes;
  общий iOS-neutral OpenAPI. iOS P10–P13 NOT STARTED, следующий владелец шага — владелец проекта.

## Проверки

| Проверка | Подтверждение |
|---|---|
| Backend | 19/19 real SQL/HTTP/SMTP/media/migrations/contracts PASS, audit0; non-root container/migrate001–007/restart/isolated SQL+media restore SUCCESS — https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42354708687 |
| Android/source | SUCCESS: build/lint,8 JVM tests,9 API35 instrumented tests (сохранены5 P3+2 P4, добавлены2 P5) — https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42355943975 |
| Числа | A/A/B=3/2; повтор/удаление/merge; A13 style X2/Y1; A14 13.25/3, quarter bins/unrated/HALF_UP2 и пересчёт после правки |
| API/ACL | 2 аккаунта, request replay/recovery, optimistic concurrent edits, filters/cursors и malformed bounds400, own/foreign media, friends/profile/hidden/suspended gates, catalogue status404 |
| Android P5 | Real UI editor/photo picker cancel/select→SQL; second API client→refresh/home/charts/drilldown; edit/remove photo/delete; wishlist UI+second client; actual recreation retains unsaved input and logout cancels scope |
| Offline | Реально выключенные radios→cached diary/banner, encrypted unsent draft→reconstructed API/edit, restored network→online data |
| P3/P4 regression | Все прежние tests PASS; cold process session/profile и actual sensor→render PASS |
| Visual/source | P5 screenshot JPEG + original PNG SHA-256 manifest; verify_p0/check_foundation/diff whitespace PASS, оригиналы AGENTS/ТЗ/180 IDs/29 screens/43 scenarios сохранены |

Отменённый первый Android runtime не считается приёмкой; выполнен необходимый
rerun только отменённого job. Пара backend-only правок проверена своим CI;
Android после них не менялся. Документационный final commit не запускает лишнюю сборку.
Полная хронология/файлы/коммиты: [P5](stages/P5.md).
Эксплуатация/правила: [P5 operations](diary/OPERATIONS.md), [ADR-0006](decisions/ADR-0006-diary-statistics.md).
P3/P4 история: [P3](stages/P3.md), [P4](stages/P4.md).

## Границы и следующая задача

Физический optical scan/TalkBack NOT RUN: физического телефона здесь нет.
RU staging/production BLOCKED до решения об инфраструктуре, production SMTP
не включён. Платные сервисы, APK release, main merge, production changes не выполнялись.
Lint без errors, остаются dependency-update/unused-resource/старый UseKtx advisories;
версии не обновлялись ради устранения рекомендаций. Автоочередь публикации SHOULD
не реализована: черновики отправляются явно. Потерянный URI доступ требует повторного
выбора фото. Retention незакреплённых upload assets и production capacity — P8.
A3 friends feed — P6, badge engine/venues/map — P7; эти части не объявляются PASS.
Пустой production каталог сохраняется; synthetic fixtures только для dev/test.
Проверка P4 50k/3M не повторялась и не выдаётся за P5 production load.

**STOP: ждать отдельной команды владельца на P6.**
Пятичасовой остаток≤25% или недельный≤50% → safe commit/push/STOP; реальные
квоты инструментально недоступны, ручной контроль владельца, проценты не придуманы.
Локальные DB/SMTP volumes сохранены. Credentials/SDK/JDK/logs — ignored work/,
секреты/APK в Git не добавлены.

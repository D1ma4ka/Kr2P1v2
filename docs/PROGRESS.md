# KraPiva 2.0 — P5 IN PROGRESS

7 ноября 2026. Новый прямой запрос владельца разрешил P5; P6 не начат.
P5.1 backend18/18 PASS; Android P5 пока не реализован.
План/хронология: [P5](stages/P5.md), ADR-0006.
Ниже сохранена принятая база P4; прежний STOP на P5 отменён новым запросом.

# P4 baseline — автоматическая приёмка PASS

7 ноября 2026, Europe/Istanbul. P0–P3 сохранены; P5 не начат.
Ветка: `codex/krapiva-2-p3`, база P3 `9f617e7`.
Проверенный code/test checkpoint: `a89d4946e8579cadaa02cb84d6fc19459d550258`.
Итоговый документационный коммит — HEAD ветки. Draft PR: https://github.com/D1ma4ka/Kr2P1v2/pull/6

## Реализовано

- PostgreSQL каталог/пивоварни/стили, verified карточки с источником и статусом,
  AND-поиск с нормализацией, фильтры/сортировки/keyset pagination, GIN/B-tree.
- Предложения с фото/правами/источником, версии и owner queue; moderator review,
  duplicate candidates, атомарный merge с сохранением aliases/GTIN/связей и audit.
- GTIN checksum/канонизация, несколько кодов на позицию, unknown отдельно от
  доказанного non-beer; QR отдельно, payload не отправляется и не открывается.
- Android KraPiva Dark: каталог/карточки/пивоварни/предложения, native picker,
  CameraX/ZXing/manual fallback/deny/retry/освобождение камеры.
- Ограниченный encrypted account cache с TTL24h, честный offline banner,
  сохранение состояния при Activity recreation и очистка при logout.
- Общий OpenAPI/UUID/cursors, миграции004–006 без переписывания P2/P3; iOS-compatible
  backend. Сам iOS — будущие обязательные P10–P13, пока NOT STARTED.

## Подтверждённые проверки

| Проверка | Результат и доказательство |
|---|---|
| Финальный Android/source CI | SUCCESS на a89d494: https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42292450630 |
| Build/lint/unit | PASS, 6 JVM tests, включая реальные изображения EAN8/EAN13/UPC-A/UPC-E/ITF14/QR во всех четырёх ориентациях |
| API35 AOSP instrumented | 7/7 PASS: пять прежних P3 + catalog integration + actual MainActivity recreation/logout/cache isolation |
| P4 device flow | Реальный HTTP: search/card/brewery, camera deny→grant/open→release, known/unknown manual lookup, NaN guard→6,5→SQL proposal, radio offline→encrypted card/banner→online refresh |
| P3 regression | Реальные auth/profile/SMTP/media; force-stop/relaunch сессии и actual sensor→render PASS |
| Backend | 17/17 real HTTP/SQL/SMTP/media/contract/migration tests PASS; audit0; non-root container/migrate001–006/restart/isolated SQL+media restore SUCCESS: https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42287948994 |
| SQL scale smoke | 50000 synthetic positions/3000000 GTIN; 30 warm sequential samples: GTIN p95=2.25ms, selective search3.40ms. EXPLAIN B-tree/GIN; это не production HTTP/concurrency capacity |
| Visual baseline | 9 реальных P4 screenshots просмотрены; docs/design/p4-review.jpg + original PNG SHA-256 manifest |
| Source/docs | verify_p0.py + check_foundation.py + git diff --check; исходные AGENTS/ТЗ/180 IDs/29 screens/43 scenarios сохранены |

Backend CI относится к последнему изменению runtime сервера (a60677b, включено
в5235c47); последующие изменения только Android/tests/docs, full backend suite
без необходимости не повторялась. Полная хронология, файлы и коммиты:
[отчёт P4](stages/P4.md); P3 отдельно: [отчёт P3](stages/P3.md).
Инструкции: [операции](catalog/OPERATIONS.md), [масштабирование](catalog/SCALING.md).

## Непроверенное и границы

Физический телефон/оптический захват печатной этикетки/TalkBack — NOT RUN:
здесь нет физического устройства. Decoder bitmap tests и камера эмулятора
не подменяют такую проверку. A9 остаётся PARTIAL по этой ручной части.
RU staging/production — BLOCKED до отдельного решения об инфраструктуре;
production SMTP не включён. Два прежних lint unused-color advisories сохранены.
Каталог стартует пустым; synthetic fixtures только тестовые, импорт сторонней
базы/фотографий не выполнялся. P5–P7 функции и iOS не объявляются готовыми.

**Текущее действие: реализовать P5, затем STOP до команды на P6.** Merge/main/production,
релиз APK, покупки и платные сервисы не выполнялись.
Пороги остановки: пятичасовой остаток≤25% или недельный≤50% → commit/push/STOP.
Реальные остатки квот недоступны; их контролирует владелец, проценты не придуманы.
SDK/JDK/fixtures/credentials/logs остаются ignored work/; секреты/APK в Git не
добавлены. Локальные dev DB/SMTP volumes сохранены, данные не удалять.

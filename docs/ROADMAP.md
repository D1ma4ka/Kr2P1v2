# Roadmap v1.3

Полный план: [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

P0 DONE → P1 DONE → P2 DONE → P3/P1.1 automated PASS → P4 automated PASS →
P5 implemented/automated PASS → P6 implemented/automated PASS (8 ноября 2026) →
П6.1 implemented candidate → **П7 implemented candidate (10 ноября 2026),
backend38/JVM12/build/lint PASS, device/visual acceptance PENDING; STOP перед П8** → P8 → P9.
П7 начат по прямой команде владельца; П6.1 visual acceptance не подменяется.
[П7: результат и ограничения](stages/P7.md).
Ручные P3/P4 physical scan/TalkBack NOT RUN, RU staging BLOCKED.
Затем обязательный iOS-трек: **P10 → P11 → P12 → P13**.
Между всеми этапами обязательная остановка и разрешение.

| iOS этап | Статус | Блокеры/условия | Следующее действие и владелец | Проверенные артефакты |
|---|---|---|---|---|
| P10 | PLANNED | Mac/Xcode, выбор KMP/SwiftUI и iOS target не проверены | После P9 владелец разрешает P10, разработчик делает ADR/проверку среды | Нет |
| P11 | PLANNED | Требуется P10 и общий проверенный backend | Разработчик после разрешения владельца реализует core parity | Нет |
| P12 | PLANNED | Требуется P11, карта/ключи/лицензии и датчики | Разработчик после разрешения владельца реализует social/venues/motion | Нет |
| P13 | PLANNED | Mac/iPhone, Apple Developer/signing/ASC; оплата/upload только отдельно | Владелец предоставляет доступы/разрешения, разработчик выполняет QA/RC | Нет |

Условия ещё не исследовались как фактические блокеры текущей задачи: это
PLANNED, не выдуманный PASS и не основание отменить iOS. Полное завершение
проекта требует обеих платформ, всех MUST и честных статусов доставки/публикации.

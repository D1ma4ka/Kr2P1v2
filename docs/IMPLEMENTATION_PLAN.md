# План KraPiva 2.0 — ТЗ v1.3

Нормативный источник: TECHNICAL_SPECIFICATION.md, §12/15/16/17.
P0 и P1 завершены; их код и результаты не пересоздаются. P2 принят: локальный backend и CI проверены. **P1.1/P3 реализованы, автоматическая приёмка PASS; P4 реализован, автоматические проверки PASS; P5 automated PASS; iOS не начата.**
После каждого согласованного этапа: проверка → diff/status → commit/push →
отчёт → остановка до новой команды владельца. Правила лимитов — PROGRESS.md.

| Этап | Объём и распределение изменений | Выходная проверка | Статус |
|---|---|---|---|
| P0 | Инвентаризация и исходный baseline v1.0 | cf4d28a, исходные документы архивированы | DONE |
| P1 | Android/backend foundation, оригинальные прежние ресурсы и CI | 69ed3e9 CI PASS, fbe95ba отчёт | DONE, не повторять |
| P2 | Расширенная SQL-схема профиля/позиций/чек-инов/соцсети/мест/кодов, migrations, media отдельно от SQL, test SMTP, переносимый локальный запуск, платформенно-нейтральный OpenAPI; конфигурация API окружений | API+DB+media+SMTP локально, persistence после restart, реальные test accounts/CRUD/ACL и contracts, DEPLOYMENT_RU; обновить совместимость source checker с v1.3 | DONE — stages/P2.md; staging/production не развёрнуты |
| P1.1 (уточнение UI) | Новые semantic tokens, original SVG/icon, sans-serif, пять пунктов и home components, ASSET_INVENTORY/visual baseline | Компоненты и screenshot/контраст; не переписывать P1/backend | P3 — адаптация доступных auth/profile screens, не блокирует P2 |
| P3 | Аккаунты/полный профиль/фото/ACL, account-scoped cache; S-01/S-29 real loading и welcome с sensor liquid, fallback, reduced motion; согласованный P1.1 UI | A1/A12/A15/A25–A30, IDOR и настоящий test SMTP; без forced delay | DONE implementation/automated acceptance; physical/TalkBack NOT RUN — stages/P3.md |
| P4 | Новый каталог, brewery card, поиск, предложения/moderation, scanner; отдельная семантика GTIN/QR | A2/A9/A21, пустой старт и реальная камера | DONE implementation/automated; physical scan/TalkBack NOT RUN — stages/P4.md |
| P5 | Чекины/вкусовые теги/фото, фильтры, editable offline drafts, unique list, wishlist, style pie/rating histogram/unrated/drilldown, реальные агрегаты; данные home | A3/A4/A7/A13/A14/A24, изменение/удаление/merge, sync | DONE implementation/automated; physical/TalkBack NOT RUN — stages/P5.md |
| P6 | Взаимные друзья, feed/comments/reactions, публичные агрегаты с ACL; доступ из home | A5/A6, два аккаунта, cursor/retry/privacy/media | DONE implementation/automated: Android11/backend21; stages/P6.md |
| P6.1 — переработка Android UI | Реализовать 14 утверждённых дизайн-референсов (проверить наличие/карта экранов), обе темы Light/Dark, общие Compose tokens/components, сохранить все действия П0–П6 | Реальные screenshot-сравнения обеих тем, build/lint/unit/instrumented regression, документация; без переписывания backend и без П7 | IMPLEMENTED CANDIDATE — 333085b / 7a16b0a, 14 макетов, 10 JVM / 22 backend PASS; общая device/visual acceptance PENDING; design/P61_COMPLETE_CANDIDATE.md |
| P7 | Badge rule engine/10 правил/3 pins; бары и магазины, интерактивная внутренняя карта, clusters, bbox/map-list, reports/admin | A8/A10/A11/A16–A18; реальная карта/лицензия, Craft Republic только после проверки | IMPLEMENTED CANDIDATE — backend38/JVM12/build/lint PASS, native/device/visual acceptance PENDING; stages/P7.md; STOP перед П8 |
| P8 | Android/server regression, cache/security/licenses/accessibility, full visual baseline, motion lifecycle/CPU, backup+restore, load/capacity plan | A1–A30; измерения в описанной среде, не утверждать 500k concurrent | PLANNED |
| P9 | Android RC, APK/hash, CI/runbooks, RU staging/production readiness отдельно | Android DoD §10.3; разрешение на выпуск отдельно; весь проект ещё INCOMPLETE | PLANNED |
| P10 | ADR KMP/Compose MP vs SwiftUI; iOS target/project/adapters/tokens/auth API client | Чистая Mac/Xcode сборка и настоящий HTTP, A31 | PLANNED — обязательный iOS |
| P11 | iOS auth/profile/catalog/brewery/scanner/checkins/photos/diary/wishlist/charts | A32–A37, общие аккаунты/DB/API и реальные устройства по доступности | PLANNED — обязательный iOS |
| P12 | iOS friends/feed/comments/reactions/badges/venues/map, бренд и motion, offline/accessibility | A38–A41, MUST parity и реальные UI/integration проверки | PLANNED — обязательный iOS |
| P13 | Двухплатформенная регрессия, signing/TestFlight при доступах и разрешении, App Store RC/privacy/review | A42/A43, IPA/реальные результаты; отсутствие доступа явно BLOCKED BY OWNER | PLANNED — обязательный iOS |

P1.1 — будущая точечная адаптация, а не повторный P1. Функциональные home/CTA
появляются с реальными API P4–P7: пустой mock home не считается приёмкой.
P6 завершён в обязательном объёме, автоматическая приёмка PASS. P0–P5 сохранены.
П6.1 — UI-этап ДО П7. По последней команде владельца реализован весь объём до общей проверки; промежуточный STOP после главной отменён. Полный кандидат подготовлен в recovery-ветке, визуальная приёмка ещё не выполнена.
По новой прямой команде владельца реализован кандидат П7; результаты — stages/P7.md. Общая приёмка П6.1/П7 на устройстве ещё PENDING. Следующий этап П8 самостоятельно не начинать.
Платформенные границы API проектируются с P2; KMP/iOS код не пишется до P10.

## Внешние решения и срок

Карта: ADR провайдера/лицензии/тайлов/ключей до интеграции P7, без оплаты
по умолчанию. RU VPS/domain/SMTP/legal/signing — до эксплуатации. P10 требует
Mac/Xcode; P13 — Apple Developer/App Store Connect/signing и разрешений.
Следующий владелец действия по началу любого этапа — владелец проекта.
После разрешения разработчик проверяет доступы и делает только этот этап.

Горизонт ноябрь 2026 — апрель 2027 — целевой, не гарантированная дата.
P10 — выбор и bootstrap с высокой неопределённостью tooling; P11 — крупный
перенос core MUST; P12 — крупные карта/social/motion; P13 зависит от QA и
внешних Apple-процедур. До ADR P10 и проверки ресурсов нет доказательной оценки
длительности. Риск расширившегося объёма фиксируется явно, iOS не убирается.
**Android готов + iOS не принята = весь продукт INCOMPLETE.**

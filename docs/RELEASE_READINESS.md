# Готовность KraPiva 2.0 — P4 реализован, автоматические проверки PASS

P0–P3 сохранены; P4 реализован: Android7/7, unit6, backend17/17 PASS; physical scan/TalkBack NOT RUN.
Двухплатформенный продукт и production не готовы. P5 требует новой команды.
Актуальная точка Git/лимиты/доказательства: [PROGRESS](PROGRESS.md), [P4](stages/P4.md).

| Область | Статус | Доказательство / оставшееся |
|---|---|---|
| Provenance v1.0/v1.3 | PASS | исходные SHA, AGENTS unchanged,180 ID/29 screens/43 scenarios |
| Auth/profile/privacy/media/motion P3 | PASS automated | stages/P3.md; реальный API35/SQL/SMTP/cold start; physical/TalkBack NOT RUN |
| Catalog/search/moderation P4 | PASS server | 17 real tests; фото/source/ACL, merge/alias/concurrency, upgrade migrations |
| Android P4 | PASS automated | build/lint/unit6 + instrumented7 PASS, camera deny/grant/open/release, recreation и offline→online; physical optical scan NOT RUN |
| Catalog scale smoke | PASS bounded | 50000 synthetic positions/3000000 GTIN, EXPLAIN/plans/timings; catalog/SCALING.md |
| Restart/backup/restore | PASS local | CI SQL+media isolated restore, session/ACL/photo hashes; P2/P3/P4 server foundation |
| Diary/wishlist/stats/social/map/badges | INCOMPLETE | P5–P7; имеющаяся SQL foundation не подменяет функции |
| Device/accessibility/security/load | INCOMPLETE | P8 regression/physical/production load/retention и release R8 ещё нужны |
| RU staging/production | BLOCKED | размещение/DNS/TLS/SMTP/legal/budget не согласованы и не развёрнуты |
| iOS/MUST parity/publication | INCOMPLETE | P10–P13, обязательный второй клиент; Mac/Xcode/iPhone/Apple доступы позже |

LOCAL_BACKEND_VERIFIED относится только к проверенному локальному окружению.
STAGING_VERIFIED/PRODUCTION_DEPLOYED не заявляются. Synthetic data не переносить
в production. Конкретные A-сценарии отражены в QA_TEST_PLAN, не все43 PASS.

До релиза: измерения нагрузки/доступности на разрешённом RU staging, production
SMTP/storage/backup/retention/legal/signing, dependency/license review и устранение
зафиксированных lint advisories. Публичный релиз/покупки/production/main merge
не выполнялись. Draft PR6 содержит накопленные P3–P4 поверх рабочей P2 ветки.

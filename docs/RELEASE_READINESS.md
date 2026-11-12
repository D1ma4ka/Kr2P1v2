# Текущая готовность — П8 automatic candidate, 12 ноября 2026

Backend38/38 и Android JVM12/12/build/lint/package PASS. Исправления П8
проверены автоматическими тестами; native SDK runtime/visual/device acceptance
NOT RUN, так же как общая визуальная приёмка П6.1/П7.
P8.1 autosave/deduplication implementation candidate добавлен; manual offline,
reboot и update-over-install acceptance остаётся PENDING.
Реальные verified venues/production provider/RU access ещё не подтверждены.
**Production и двухплатформенный продукт INCOMPLETE. P9 не начинать.**
Актуальные доказательства: [П8](stages/P8.md), [П7](stages/P7.md), [PROGRESS](PROGRESS.md),
[verification manifest](places/VERIFICATION.json). Ниже историческая приёмка П5;
она не заменяет проверку нового Android UI/MapLibre.

---

# Готовность KraPiva 2.0 — P5 реализован, автоматическая приёмка PASS

P0–P3 сохранены; P4 реализован: Android7/7, unit6, backend17/17 PASS; physical scan/TalkBack NOT RUN.
P5 backend19 PASS, Android8 unit/build/lint +9 instrumented PASS; physical/TalkBack NOT RUN.
Двухплатформенный продукт и production не готовы. P6 требует новой команды.
Актуальная точка Git/лимиты/доказательства: [PROGRESS](PROGRESS.md), [P5](stages/P5.md), [P4 baseline](stages/P4.md).

| Область | Статус | Доказательство / оставшееся |
|---|---|---|
| Provenance v1.0/v1.3 | PASS | исходные SHA, AGENTS unchanged,180 ID/29 screens/43 scenarios |
| Auth/profile/privacy/media/motion P3 | PASS automated | stages/P3.md; реальный API35/SQL/SMTP/cold start; physical/TalkBack NOT RUN |
| Catalog/search/moderation P4 | PASS server | 17 real tests; фото/source/ACL, merge/alias/concurrency, upgrade migrations |
| Android P4 | PASS automated | build/lint/unit6 + instrumented7 PASS, camera deny/grant/open/release, recreation и offline→online; physical optical scan NOT RUN |
| Catalog scale smoke | PASS bounded | 50000 synthetic positions/3000000 GTIN, EXPLAIN/plans/timings; catalog/SCALING.md |
| Restart/backup/restore | PASS local | CI SQL+media isolated restore, session/ACL/photo hashes; P2/P3/P4/P5 server foundation |
| Diary/wishlist/stats/home P5 | PASS automated | real SQL/API19 + Android8 unit/build/lint/device9 PASS; stages/P5.md |
| Social/map/badges | IMPLEMENTED CANDIDATE | P6–P7 backend/JVM/API PASS; native MapLibre/device/visual acceptance PENDING |
| Device/accessibility/security/load | INCOMPLETE | P8 automatic checks PASS; physical accessibility, screenshots, production load и retention ещё нужны |
| RU staging/production | BLOCKED | размещение/DNS/TLS/SMTP/legal/budget не согласованы и не развёрнуты |
| iOS/MUST parity/publication | INCOMPLETE | P10–P13, обязательный второй клиент; Mac/Xcode/iPhone/Apple доступы позже |

LOCAL_BACKEND_VERIFIED относится только к проверенному локальному окружению.
STAGING_VERIFIED/PRODUCTION_DEPLOYED не заявляются. Synthetic data не переносить
в production. Конкретные A-сценарии отражены в QA_TEST_PLAN, не все43 PASS.

До релиза: измерения нагрузки/доступности на разрешённом RU staging, production
SMTP/storage/backup/retention/legal/signing, dependency/license review и устранение
зафиксированных lint advisories. Публичный релиз/покупки/production/main merge
не выполнялись. Draft PR6 содержит накопленные P3–P5 поверх рабочей P2 ветки.

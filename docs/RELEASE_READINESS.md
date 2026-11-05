# Готовность KraPiva 2.0 — после P2

**P0/P1/P2 DONE. Двухплатформенный продукт и production не готовы.**
ТЗ v1.3, iOS P10–P13 MUST/PLANNED. Следующий этап P3 только по новой команде.
Текущий Git/лимиты/команды продолжения: PROGRESS.md.

| Область | Статус | Доказательство / оставшееся |
|---|---|---|
| Provenance v1.0/v1.3 | PASS | 12 исходников по SHA, AGENTS unchanged,180 требований/29 экранов/43 сценария |
| Android foundation/config | PASS | f81e10a assembleDebug/lintDebug,0 errors/4 warnings; API env и policy проверены |
| SQL/migrations/API/OpenAPI | PASS P2 | 11 real-service tests, transactions/checksums/concurrency/owner ACL |
| Media и SMTP | PASS P2 | sanitized private files, EXIF/limits/thumbnail/delete failures; реальный Mailpit |
| Restart/backup/restore | PASS local | SQL+media isolated restore, session/ACL/photo hash/mail persistence |
| Backend CI | SUCCESS | [run42047987386](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42047987386), code2d11942 |
| Android/source CI | SUCCESS | [run42047237008](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42047237008), Android f81e10a |
| Source after CI fix | SUCCESS | [run42047987563](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42047987563), Android skipped без изменений |
| Полные account/profile workflows | INCOMPLETE | P3, не заменять dev session provisioning публичным login |
| Catalog/social/stats/map/badges | INCOMPLETE | SQL schema не означает готовые P4–P7 endpoints/UI |
| Offline/device/accessibility/security/load | INCOMPLETE | P3–P8, реальной capacity500k+ нет |
| RU staging/production | BLOCKED | Размещение/DNS/TLS/SMTP/legal/budget ещё не согласованы и не развёрнуты |
| iOS/MUST parity/публикация | INCOMPLETE | P10–P13, Mac/Xcode/iPhone/Apple доступы и разрешения позже |

LOCAL_BACKEND_VERIFIED = проверенная P2 foundation. STAGING_VERIFIED и
PRODUCTION_DEPLOYED не заявляются. A19 server persistence PASS; A20 BLOCKED;
остальные полные продуктовые A1–A43 NOT RUN. В матрице 7 требований DONE
(NFR001,FR114,FR118,NFR018,NFR019,NFR021,NFR033);173 INCOMPLETE, из них167 MUST.
Более широкие FR/NFR с клиентом/соцсетью/эксплуатацией остаются незавершёнными.

До релиза проверить targetSdk/AndroidX (4 lint warnings), обновление pinned
GitHub actions с deprecated Node20 runtime и совместимость. Нужны production
SMTP, storage/backup monitoring/retention, legal/signing, load tests. P1 UI и
бренд не перерабатывались: v1.3 UI refinement/motion относится к P3.

Draft PR5 в рабочую P1 ветку, без merge. APK этой сессии — локальный debug
артефакт проверки конфигурации, не новый опубликованный релиз для пользователей.
Нет покупки, production deployment, App Store upload или изменений main.

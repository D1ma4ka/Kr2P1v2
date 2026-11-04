# Готовность KraPiva 2.0

**Двухплатформенный релиз не готов. ТЗ v1.3 принято документационно.** Завершены P0 и локальные проверки основы P1.
**P1 DONE:** все три job [CI](https://github.com/D1ma4ka/Kr2P1v2/actions/runs/41888047279) прошли.
Четыре предупреждения о версиях, runtime/device проверки и дальнейшие задачи
зафиксированы в [PROGRESS.md](PROGRESS.md).
Реализованы Android splash/стартовый экран/диалог, brand assets и HTTP liveness.
БД, аккаунтов, каталога, социальной сети и остальных пользовательских функций
пока нет. P2 не начат и требует нового сообщения владельца «Продолжай».

[Матрица требований](REQUIREMENTS_MATRIX.md): NFR-001 DONE; остальные 179 FR/NFR
пока INCOMPLETE, из них 173 MUST. A1–A43 NOT RUN; реальный HTTP P1 не является
продуктовым E2E клиента и сервера.

| Группа | Состояние | Доказательство / следующий шаг |
|---|---|---|
| P0 provenance/requirements | DONE | cf4d28a, восемь неизменённых документов |
| Android source build | DONE (NFR-001) | P1 clean assembleDebug/lintDebug; запуск на устройстве NOT RUN |
| HTTP process foundation | PASS | P1 TypeScript build, четыре настоящих локальных HTTP/config/process tests |
| CI foundation | См. [отчёт P1](stages/P1.md) | build/lint/HTTP/assets; нет migration/contract suite до P2 |
| Brand vectors/PNGs/icons/tokens | PASS, частичное X-05 | Экспорты/контраст/XML проверены; функциональные screenshots/TalkBack NOT RUN |
| SQL/migrations/OpenAPI/media/SMTP | INCOMPLETE | P2 |
| Auth/profile/catalog/checkins/social | INCOMPLETE | P3–P6 |
| Badges/venues/moderation | INCOMPLETE | P7 |
| Offline/security/restore/license audit | INCOMPLETE | P8 |
| Production РФ и доступ без VPN | BLOCKED | Провайдер/бюджет/домен/почта/размещение не подтверждены |
| Юридическая готовность и выпуск | BLOCKED | Нет legal review, release signing и решения о публикации |

Нет разрешения на merge, deployment, покупки или выпуск. Локальная APK
подписана автоматически созданным debug key, не опубликована, не release candidate.

## Новые критерии готовности v1.3

Обязательны Android P0–P9 и iOS P10–P13; iOS PLANNED, артефактов/тестов нет.
Полное MUST parity, один API/DB/account, Cross-platform A31–A43 и Mac/Xcode/iPhone
проверки; TestFlight/Apple submission требуют отдельных доступов и разрешений.
Новый бренд/пять пунктов/home/motion, встроенная карта/магазины/диаграммы/
фильтры/черновики — INCOMPLETE, планы см. ROADMAP и UPDATE_v1.3.
P1 не переоткрывается. Его CI доказательство относится к 69ed3e9 и прежним
входам. Проверка source provenance в CI требует адаптации к v1.3 в P2.
LOCAL_BACKEND_VERIFIED, STAGING_VERIFIED, PRODUCTION_DEPLOYED не заявляются:
сейчас есть только liveness foundation без БД/пользовательских функций.

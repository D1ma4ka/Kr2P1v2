# Готовность KraPiva 2.0

**Релиз не готов.** Текущий результат — P0 (требования и репозиторий).
Приложение, API и БД не созданы; работающая социальная сеть не заявляется.

Точная разбивка всех 82 FR/NFR, 22 экранов и дополнительных обязательств —
[REQUIREMENTS_MATRIX.md](REQUIREMENTS_MATRIX.md). Все продуктовые требования
пока INCOMPLETE; все A1–A12 — NOT RUN. 70 нумерованных MUST ещё не закрыты.

| Группа | Состояние | Что требуется |
|---|---|---|
| Репозиторий/документы P0 | Проверки в [отчёте P0](stages/P0.md) | Сохранённые входы, решения, проверяемый baseline |
| Android/backend builds, API/SQL и миграции | INCOMPLETE | P1/P2, реальные clean builds и постоянство |
| Auth/profile/catalog/checkins/social | INCOMPLETE | P3–P6, клиент↔API и A1–A7/A9/A12 |
| Badges/venues/moderation | INCOMPLETE | P7, A8/A10/A11 |
| Media/ACL/offline/security/restore | INCOMPLETE | P2–P8, негативные тесты и фактический restore |
| Brand assets/accessibility/screenshots | INCOMPLETE | P1/P8, реальные ресурсы и Android проверки |
| Debug APK/hash/CI | INCOMPLETE | P1/P9; на P0 сборки не запускались |
| Production в РФ и доступ без VPN | BLOCKED | Нет подтверждённых провайдера/бюджета/домена/почты/инфраструктурного размещения |
| Юридическая готовность и выпуск | BLOCKED | Нет профильного review, ключей release, решения о публикации |

Production-блокеры не препятствуют локальным P1–P8. Статус релиза обновляется
по фактическим результатам, а не по числу написанных документов.

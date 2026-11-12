# Platform parity — план v1.3

Обязательный iOS-трек PLANNED P10–P13. Наличие Android-основы P1 не означает
реализацию MUST на Android или iOS. Общее хранилище/аккаунты/API — с P2;
камера, sensors, photo picker, карта и secure storage через platform adapters.
Решение KMP/SwiftUI — ADR перед P10, без массового переписывания Android сейчас.

| Область MUST | Android план | iOS эквивалент/план | Приёмка | Фактическое доказательство |
|---|---|---|---|---|
| Build/bootstrap | P1 DONE | P10 | NFR-001/A31 | Android CI 69ed3e9; iOS NOT RUN |
| Auth/session/privacy/profile | P3 automated PASS | P11, Keychain/server ACL NOT RUN | A1/A12/A15/A32/A33 | Android regression CI42355943975; iOS NOT RUN |
| Catalog/brewery/scanner/GTIN vs QR | P4 automated PASS, physical optical scan NOT RUN | P11, native camera NOT RUN | A2/A9/A21/A36 | Android regression CI42355943975; iOS NOT RUN |
| Checkins/photos/diary/unique/tags | P5 implemented/automated PASS | P11 NOT RUN | A3/A4/A34/A35 | Android CI42355943975, backend19; no iOS client yet |
| Wishlist/charts/ratings/unrated | P5 implemented/automated PASS | P11 NOT RUN | A7/A13/A14/A35/A37 | Android CI42355943975, SQL A/A/B/A13/A14; iOS NOT RUN |
| Friends/feed/reactions/comments | P6 implemented/automated PASS | P12 NOT RUN | A5/A6/A38 | Real two-account Android SocialIntegrationTest + recreation/logout PASS CI42463380380 Android11/11; backend21/restore42398072924. A38/iOS NOT RUN |
| Badges/3 pins/rule engine | P7 | P12 | A8/A39 | NOT RUN |
| Bars/shops/in-app map/clusters | P7 | P12 | A10/A16–A18/A40 | NOT RUN |
| Brand/home/navigation/motion | P3/P4/P5 partial product, P7 implemented, P8 automatic checks PASS | P10/P12 NOT RUN | A22–A30/A41 | Android build/lint/JVM PASS; screenshots/TalkBack/device acceptance PENDING |
| Cache/drafts/offline/reconnect | P3/P4/P5 automated PASS, P8 client hardening PASS | P11/P12/P13 NOT RUN | A19/A20/A34–A41 | Backend38/38 and Android package PASS; physical radio/reconnect regression NOT RUN |
| Security/accessibility/backup | P8 automatic security/regression checks PASS; device/retention/staging PENDING | P13 NOT RUN | NFR/§17.4/A43 | Backend headers/media/ACL/migrations PASS; physical TalkBack/iOS NOT RUN |
| Delivery/store readiness | P9 future | P13 future | A42/A43 | P5 CI debug build only, no RC/public APK/TestFlight |

Перед приёмкой P13 каждая из 174 строк MUST REQUIREMENTS_MATRIX должна иметь
ссылку на Android-доказательство и iOS-эквивалент (для общих серверных требований
— один общий тест плюс клиентские проверки). До этого таблица — групповой план,
а не утверждение достигнутого паритета. Различия permissions/VoiceOver/Dynamic
Type/native UX уточняются по реальной реализации. TestFlight/публикация не
объявляются выполненными без фактической доставки и отдельного разрешения.

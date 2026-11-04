# Platform parity — план v1.3

Обязательный iOS-трек PLANNED P10–P13. Наличие Android-основы P1 не означает
реализацию MUST на Android или iOS. Общее хранилище/аккаунты/API — с P2;
камера, sensors, photo picker, карта и secure storage через platform adapters.
Решение KMP/SwiftUI — ADR перед P10, без массового переписывания Android сейчас.

| Область MUST | Android план | iOS эквивалент/план | Приёмка | Фактическое доказательство |
|---|---|---|---|---|
| Build/bootstrap | P1 DONE | P10 | NFR-001/A31 | Android CI 69ed3e9; iOS NOT RUN |
| Auth/session/privacy/profile | P3 | P11, Keychain/server ACL | A1/A12/A15/A32/A33 | NOT RUN обе платформы |
| Catalog/brewery/scanner/GTIN vs QR | P4 | P11, native camera | A2/A9/A21/A36 | NOT RUN |
| Checkins/photos/diary/unique/tags | P5 | P11 | A3/A4/A34/A35 | NOT RUN |
| Wishlist/charts/ratings/unrated | P5 | P11 | A7/A13/A14/A35/A37 | NOT RUN |
| Friends/feed/reactions/comments | P6 | P12 | A5/A6/A38 | NOT RUN |
| Badges/3 pins/rule engine | P7 | P12 | A8/A39 | NOT RUN |
| Bars/shops/in-app map/clusters | P7 | P12 | A10/A16–A18/A40 | NOT RUN |
| Brand/home/navigation/motion | P1.1/P3–P8 | P10/P12 | A22–A30/A41 | P1 старый бренд не доказывает v1.3 |
| Cache/drafts/offline/reconnect | P3/P5/P8 | P11/P12/P13 | A19/A20/A34–A41 | NOT RUN |
| Security/accessibility/backup | P8 | P13 | NFR/§17.4/A43 | NOT RUN |
| Delivery/store readiness | P9 | P13 | A42/A43 | Только старая P1 debug APK, не RC |

Перед приёмкой P13 каждая из 174 строк MUST REQUIREMENTS_MATRIX должна иметь
ссылку на Android-доказательство и iOS-эквивалент (для общих серверных требований
— один общий тест плюс клиентские проверки). До этого таблица — групповой план,
а не утверждение достигнутого паритета. Различия permissions/VoiceOver/Dynamic
Type/native UX уточняются по реальной реализации. TestFlight/публикация не
объявляются выполненными без фактической доставки и отдельного разрешения.

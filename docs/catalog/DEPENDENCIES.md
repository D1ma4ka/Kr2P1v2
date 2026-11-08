# P4 — новые зависимости и материалы

| Компонент | Версия | Лицензия | Использование |
|---|---|---|---|
| AndroidX CameraX camera-camera2/lifecycle/view | 1.4.2 | Apache-2.0 | Camera lifecycle, preview, image analysis |
| ZXing core | 3.5.3 | Apache-2.0 | Bundled EAN/UPC/GTIN и отдельное распознавание QR |
| PostgreSQL pg_trgm | в составе PostgreSQL17.7 | PostgreSQL License | GIN trigram index; локальный сервер |

Maven coordinates зафиксированы в app/build.gradle.kts. Ни runtime download,
ни API keys, ни платной модели/аналитики. Перед публичным релизом собрать полный
transitive NOTICE/licenses и проверить R8/release dependency audit (P8/P9).
Новый сторонний каталог/фотографии не скачивались. Placeholder — оригинальный
KraPiva mark из P3; все generated test images/records явно synthetic и disposable.

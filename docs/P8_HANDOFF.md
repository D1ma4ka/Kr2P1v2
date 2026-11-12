# Передача П8 · актуализация 12 ноября 2026

Ветка: `codex/krapiva-p8`
База: П7 `43af2ce`
Документ этапа: [stages/P8.md](stages/P8.md)

П8 завершил доступную автоматическую стабилизацию. Backend 38/38 и Android
JVM/build/lint/package прошли, локальные миграции 001–010 проверены. Исправления
касаются только надёжности Android-сессии/сетевых чтений/выхода и security headers
Fastify; публичные API и схема данных не менялись.

Готовый APK: `work/p8/krapiva-p8-debug.apk` (SHA-256 указан в отчёте этапа).
Артефакты `work/`, `.env`, Docker volumes и credentials остаются локальными и не
добавляются в Git.

Не проверено: реальный телефон/эмулятор, native MapLibre, camera optical scan,
TalkBack, 320dp/large font, screenshots, staging/production и iOS. Следующее
действие — ручная device/visual acceptance П8 с разрешённым локальным или staging
API; П9 не начинать автоматически.

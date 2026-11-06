# P3 — визуальная приёмка

6 ноября 2026. Code/test checkpoint0ca66fd, CI **SUCCESS**:
https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42231674746

Просмотрены **8 реальных synthetic кадров** с API35 AOSP x86_64/KVM:
welcome, profile, независимые avatar/cover, login после reset, actual cold
process MainActivity и противоположные sensor tilts; также liquid test fixture.
KraPiva Dark/cream/amber/hop, scanner corners, читаемый текст и доступные CTA
подтверждены на кадрах продукта, системных ANR/диалогов поверх них нет.
Fixture liquid-states занимает собственную Surface на test Activity; белая
часть вне неё не является экраном продукта или принятой product palette.

- [Контактный лист JPEG](p3-android-baseline.jpg) — производный обзор из настоящих PNG.
- [Происхождение и SHA-256 исходных PNG](p3-android-baseline.json).
- Исходные1080×1920 PNG и XML test reports — CI artifact
  `p3-android-test-evidence`, срок7days. JPEG/manifest остаются в Git.
- Реальная поверхность жидкости измерена в исходных PNG: +10px/−4px при hero231px;
  это не только previewAngle/unit math и не макет.

Instrumented screenshots записываются в MediaStore Downloads/KraPivaP3,
host cold/sensor checks — work/screenshots/. Нет APK/ключей/.env/реальных
аккаунтов. Янтарные прямоугольники — собственное synthetic media из picker.
Исходный reference PNG неизменён и не встраивается в приложение. Оригинальные
SVG/PNG/vectors отдельно прошли XML/dimension/contrast guard.

Ранние Quickstep/SystemUI ANR кадры **отклонены**. CI ждёт clean launcher;
crash/ANR при TestEvidence screenshot делает тест FAIL. Блокированный локальный
TCG software emulator не считается доказательством продуктовой ошибки/PASS.

Home/catalog/checkin/badges baseline — с настоящими функциями P4–P7;
iOS parity — P12/P13. Physical phone/TalkBack **NOT RUN**; эти изображения
и automated320dp/fontScale2 не подменяют ручную проверку A29/FR136.

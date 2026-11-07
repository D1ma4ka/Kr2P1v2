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


# P4 — визуальная приёмка

7 ноября 2026, Europe/Istanbul; code/test `a89d494`, CI SUCCESS:
https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42292450630

Просмотрены 9 реальных synthetic кадров: каталог, пивоварня, карточка online/
offline/после recreation, owner queue, scanner deny/open/unknown. Graphite/amber
cards/navigation, оригинальный cream mark вместо отсутствующего фото; нет
демонстрационных рейтингов. Native camera preview ограничен viewport220dp,
заголовок/manual controls не перекрыты. Системных ANR поверх кадров нет.

- [P4 contact sheet](p4-review.jpg) — производный JPEG из CI contact sheet.
- [Исходные PNG SHA-256/размеры/run/source SHA](p4-screenshot-manifest.json).
- Оригиналы1080×1920 и XML доступны в Actions artifacts7days; JPEG/manifest в Git.

Цветные блоки камеры — emulated camera scene; каталог и имена — исключительно
synthetic fixture, отправленная в настоящую disposable dev DB через API/SQL.
Это не продуктовый mock catalog. Физический optical scan/TalkBack NOT RUN.
Home/checkin/badges baseline — будущие P5–P7; P5 пока не начат.


# P5 — визуальная приёмка

7 ноября 2026, Europe/Istanbul. Code `0f06c9dedb92c93255aa513c9ab3beb1a081c2f0`, CI SUCCESS: https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42355943975
Просмотрены настоящие synthetic-only API35 кадры home empty/counts, чек-ин/фото,
диаграммы/unique drilldown/wishlist, offline diary и unsent drafts/recreated editor.
[P5 contact sheet](p5-review.jpg) — производный JPEG из CI contact sheet;
[Original PNG SHA-256/размеры/run](p5-screenshot-manifest.json). Оригиналы и XML
в Actions artifact7days, JPEG/manifest остаются в Git. Нет APK/credentials/реальных
пользовательских данных. Physical/TalkBack NOT RUN; P6/P7 screens не заявляются.

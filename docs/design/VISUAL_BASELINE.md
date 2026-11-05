# P3 — визуальная приёмка

Исходный reference PNG неизменён и не встраивается в приложение.
Оригинальные SVG/PNG/Android vectors проверены XML/dimension/contrast guard.
Снимки принимаются только с реально запущенного APK, не из макета.

Instrumented tests сохраняют synthetic `p3-welcome.png`, `p3-profile.png`,
`p3-profile-photos.png`, `p3-liquid-states.png` в app external files. CI собирает
их в `p3-android-test-evidence` (7 days), без APK/ключей/.env/реальных аккаунтов.
Результат runtime ещё ожидается; до него скриншоты не считаются принятыми.

Local AOSP15 TCG без KVM не дал надёжного baseline: System UI ANR; такой кадр
не является принятой картинкой продукта. Финальные ссылки/results — stages/P3.md.
Home/catalog/checkin/badges baseline появится с их настоящими данными P4–P7;
iOS визуальный паритет — P12/P13. Физический телефон/TalkBack пока NOT RUN.

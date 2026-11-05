# KraPiva 2.0 — P3 WIP, АВАРИЙНАЯ ОСТАНОВКА

6 ноября 2026, Europe/Istanbul. Разработка остановлена по прямой команде
владельца. **Не запускать задачи, сборки, тесты или исследования до «Продолжай».**
P0/P1/P2 DONE. **P3 не принят: runtime-проверки Android не завершены. P4 не начат.**

Ветка: `codex/krapiva-2-p3`, база P2 `bf995357098df505965dd4425afca6f5eeff4810`.
Последний code/config checkpoint перед отчётом — `af07a7c` (push подтверждён).
Финальный WIP-коммит сохранения — `git log -1`; его полный SHA сообщён владельцу.
Draft PR: https://github.com/D1ma4ka/Kr2P1v2/pull/6, base P2. Merge, force push,
production, публикация APK и расходы не выполнялись.

## Что реализовано

- Настоящие register/SMTP verify/resend/login/reset, scrypt, hash-only access и
  одноразовый rotating refresh, revoke/TTL/replay и SQL rate limits; migration003.
- Полный серверный профиль: displayName/имя/фамилия/пол/город/страна/bio/HTTPS
  ссылки, version conflict, независимые avatar/cover, owner/friends/public и
  field/media ACL. Durable deletion request с дедлайном и блокировкой доступа.
- Android HTTP forms/ViewModel, Keystore AES-GCM atomic session, account-scoped
  encrypted SQLite profile/photos, честные offline/error/retry/save/cancel/logout.
  Фото через системный picker, просмотр/замена/удаление; внешние ссылки браузером.
- KraPiva Dark, оригинальные mark/logo/launcher/vector assets, линейные nav icons.
  Canvas liquid: gravity/accelerometer, rotation/filter/clamp/lifecycle,
  no-sensor loop/reduced motion. Нет forced delay; failed restore — static hero.
- Общий OpenAPI без Android-specific полей; обязательная iOS остаётся P10–P13.
  Будущие destinations disabled; никакой реализации P4 или выдуманных данных.

## Уже подтверждённые проверки

- Local backend full suite **13/13 PASS**, включая SMTP rollback, expired codes,
  независимые token budgets и expired refresh без отзыва новых сессий.
  Последний сохранённый вывод: ignored `work/backend-final-tests.log` (6.94s).
- Backend CI на `8b4dc75` **SUCCESS**: SQL/HTTP/SMTP/OpenAPI/audit/container/
  migrate/restart/isolated restore:
  https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42111333476
- Local Android assembleDebug/assembleDebugAndroidTest/lint/unit PASS,
  2 unit tests; lint 0 errors/2 unused-color warnings. Последний app build/lint:
  `work/android-profile-final.log`, 36s, 12 executed/36 cached.
- Source provenance/180 requirements/29 screens/43 scenarios и asset contrast/
  dimensions/XML/wrapper/hygiene PASS. AGENTS и исходное ТЗ не изменены.
- Ранний Android/source CI без device stage на b235cea SUCCESS:
  https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42105074471

## Что не завершено и известные ошибки

- **Нет подтверждённого Android ↔ API E2E runtime PASS.** Instrumented tests
  написаны/скомпилированы, но ещё не приняты: UI signup/verification/login,
  profile save/cancel/photos/cache/offline/logout/reset, fontScale2/320dp,
  no-sensor/static pixel checks, sensorservice pause/dispose и rendered tilt.
- Local AOSP15 software emulator без KVM показал системные/System UI ANR;
  APK установлены direct adb-shell, результат instrumentation `Process crashed`.
  Такой запуск не считается PASS; эмулятор остановлен.
- GitHub Android build/lint/unit проходят, device stage упирался в настройку
  runner: KVM group → sudo Runas password → унаследованный /root AVD path.
  Последний прочитанный failure:
  https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42111333099
- `af07a7c` исправляет пути: AVD/user/emulator каталоги явно в work/,
  setpriv --reset-env восстанавливает user context; emulator code запускается
  непривилегированно. **Результат этого CI на момент стоп-команды не прочитан.**
  Ранее автоматически запущенный удалённый CI может ещё выполняться;
  WIP-сохранение помечено `[skip ci]`, новых проверок оно не запускает.
- Screenshot baseline pending; physical tilt/TalkBack и RU staging NOT RUN/BLOCKED.
  Запрос удаления реализован, production erasure/retention process — до эксплуатации.
  Цели capacity не измерялись; весь продукт и iOS не объявляются завершёнными.

## Точная следующая задача — только после «Продолжай»

1. Прочитать AGENTS, этот файл и `docs/stages/P3.md`; проверить branch/HEAD/status.
2. **Прочитать уже запущенный CI для `af07a7c`**, не запускать сборку повторно
   автоматически. Если boot прошёл — разобрать реальные instrumented results;
   если нет — прочитать startup log и исправить только конкретную причину.
3. Завершить настоящие Android account/profile/motion acceptance, посмотреть
   реальные screenshots, обновить QA/matrix/P3 evidence. Не выдавать compile
   test APK или unit math за runtime/physical PASS.
4. После проверки сохранить результат, отчёт и STOP. P4 — отдельная команда.

## Лимиты и сохранность

Пятичасовой остаток **≤25%** или недельный **≤50%** → остановка и WIP/push.
Реальные остатки API недоступны; проценты не выдумываются, контроль владельца.
Платные кредиты/сервисы не использовать.

Код/документы сохранены в GitHub. Локальные synthetic credentials, tokens,
media, SDK/JDK/cache/logs — только ignored `work/` и `.env`; не коммитить.
Локальные DB/Mailpit volumes сохранены, dev services могут оставаться запущены;
не применять `down -v`. История и ADR — `docs/stages/P3.md`, `docs/decisions/`.

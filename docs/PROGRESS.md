# KraPiva 2.0 — P3 реализован, автоматическая приёмка PASS

6 ноября 2026, Europe/Istanbul. P0/P1/P2 завершены; реализация P3 завершена.
Полная ручная приёмка ещё не закрыта: физический телефон/TalkBack NOT RUN.
**P4 не начат. Работа остановлена до отдельной команды владельца.**

Ветка: `codex/krapiva-2-p3`. Проверенный code/test checkpoint:
`0ca66fd7804d37669d7fb105484b73ac72fd5587`, push подтверждён.
База P2: `bf995357098df505965dd4425afca6f5eeff4810`.
Итоговый документационный коммит — HEAD ветки (`git log -1`).
Draft PR: https://github.com/D1ma4ka/Kr2P1v2/pull/6 (base P2).
Merge/force push/production/публикация APK/покупки не выполнялись.

## Что реализовано

- Настоящие register/18+/SMTP verify/resend/login/reset, scrypt, hash-only
  access/refresh, TTL/rotation/replay/revoke и SQL budgets; migration003.
- Полный профиль: displayName/имя/фамилия/пол/страна/город/bio, HTTPS-ссылки
  с собственной видимостью, version conflicts, независимые avatar/cover.
  Owner/friends/public и прямые field/media ACL проверяются сервером.
- Durable deletion request: SQL deadline30d, немедленная блокировка доступа,
  публичности и сессий; запрос не называется завершённым стиранием.
- Android HTTP forms/ViewModel, Keystore AES-GCM/AtomicFile session,
  encrypted account-scoped SQLite profile/photos; native picker cancel/select,
  upload/view/replace/remove, save/cancel, offline/retry, server logout/reset.
- KraPiva Dark, оригинальные SVG/PNG/vectors/launcher, cream scanner corners.
  Canvas beer/foam ±8°, gravity/accelerometer, filter/rotation, lifecycle,
  no-sensor loop/reduced static; без forced delay и sensor telemetry.
- Общий OpenAPI без Android-specific identity; iOS остаётся P10–P13.
  Будущие destinations выключены до настоящих функций P4–P7.

## Подтверждённые проверки

| Проверка | Результат и доказательство |
|---|---|
| Финальный Android/source CI | **SUCCESS**, run42231674746 на 0ca66fd |
| Build/lint/unit | PASS; 2/2 unit, lint0 errors/2 unused-color warnings |
| API35 AOSP x86_64/KVM UI | **5/5 PASS**, 3m11s: signup/18+/SMTP verify/login, profile save/cancel, native picker cancel/select, avatar/cover upload/read/view, cover delete→404 при сохранённом avatar, encrypted cache/isolation, offline→online, logout/reset/login новым паролем |
| Actual cold process | **PASS**: force-stop → MainActivity → Keystore session → real API profile, editor enabled; offline copy не принимается |
| Sensor lifecycle | **PASS**: active handles1→0→1→0 при resume/pause/resume/dispose |
| Loop/static/layout | **PASS**: no-sensor bitmap меняется, reduced static неизменен; 320dp/fontScale2/IME |
| Actual sensor→render | **PASS**: surface left=+10px/right=−4px, hero231px; исходные PNG измерены checker |
| Visual baseline | Просмотрены 8 реальных synthetic кадров без ANR; JPEG+PNG hashes сохранены в docs/design/ |
| Backend full suite | **13/13 PASS** ранее на неизменённом backend; CI42111333476: SQL/HTTP/SMTP/OpenAPI/audit/container/migrate/restart/isolated restore |
| Source/assets | PASS: provenance/180 requirements/29 screens/43 scenarios, contrast/dimensions/XML/wrapper/hygiene; исходные ТЗ и AGENTS неизменны |
| Последняя локальная проверка | assembleDebugAndroidTest + lintDebug PASS39s; Python scripts compile PASS |

Android CI: https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42231674746
Backend CI: https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42111333476
Backend после Android-only изменений заново не пересобирался и не тестировался.
Команды и файлы — stages/P3.md и android/README.md.

## Исправленные ошибки

Android HttpURLConnection добавлял form Content-Type к DELETE без тела:
logout/media delete отклонялись JSON-only сервером. Теперь отправляется `{}`
JSON; оба реальных сценария приняты E2E. Исправлены ожидания native picker и
возврата в Compose, enabled navigation после photos и точный sensorservice parser.
CI ждёт чистого AOSP launcher; boot-only Quickstep ANR не принимается за кадр
продукта. Любой crash/ANR при screenshot делает тест FAIL. Старые сбои и WIP
сохранены в хронологии stages/P3.md, включая аварийный checkpoint24bd744.

## Ограничения и следующая задача

- Физический телефон/TalkBack **NOT RUN**; FR-136 и ручная часть A29 не закрыты.
  Автоматический rotation/layout/IME тест не подменяет проверку TalkBack.
- Разрешённый RU staging **BLOCKED**, production SMTP не включён.
- До эксплуатации нужен процесс исполнения deletion/retention/backups (ADR0004,
  P8/P9); capacity не измерялась. Полный продукт/iOS не объявляются готовыми.
- Линт: два unused-color предупреждения, без suppressions/baseline.

**Точная следующая задача: ждать команды владельца.** При дальнейшей приёмке
P3 — ручной A29/TalkBack и physical tilt при наличии устройства; P4 не начинать
без отдельной команды. Незакоммиченного функционального WIP нет.

## Лимиты и сохранность

Пятичасовой остаток ≤25% или недельный ≤50% → немедленные WIP/commit/push/STOP.
Реальные остатки API недоступны; проценты не выдумываются, контроль владельца.
Платные кредиты/сервисы не использовались.
Synthetic credentials/tokens/media/SDK/JDK/cache/logs — только ignored work/ и
.env; в Git их нет. DB/Mailpit volumes сохранены, dev services могут оставаться
запущены; не удалять данные. В GitHub сохраняются код, документы и только
собственные synthetic screenshots, без APK/ключей/.env.

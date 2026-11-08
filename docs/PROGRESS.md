# KraPiva 2.0 — P6 завершён, STOP перед P7

8 ноября 2026, Europe/Istanbul. **Обязательные функции P6 реализованы,
автоматическая приёмка PASS. P0–P5 сохранены. P7 НЕ НАЧАТ.**
Ветка: `codex/krapiva-2-p3`. P5 baseline: `b2cc8441d3b9e0f226bf8e552a9010c6f94a46ab`.
Draft PR: https://github.com/D1ma4ka/Kr2P1v2/pull/6 — без merge.
Последний проверенный code/test commit: `299558e530177a100275c08d15e59f5b2f3c303e`.
Финальный docs/visual-only commit с [skip ci] — HEAD; SHA сообщается владельцу.

## Что реализовано

- Настоящие PostgreSQL поиск видимых пользователей, взаимные заявки/accept/reject/
  cancel/remove, лента друзей с фото, чужие профили и доступная зрителю статистика.
- Уникальный лайк PUT/DELETE, комментарии с UUID и безопасным повтором, удаление
  только своего; cursor pagination, terminal request и comment tombstone без дублей.
- Profile AND checkin privacy, hidden/deleted/suspended, права прямого доступа к
  записям/комментариям/медиа. SQL locks для конкурентных privacy/unfriend и записей.
- Android home/profile/own-checkin entry, поиск/друзья/заявки/лента/профиль/обсуждение;
  KraPiva Dark, loading/empty/error/offline/retry, подтверждение удаления.
- Social data/photos не сохраняются в disk cache; очистка stop/dispose/failure,
  revalidation на resume/refresh. Comment draft/retry UUID переживают Activity
  recreation; явный новый переход сбрасывает ввод, logout очищает account scope.
- Additive migration008, общий Android/iOS-neutral OpenAPI, ADR-0007.
  Нет заглушек, production fixtures, платных ресурсов или отдельной БД для платформ.

## Пройденные проверки

Backend: https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42398072924
`7ad1584379af9604d691e1b64c31a0277b200a3f`: **21/21 PASS**,20.54s, audit0.
Build/OpenAPI, migrations, non-root container, PostgreSQL/SMTP/media restart и
isolated pg_restore PASS. Backend code после этого прогона не менялся.
Два аккаунта social test зарегистрированы через HTTP→SMTP→verification→login;
проверены graph/feed/like/comment/restart, IDOR/privacy/direct media, concurrent
retries и actual PostgreSQL lock wait→privacy/unfriend denial без вставки записи.
Restore сохраняет social UUID/feed/counts/comment, private photo bytes и session ACL.

Android: https://github.com/D1ma4ka/Kr2P1v2/actions/runs/42463380380
`299558e530177a100275c08d15e59f5b2f3c303e`: **SUCCESS,11/11 device tests PASS**,
0 skipped/failed,4m55s. Build/lint/unit8/source/assets PASS. Прежние9 tests сохранены.
SocialIntegrationTest: реальные signup/SMTP/API двух аккаунтов, UI invite/accept/
remove/feed/photo/like/comment/delete, второй клиент проверяет изменения и отзывает
видимость; первый после refresh теряет доступ. Radio-off→error, чужого disk cache нет.
SocialRetentionTest: actual recreation сохраняет ввод/model, новый entry сбрасывает,
logout очищает scope. Cold process→Keystore→API profile/editing PASS. Actual sensor
motion PASS (+10/−4px). Новые тесты после успешной приёмки не запускались.

Семь P6 screenshots проверены; [baseline](design/p6-review.jpg) и
[hash manifest](design/p6-screenshot-manifest.json). Источник42398404892, social2/2
PASS; production social UI после него не менялся. Полный финальный прогон — выше.
Финальные verify_p0.py / check_foundation.py / git diff --check: PASS.

## Исправленные ошибки и сохранность

Direct-entry effect сбрасывал comment при recreation — исправлен navigation token.
UI tests: lazy comment scroll и закрытие IME; native permission coordinate click
попадал в ручную форму сканера — заменён реальным accessibility action конкретной
системной кнопки. Actual denied/granted permission и готовая камера проверяются.
Test fixture соблюдает исходный контракт POST Idempotency-Key / DELETE version.
API/ACL/assertions не ослаблялись. Все11 device tests после исправлений PASS.

Аварийные STOP сохранены b929b7e и97d4153 [skip ci]; работа продолжалась только
после новых команд владельца. Checkpoints b30de95/bb0f898/7ad1584/08386c0/60a1d16/
90f65ca/299558e отправлены в эту же ветку. Полный перечень файлов и доказательств:
[отчёт P6](stages/P6.md), [операции](social/OPERATIONS.md).

## Осталось за пределами обязательного P6

Известных незакрытых функциональных ошибок P6 в проверенных сценариях нет.
Physical phone/TalkBack/physical optical scan NOT RUN; RU VPS/staging не подключён.
FR-068 extended blocking/reports SHOULD отложены; mandatory moderation FR-090
по утверждённой матрице P7/P8. iOS P10–P13 NOT STARTED; A38 parity ещё NOT RUN.
Realtime/push revoke не заявляется: bytes уже доставленного ответа не отзываются,
каждый следующий HTTP проверяет актуальные права. Social offline намеренно закрыт.
Это не production launch и не завершение всего KraPiva2.0.

**Точная следующая задача: ждать команды владельца.** После отдельного разрешения
определить следующий этап по ТЗ/ROADMAP; P7 и VPS самостоятельно не начинать.
Публичный сервер, APK release, main merge/force push и платные сервисы не выполнялись.
Credentials, DB volumes, временные screenshots/logs/backups остаются вне Git.
Точные остатки лимитов недоступны; при сигнале ≤25%/≤50% — safe save/STOP.
**STOP — ожидание следующей команды владельца.**

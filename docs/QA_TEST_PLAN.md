# План проверок — ТЗ v1.3

43 приёмочных сценария: A19 backend foundation проверена, A20 BLOCKED;
P3 автоматическая приёмка PASS (stages/P3.md). P4 SQL/API/search/cards PASS,
Android7/7 PASS, камера/разрешения/освобождение, offline/recreation PASS
(stages/P4.md); физический оптический scan/TalkBack NOT RUN.
P5 automated acceptance PASS: Android9/unit8/backend19, CI42355943975.
P6 automated acceptance PASS: Android11/11 + build/lint/unit8/cold restore/motion CI42463380380.
Backend21/21 + social restart/restore PASS CI42398072924.
Будущие полные продуктовые сценарии P7–P13 NOT RUN. Старые фактические P1 build/HTTP/asset
проверки сохранены в stages/P1.md, не выдаются за продуктовые E2E.
Нужны настоящие API/SQL/media/test SMTP и два аккаунта; privacy/IDOR и чужой
кэш проверяются негативно. Синтетические данные изолированы, реальные данные
пользователей/карточки/фото из PNG не импортируются в production.

| ID | Сценарий из ТЗ | Этап | Результат |
|---|---|---|---|
| A1 | новый пользователь: открыть → 18+ → зарегистрироваться → подтвердить e-mail → войти → создать профиль → перезапустить → данные и авторизация сохранены. | P3 | PASS: Android→SMTP/API/SQL + actual force-stop/relaunch с Keystore (42231674746) |
| A2 | новая позиция: пустой каталог → «не найдено» → предложить позицию с пивоварней → модератор подтверждает → позиция видна в каталоге, без старых импортов. | P4 | PASS automated: empty→proposal→moderation→catalog real SQL/HTTP; Android search/card/brewery + submission→owner queue PASS, CI42292450630 |
| A3 | чекин: найти позицию → выбрать → поставить 4,25 → добавить текст и фото → отправить → запись в собственном дневнике и у друзей по видимости. | P5 | PASS P5 owner editor/photo/SQL/diary; P6 friends photo/feed UI PASS through shared backend; stages/P5.md + stages/P6.md |
| A4 | повтор и уникальность: сделать A, A, B → всего 3, уникальных 2 → удалить B → всего 2, уникальных 1. Для A в разных подтверждённых вариантах — отдельные `positionId`. | P5 | PASS: exact SQL A/A/B3/2, delete/merge/repeats; actual Android4/3→delete repeated A→3/3, unique drilldown; stages/P5.md / CI42355943975 |
| A5 | социальная сеть: U1 отправляет заявку U2 → U2 принимает → U1 публикует видимый чекин → U2 видит его в ленте, ставит реакцию и оставляет комментарий → U1 видит их после перезапуска. | P6 | PASS automated P6: two HTTP/SMTP registered accounts, Android SocialIntegrationTest, server ACL/restart/restore; CI42463380380 Android11/11 and backend42398072924; stages/P6.md |
| A6 | приватность: U1 скрывает чекин → U2 не видит его в ленте, по прямому ID или через API, даже если раньше видел. | P6 | PASS automated P6: two HTTP/SMTP registered accounts, Android SocialIntegrationTest, server ACL/restart/restore; CI42463380380 Android11/11 and backend42398072924; stages/P6.md |
| A7 | wishlist: добавить позицию → проверить в профиле → открыть второе устройство/сессию → список совпадает → удалить → в обоих местах отсутствует. | P5 | PASS: real position-card wishlist add/remove UI + second authenticated API instance, own list/foreign isolation and merge SQL; stages/P5.md / CI42355943975 |
| A8 | значки: реальные события достигают порога → значок открывается → закрепить три → четвёртый отклоняется → состояние сохраняется в БД и UI. | P7 | NOT RUN |
| A9 | штрихкод: скан неизвестного GTIN → «не найдено» → ручное добавление; код после подтверждения карточки даёт совпадение; исходная база OFF отсутствует. | P4 | PARTIAL manual: real GTIN unknown→proposal→accept→lookup SQL PASS; six-format/four-rotation bitmap decoder PASS; native camera deny/grant/open/release + known/unknown manual UI PASS. Physical optical capture of a printed label NOT RUN |
| A10 | бар и карта: поиск реального заведения → встроенная карта/метка → карточка → профиль заведения → привязка к чекину → видимость после сохранения. Проверить карту↔список и cluster. | P7 | NOT RUN |
| A11 | модерация: жалоба на чужой публичный контент → модератор скрывает → недоступно API и UI → есть запись в audit log. | P7 | NOT RUN |
| A12 | восстановление: сброс пароля → письмо → смена пароля → предыдущие сессии отозваны по принятой политике. | P3 | PASS: Android SMTP reset, new password login and durable session revoke (run42231674746); P4 regression P3 tests PASS |
| A13 | стиль: 4 чекина трёх уникальных позиций: стиль X у двух позиций, Y у одной → диаграмма X=2, Y=1; переход X открывает именно 2 позиции, повтор не меняет количество. | P5 | PASS: real SQL X2/Y1 after4 checkins; Android style bar→exact2 unique positions; repeat/delete/merge recalc; stages/P5.md / CI42355943975 |
| A14 | оценки: 4 чекина: `4.25`, `4.75`, `null`, `4.25` → histogram 4.25×2, 4.75×1, unrated×1, средняя `13.25/3=4.41666...` (формат отображения по единому правилу округления); поменять 4.75 на 5.0 → пересчёт. | P5 | PASS: SQL17/19/null/17 quarters→13.25/3,19→20 recalc; JVM HALF_UP2 and Android4.42/20-bin/unrated; stages/P5.md / CI42355943975 |
| A15 | профиль: обновить avatar/cover, пол/город/страну/соцссылку → перезапуск → видит владелец, другой аккаунт видит только разрешённое. | P3 | PASS automated: profile/media/privacy + native picker save/cancel/view/delete and cold restore (run42231674746); physical/TalkBack NOT RUN |
| A16 | карта: несколько проверенных мест из одного bbox → реальные метки и cluster при уменьшенном масштабе → zoom → разные метки → tap → карточка → подробный профиль; изменённый фильтр синхронизирует список и карту. | P7 | NOT RUN |
| A17 | карта без разрешений: отклонить доступ к геолокации → карта продолжает работать с ручным выбором города; офлайн/недоступный тайл → отображается ошибка и fallback список, не пустой серый экран. | P7 | NOT RUN |
| A18 | бары/магазины: фильтр по типу возвращает корректные объекты; неизвестный адрес/координаты не выдумываются; привязка к чекину имеет проверяемую ссылку. | P7 | NOT RUN |
| A19 | backend: поднять API+DB+media+test SMTP → создать двух пользователей и чекины → перезапустить контейнеры с сохранёнными volumes → данные/фото/сессии не потеряны; проверить неавторизованный доступ. | P2/P8 | PASS local backend: real HTTP+SQL/media/SMTP suite и runtime-check; не product signup E2E |
| A20 | сервер позже: сменить dev `API_BASE_URL` на разрешённый staging домен без изменения бизнес-кода Android; собрать staging и выполнить реальный E2E с тем же OpenAPI. Без доступа отметить `BLOCKED`, а не `PASS`. | P2/P9 | BLOCKED: нет разрешённого RU staging; dev Android↔API integration работает, env config проверена |
| A21 | коды: неизвестный GTIN ≠ непивной; GTIN и произвольный QR разрабатываются отдельно; в тестовых 3 млн **синтетических** кодов проверить индекс/план запроса и длительность, измерив на указанном оборудовании, не делая заявлений о production без нагрузки. | P4/P8 | PASS bounded SQL smoke: 50000 synthetic positions/3000000 GTIN, EXPLAIN B-tree/GIN, p95 2.25/3.40ms warm sequential; docs/performance/P4-catalog-benchmark.json |
| A22 | цветовая система: домашний экран, карточка позиции, профиль, каталог и значки используют зафиксированные токены; снимки показывают единый визуальный стиль, нет случайной зелёно-коричневой палитры. | P1.1/P8 | PARTIAL: P3/P4/P5 KraPiva screens/baselines; badge and full product screens P7/P8; stages/P5.md / CI42355943975 |
| A23 | навигация: доступны все пять пунктов `Главная/Каталог/Чекин/Бары/Профиль`; `Чекин` открывает реальный поток создания записи, а дневник, друзья, статистика, wishlist и значки доступны через вложенную навигацию. | P3–P8 | IMPLEMENTED CANDIDATE: P5 real home/catalog/checkin/profile+diary/unique/stats/wishlist; P6 social nested entry; P7 bars/badges/map entrypoints; native device/navigation acceptance NOT RUN; stages/P8.md |
| A24 | данные главной: пустая серверная учётка показывает нули, пустые списки и CTA; при трёх чекинах A/A/B выводит total=3, unique=2, динамическое количество значков; нет фиктивных записей из референса. | P5–P8 | PASS P5: fresh account0/empty/recent, backend A/A/B3/2, Android4/3 and live delete counters; actual earned-badge SQL count (engine P7); stages/P5.md / CI42355943975 |
| A25 | запуск быстро: после готового восстановления сессии приложение пропускает custom loading без искусственной задержки; system splash корректно отображает иконку. Повторному пользователю onboarding принудительно не показывается. | P3/P8 | PASS: cold process восстанавливает online profile; нет forced delay (42231674746) |
| A26 | наклон: при симулированном наклоне влево/вправо наклоняется **поверхность жидкости/пена** внутри неподвижного/слегка отклонённого контура бокала; амплитуда ограничена, движение гладкое. | P3/P8 | PASS: unit 2/2; real emulator sensor→PNG slope +10/−4px (42231674746) |
| A27 | fallback: без sensor API/доступных датчиков onboarding продолжает работать, показана мягкая loop-анимация; при reduced-motion — статичный бокал, регистрация доступна. | P3/P8 | PASS на API35: no-sensor bitmap меняется, reduced-motion bitmap неизменен (run 42222956219) |
| A28 | lifecycle: перейти из onboarding → фон → вернуться → выйти: нет подписок на датчик вне видимого экрана, дубликатов listeners и утечки контекста/ресурсов. | P3/P8 | PASS на API35: active handles 1→0→1→0 при resume/pause/resume/dispose (42231674746) |
| A29 | форма: на узком экране, с клавиатурой и увеличенным шрифтом пивной hero не скрывает email/пароль/подтверждение/CTA и не мешает TalkBack. | P3/P8 | PASS на API35: 320dp/fontScale2/keyboard (run 42222956219); TalkBack NOT RUN |
| A30 | failure: network timeout/error вызывает явное сообщение и retry; брендовая анимация не маскирует failure и не ведёт к бесконечному ожиданию. | P3/P8 | PASS: реальная потеря/возврат сети, сохранённый профиль, retry (42231674746); connect10s/read15s |
| A31 | сборка: iOS проект собирается в чистой поддерживаемой среде macOS/Xcode; версия, toolchain и команды указаны. Без Xcode статус `NOT_RUN`, не `PASS`. | P10 | NOT RUN |
| A32 | один аккаунт: зарегистрироваться на Android и войти на iOS тем же e-mail; профиль и публичные данные совпадают; сервер не создаёт дубликат учётки. | P11 | NOT RUN |
| A33 | изменение на iPhone: сменить на iOS никнейм/аватар/город, обновить Android → данные те же; повторить наоборот, соблюдая privacy. | P11 | NOT RUN |
| A34 | чекины и уникальность: Android делает A/A, iOS делает B → всего 3 чекина и 2 уникальные позиции на обоих устройствах; повтор B на iOS → всего 4, уникальных по-прежнему 2. | P11 | NOT RUN |
| A35 | фото и wishlist: добавить фото и wishlist с iPhone → увидеть через Android; убрать из Android → синхронизировать iPhone; серверное удаление медиа корректно. | P11 | NOT RUN |
| A36 | поиск и сканер: один и тот же известный barcode на двух устройствах открывает один `positionId`; неизвестный показывает одинаковое состояние «не найдено» без фальшивого результата. | P11 | NOT RUN |
| A37 | статистика: после редактирования/удаления чекина стиль/гистограмма/средняя/уникальные позиции корректно пересчитываются на Android и iOS. | P11 | NOT RUN |
| A38 | social: iOS-пользователь добавляет Android-пользователя в друзья, ставит реакцию, пишет комментарий; изменения появляются на второй платформе и проверяются privacy/ACL. | P12 | NOT RUN |
| A39 | достижения: одно и то же серверное достижение/закрепление отображается на обеих платформах, повторный чекин не создаёт ложного дополнительного прогресса. | P12 | NOT RUN |
| A40 | карта: проверенные бары/магазины открываются на интерактивной iOS карте; кластеры, фильтры и экран места функционируют; отказ в геолокации не скрывает список. | P12 | NOT RUN |
| A41 | motion + accessibility: на iPhone колышется **пиво в бокале** при наклоне; Reduce Motion/fallback; VoiceOver и крупный текст; анимация не блокирует регистрацию и не работает после закрытия экрана. | P12 | NOT RUN |
| A42 | доставка: при доступных учётной записи Apple Developer и подписи тестер устанавливает iOS-сборку через TestFlight; при отсутствии внешних предпосылок — честно `BLOCKED BY OWNER`, не фиктивная публикация. | P13 | NOT RUN |
| A43 | релиз и безопасность: подготовлены App Store release candidate, privacy disclosures, правила для контента/возраста, реальные результаты тестов и чек-лист ревью; никаких секретов в репозитории; публикация только по отдельному разрешению. | P13 | NOT RUN |

## Дополнительные проверки

Unit+SQL: A/A/B, удаление/merge, servingFormat, rating quarters/null,
style знаменатель/unknown, histogram/unrated/average, privacy aggregate,
10 badge rules/3 pins и идемпотентность. Integration: migrations/restart,
SMTP с чтением письма, session revoke, owner/media ACL, TTL/delete/retention.
Contract: одна OpenAPI/БД для Android/iOS, одинаковые IDs/errors/cursors/retries.
UI: 29 экранов, 5 пунктов, home реальные нули/данные, screenshot baseline,
large text, keyboard/insets, TalkBack/VoiceOver, camera/location denial.
Motion: tilt clamp/smoothing, no sensor/reduced motion, rotation, pause/resume,
отсутствие фоновых listeners/отправки sensor data и forced delay; CPU/GPU замеры.
Maps: настоящий SDK, лицензии/атрибуция, bbox limits, clusters, list-map/filter,
Craft Republic только после проверки фактов/прав; без доступа — BLOCKED.
Operations: restore DB+media, portable dev/staging/prod, synthetic load/EXPLAIN,
500k/1M/3M как цели, не результаты. Нет публичного запуска без разрешения.
Перед P10 — ADR и Mac/Xcode; P13 — настоящий TestFlight при разрешении и доступах,
иначе BLOCKED BY OWNER, а не подмена локальной сборкой.

PASS только с командой/средой/commit/result или описанным manual устройством.
Актуальные команды, среды, SHA и ограничения P3 — stages/P3.md; будущие сценарии
не повышаются до PASS по одному факту сборки Android или HTTP smoke.
Старый ACCEPTANCE_CHECKLIST.md сохранён как исторический список v1.0;
актуальная полная нормативная приёмка — §10/15/16/17 ТЗ и эта таблица.


## П7 — 10 ноября 2026

Реальный PostgreSQL/HTTP/SMTP backend38/38, Android JVM12/12, APK/androidTest
compilation/lint PASS. Матрица деталей — [stages/P7](stages/P7.md).
Новые `places/P7IntegrationTest` используют настоящие API и MapLibre GL: clustering,
нажатия, pinch/pan, переход к странице/чекину, подтверждённые pins/четвёртый disabled.
Они пока только скомпилированы. A8/A10/A11 UI/A16–A18 native runtime, обе темы,
320dp/large font/TalkBack, deny-location/manual-city/offline/provider recovery,
background/recreate и полная новая Android регрессия NOT RUN: нет KVM/устройства.
Подготовка fixtures и адресный запуск — [places/OPERATIONS](places/OPERATIONS.md).
Нельзя принимать статичный mock карты/внешнюю ссылку/только compile как выполнение A16.

# ADR-MOTION — KraPiva liquid на Android

Принято для P3, общий визуальный контракт применим к iOS P12.
Сравнение: видео/Lottie не реагируют сами по себе на gravity и усложняют
asset lifecycle; physics engine избыточен для небольшого hero. Выбран Compose
Canvas с clipPath по бокалу, отдельно движущейся поверхностью пива и пеной.
Статичный бренд системного splash остаётся VectorDrawable.

Датчик gravity предпочтителен; accelerometer с low-pass — запасной. Gyro,
специальные permissions, запись и сеть не нужны. Ось преобразуется по display
rotation, угол ограничен ±8°, low-pass на входе и затухание 0.12 на кадр.
Около 30 Гц, state читается в Canvas draw, форма не перерисовывается на каждый
sensor event. При register failure/no sensor — loop ±2°, при disabled animator
scale — статично. ContentObserver учитывает изменение reduced motion во время
работы. Lifecycle RESUMED + видимость composable ограничивают подписку;
DisposableEffect освобождает sensor listener и observer.

Никаких forced delay: восстановление сессии через настоящий HTTP, сразу ready
или явный retry. Welcome/register hero 88dp в общей прокручиваемой форме,
safeDrawing/IME padding; TalkBack получает одну подпись, не значения gravity.
На обычном профиле placeholder статичен. Все датчики остаются локальными.

Проверки: unit test clamp/rotation/non-finite/filter settling; instrumented
left/right/static states; реальные события эмулятора и физический телефон
отмечаются отдельно по фактическим результатам. Unit test не доказывает
аппаратное поведение. iOS будет использовать Core Motion и Reduce Motion,
а не Android imports; формат backend не зависит от сенсоров.

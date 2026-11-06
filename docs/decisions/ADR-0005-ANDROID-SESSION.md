# ADR-0005 — Android session и account cache

P3: нативный клиент P1 сохранён. Общий versioned JSON API P2/P3 не содержит
Android-specific полей; iOS P10 выберет HTTP/Keychain/SQLite adapters.

HttpURLConnection вместо добавления сетевого framework: достаточно для
небольшого P3 API, redirects отключены, 10s connect/15s read timeout,
работа на Dispatchers.IO. UI/state/ViewModel отделены от API/store.
Для DELETE без прикладного тела клиент отправляет `{}` с JSON Content-Type:
Android transport иначе добавляет `application/x-www-form-urlencoded`, который
JSON-only сервер корректно отклоняет. Это касается session revoke и media delete;
стандартный DELETE без тела на сервере также поддерживается, iOS не ограничен.
TLS обязателен кроме разрешённого debug loopback/emulator. Production адрес
не выдумывается. Ошибки не превращаются в успешные локальные записи.

Сессия — AES-GCM с ключом Android Keystore, AtomicFile в noBackupFilesDir.
Обновлённая пара сохраняется до повторного запроса. ViewModel сериализует
операции формы. Password/token формы живут только в RAM; не rememberSaveable.
Ротация refresh при потерянном ответе может потребовать нового входа:
предпочтена строгая защита от replay, без повторного использования старого токена.

Кэш — platform SQLiteOpenHelper (для двух маленьких таблиц Room не добавляет
полезного поведения); userId key и AES-GCM payload, отдельные profile/photos.
Читается только для текущей защищённой сессии. Выход/смена аккаунта очищают
таблицы и session file, secure_delete/checkpoint. Backup и device transfer
уже полностью исключены P1. Это не очередь публикаций и не черновики P5.

Форма изменяет локальные поля/URI; отмена не отправляет PATCH или upload.
Save загружает реальные media и сохраняет profile version; при конфликте
пользователь сохраняет видимый черновик до явного refresh/cancel. Фото вне
JSON/SQL payload сервера, на клиенте кэшируются только thumbnails. Отказ
выбора/доступа показывает явный текст. Offline logout предлагает отдельную
локальную очистку и прямо сообщает о сроке серверной сессии.

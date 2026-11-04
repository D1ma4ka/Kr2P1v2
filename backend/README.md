# Backend — P1

Только настоящий HTTP lifecycle и liveness. Нет БД, аккаунтов, каталога и
социальных endpoints; `/health/live` не подтверждает их готовность.

Node 24.22.1, npm 11.9.0. Из этой папки:

```sh
npm ci
npm run check
npm start
```

По умолчанию: `http://127.0.0.1:3000/health/live` → `{"status":"ok"}`.
`HOST`, `PORT`, `NODE_ENV`, `LOG_LEVEL` передаются окружением; пример в корне.
`.env` автоматически не загружается. Для Node можно использовать
`node --env-file=../.env dist/src/main.js` после создания собственного файла.

Сервер принимает SIGINT/SIGTERM и закрывает HTTP; таймаут завершения 10 секунд.
Запросы не логируют query/body/credentials. Неизвестные маршруты — 404,
превышение body limit (16 KiB) — 413. Полноценный OpenAPI, readiness с БД,
миграции и контракты данных относятся к P2.

`src/config.ts` — конфигурация; `app.ts` — фабрика HTTP; `main.ts` — процесс.
`test/` использует настоящие loopback TCP соединения, не внешние сервисы.
`dist/` не коммитится.

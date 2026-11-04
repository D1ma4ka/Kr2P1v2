import { isIP } from "node:net";
const levels = ["fatal", "error", "warn", "info", "debug", "trace", "silent"] as const;
export type LogLevel = (typeof levels)[number];
export interface Config {
  host: string; port: number;
  environment: "development" | "test" | "production";
  logLevel: LogLevel;
}
export function readConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const host = env.HOST ?? "127.0.0.1";
  if (host !== "localhost" && !isIP(host)) throw new Error("HOST must be an IP address or localhost");
  const portText = env.PORT ?? "3000";
  if (!/^\d+$/.test(portText)) throw new Error("PORT must be an integer between 1 and 65535");
  const port = Number(portText);
  if (port < 1 || port > 65535) throw new Error("PORT must be between 1 and 65535");
  const environment = env.NODE_ENV ?? "development";
  if (environment !== "development" && environment !== "test" && environment !== "production") {
    throw new Error("NODE_ENV must be development, test or production");
  }
  const logLevel = env.LOG_LEVEL ?? "info";
  if (!levels.includes(logLevel as LogLevel)) throw new Error("LOG_LEVEL is invalid");
  return { host, port, environment, logLevel: logLevel as LogLevel };
}

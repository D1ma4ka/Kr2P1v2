-- Runs only on initial local volume. Password supplied through container environment.
\getenv runtime_password DB_APP_PASSWORD
CREATE ROLE krapiva_app LOGIN PASSWORD :'runtime_password';
GRANT CONNECT ON DATABASE krapiva TO krapiva_app;
GRANT USAGE ON SCHEMA public TO krapiva_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO krapiva_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO krapiva_app;

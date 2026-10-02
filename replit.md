# SmileOS / Open Dental API

This repository contains a Java Spring Boot API at the root and the SmileOS React dashboard in `SmileOS-AI-Dashboard`. Preserve both existing stacks and their layout.

## Running on Replit

- Install dashboard dependencies: `cd SmileOS-AI-Dashboard && pnpm install`.
- Dashboard workflow: `SmileOS-AI-Dashboard/artifacts/smileos-dashboard: web`.
- The artifact-managed workflow supplies `PORT` and `BASE_PATH` automatically; start/restart the existing workflow rather than overriding it.
- Manual dashboard command outside that workflow: `cd SmileOS-AI-Dashboard && PORT=5000 BASE_PATH=/ pnpm --filter @workspace/smileos-dashboard run dev`.
- Java backend command: `PORT=8080 mvn spring-boot:run`.
- The dashboard binds to `0.0.0.0` on the artifact-assigned port; Vite forwards `/api` requests to the Java backend on port 8080. Browser requests use relative URLs by default.
- The separate Express API artifact only supplies a health endpoint and is not the dashboard's patient/appointment backend. It does not replace the Java API.
- The mockup sandbox is for design previews, not needed to run the application.

## Required backend configuration

Provide `SUPABASE_DB_URL` (JDBC PostgreSQL URL), `SUPABASE_DB_USERNAME`, and `SUPABASE_DB_PASSWORD` through Replit Secrets before starting the Java backend. Use the existing Supabase database and schema; do not create or migrate a replacement database.

The imported application configuration contains committed database credentials. Do not use those defaults; rotate the exposed credentials and remove them from source/history in a separate security cleanup.

Set `OPENDENTAL_BASE_URL` to the existing Open Dental service when live synchronization is needed. Without that service, API operations may fall back to Supabase data, but upstream synchronization will not work.

## Verification

- Java build/tests: `mvn test`.
- Dashboard typecheck: `cd SmileOS-AI-Dashboard && pnpm --filter @workspace/smileos-dashboard run typecheck`.
- Dashboard build: `cd SmileOS-AI-Dashboard && PORT=5000 BASE_PATH=/ pnpm --filter @workspace/smileos-dashboard run build`.
- The imported dashboard includes demo metrics and local data. A visible dashboard is not proof that the database or live Open Dental connection works.
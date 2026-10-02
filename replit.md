# SmileOS / Open Dental API

This repository contains a Java Spring Boot API at the root and the SmileOS React dashboard in `SmileOS-AI-Dashboard`. Preserve both existing stacks and their layout.

## Running on Replit

- Install dashboard dependencies: `cd SmileOS-AI-Dashboard && pnpm install`.
- Dashboard workflow: `SmileOS-AI-Dashboard/artifacts/smileos-dashboard: web`.
- The artifact-managed workflow supplies `PORT` and `BASE_PATH` automatically; start/restart the existing workflow rather than overriding it.
- Manual dashboard command outside that workflow: `cd SmileOS-AI-Dashboard && PORT=5000 BASE_PATH=/ pnpm --filter @workspace/smileos-dashboard run dev`.
- Java backend workflow: `Open Dental Java API`.
- Java backend command: `PORT=8080 RECONCILIATION_ENABLED=false mvn spring-boot:run`.
- Scheduled synchronization is deliberately disabled during import setup to avoid automatic writes to the existing database. Enable it only after explicitly configuring and approving live Open Dental synchronization.
- The dashboard binds to `0.0.0.0` on the artifact-assigned port; Vite forwards `/api` requests to the Java backend on port 8080. Browser requests use relative URLs by default.
- The separate Express API artifact only supplies a health endpoint and is not the dashboard's patient/appointment backend. It does not replace the Java API.
- The mockup sandbox is for design previews, not needed to run the application.

## Required backend configuration

Provide `SUPABASE_DB_URL` (JDBC PostgreSQL URL), `SUPABASE_DB_USERNAME`, and `SUPABASE_DB_PASSWORD` through Replit Secrets before starting the Java backend. Use the existing Supabase database and schema; do not create or migrate a replacement database.

The imported application configuration contains committed database credentials. Do not use those defaults; rotate the exposed credentials and remove them from source/history in a separate security cleanup.

Set `OPENDENTAL_BASE_URL` to the existing Open Dental service when live synchronization is needed. Existing patient reads may fall back to stored Supabase data, but this does not prove live synchronization. The direct Patients & Families resource adapters return explicit connectivity errors when the upstream service is unavailable.

## Clinical resource behavior

- Patients & Families, Scheduling, Clinical Care, and Insurance & Billing additions are UI-only; the user will create APIs later. Preserve the existing Allergies, Allergy Defs, and Disease Defs screens. Keep the other screens available without backend dependencies. In the API sidebar, retain groups through Insurance & Billing and remove every group after it.
- Clearly labeled UI-only previews may use fictional in-memory demo records. They are not clinical data and must not be sent to Open Dental.
- Supported actions follow each resource's official Open Dental documentation, not universal CRUD. Read-only resources must not offer write actions.
- Never synthesize clinical records or report a local save as success after an API write fails. Keep failed forms open with an explicit error; only a successful backend response permits a success message.
- The display-only dashboard demo is separate from clinical API screens. Its sample metrics are not evidence of live clinical data.

## Verification

- Java build/tests: `mvn test`.
- Dashboard typecheck: `cd SmileOS-AI-Dashboard && node_modules/.bin/tsc -p artifacts/smileos-dashboard/tsconfig.json --noEmit`.
- Resource UI contract tests: from `SmileOS-AI-Dashboard/artifacts/smileos-dashboard`, run `../../node_modules/.bin/tsc src/lib/resourceMeta.test.ts --outDir /tmp/smileos-resource-tests --module commonjs --moduleResolution node --target ES2022 --esModuleInterop --skipLibCheck --types node && node --test /tmp/smileos-resource-tests/lib/resourceMeta.test.js`. These use Node's built-in test runner, not Vitest.
- Dashboard build: `cd SmileOS-AI-Dashboard && PORT=5000 BASE_PATH=/ pnpm --filter @workspace/smileos-dashboard run build`.
- The imported dashboard includes demo metrics and local data. A visible dashboard is not proof that the database or live Open Dental connection works.
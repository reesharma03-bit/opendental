# SmileOS database design and Open Dental sync strategy

Status: proposed. Schema: [`supabase-sync-v2.sql`](../supabase-sync-v2.sql) (additive, safe to run on the live database).

## 1. Goals and constraints

| | |
|---|---|
| **Access** | Open Dental REST API only. No direct access to the practice's MySQL database. |
| **Scale** | Millions of rows per practice (claim procs, pay splits, appointment history), many practices in one database. |
| **Source of truth** | Open Dental. Our database is a fast, local-first copy: the dashboard reads only from it, writes go to it first and are then pushed to Open Dental. |
| **Freshness** | Seconds for schedule and patients, minutes for billing, a day for setup lists. |
| **Compliance** | Patient health information (HIPAA): least access, audit trail, retention, encryption. |

The design has to cope with what an API cannot tell us: it does not report deletes, not every endpoint can filter by "changed since", webhooks cover only some tables and can be missed, and calls are rate-limited.

## 2. Architecture

```
                         Open Dental (practice)
          ┌──────────────────┬──────────────────┬───────────────────┐
          │ webhooks (push)  │ GET ?DateTStamp= │ GET all pages     │  ▲ POST/PUT/DELETE
          ▼                  ▼                  ▼                   │
   ┌─────────────┐   ┌──────────────┐   ┌──────────────┐    ┌──────────────┐
   │webhook_events│  │ incremental  │   │  full pass   │    │ od_sync_queue│
   │ (store first)│  │ every 1–60m  │   │ nightly/Force│    │   (outbox)   │
   └──────┬──────┘   └──────┬───────┘   └──────┬───────┘    └──────▲───────┘
          └─────────────────┼──────────────────┘                   │
                            ▼                                      │
                    od_sync_staging  (unlogged, per batch)         │
                            │  one MERGE per batch:                │
                            │  insert new · update changed hash ·  │
                            │  tombstone unseen (full pass only)   │
             ┌──────────────┴───────────────┐                      │
             ▼                              ▼                      │
   Typed tier (real columns)       Raw tier (JSON)                 │
   patients, appointments,         od_resource_records             │
   procedure_logs, claims,         (~45 other resources)           │
   payments, allergies, ...                                        │
             └──────────────┬───────────────┘                      │
                            ▼                                      │
                  Backend API  ──── dashboard writes ──────────────┘
                            ▼
                     SmileOS dashboard
```

Control: `sync_cursors` (one row per clinic and resource) holds the watermark, schedule, lease and health of every resource; `sync_runs` records each run.

## 3. Tables

### 3.1 Tenancy and access

| Table | Key | Purpose | Notes |
|---|---|---|---|
| `clinics` | `id` | One row per practice (tenant): Open Dental URL and key, webhook secret. | Move `api_key` and `webhook_secret` into Supabase Vault (encrypted) before production. |
| `od_clinics` | `(clinic_id, clinic_num)` | Open Dental's own clinic list inside a practice. | |
| `app_users` | `id` | Dashboard users, role, BCrypt hash, lockout. | |
| `audit_log` | `id` | Sign-ins, user changes, data changes. | Keep 6 years. Partition by month once it passes ~10M rows. |

### 3.2 Typed tier: real columns for data the app reports on

Rule: a resource gets a typed table when the dashboard filters, joins or totals it. Every typed table has the same sync columns as the raw tier (`raw` / `data_hash` / `od_tstamp` / `synced_at` / `seen_at` / `deleted_at`), so one merge routine serves both.

| Table | Open Dental resource | Key | Expected size | Status |
|---|---|---|---|---|
| `patients` | patients | `pat_num` | 10k–200k | exists |
| `appointments` | appointments | `apt_num` | 100k–2M | exists |
| `procedure_logs` | procedurelogs | `proc_num` | 500k–10M | exists |
| `documents` | documents | `doc_num` | 50k–1M | exists (metadata only, files stay in Open Dental) |
| `providers`, `operatories`, `schedules`, `pat_fields`, `tooth_initials` | same | `*_num` | small | exist |
| `carriers` | carriers | `carrier_num` | < 5k | **new** |
| `ins_plans` | insplans | `plan_num` | < 50k | **new** |
| `ins_subs` | inssubs | `ins_sub_num` | 10k–200k | **new** |
| `pat_plans` | patplans | `pat_plan_num` | 10k–200k | **new** |
| `claims` | claims | `claim_num` | 100k–2M | **new** |
| `claim_procs` | claimprocs | `claim_proc_num` | 1M–20M (largest) | **new** |
| `payments` | payments | `pay_num` | 100k–2M | **new** |
| `pay_splits` | paysplits | `split_num` | 200k–5M | **new** |
| `allergy_defs` | allergydefs | `allergy_def_num` | small | **new** |
| `allergies` | allergies | `allergy_num` | 10k–200k | **new** |
| `recalls` | recalls | `recall_num` | 10k–400k | **new** |

All keys are `(clinic_id, <num>)`. Amounts are `numeric(12,2)`, dates are `date`.

**No foreign keys between synced tables.** Pages and webhooks arrive in any order. A claim must not be rejected because its patient lands a second later. Joins are covered by indexes, and integrity is checked by the nightly count check (section 4.7). Existing FKs on `appointments`/`procedure_logs` → `patients` can stay because patients always sync first, but new tables don't add any.

### 3.3 Raw tier: everything else

| Table | Key | Purpose |
|---|---|---|
| `od_resource_records` | `(clinic_id, resource, record_key)` | One row per Open Dental record, the JSON exactly as returned, for the ~45 resources without a typed table (diseases, medications, perio, treatment plans, fees, statements, …). |

Columns: `pat_num` (extracted for patient lookups), `data jsonb`, `data_hash`, `od_tstamp`, `synced_at`, `seen_at`, `deleted_at`.

Indexes:
- live rows by resource in key order (keyset paging);
- live rows by patient;
- GIN on `data` for field searches.

Adding a new Open Dental resource is one line in `OdResourceCatalog`, with no new table. When a raw resource becomes something we report on, it is promoted to a typed table (the merge routine is the same).

**Partitioning:** not needed at first. Once the table passes ~50M rows, or one resource passes ~20M, convert it to `PARTITION BY LIST (resource)`, giving the large resources their own partitions and the rest a default partition. The primary key already contains `resource`, so the conversion is a copy-and-rename migration, with no code change.

### 3.4 Sync control

| Table | Key | Purpose |
|---|---|---|
| `sync_cursors` | `(clinic_id, resource)` | Watermark (highest `DateTStamp` merged), tier, `next_due_at`, lease (`locked_by`, `locked_until`), failure count and last error, and the latest count check (`od_count`, `local_count`). The sync health screen reads this table. |
| `sync_runs` | `id` | One row per run per resource: mode, rows fetched / inserted / updated / unchanged / deleted, API calls, duration, error. Kept 180 days. |
| `od_sync_staging` | `(batch_id, resource, record_key)` | Unlogged landing area for fetched pages before the merge. Disposable. |

### 3.5 Inbound and outbound queues

| Table | Purpose |
|---|---|
| `webhook_events` | Every Open Dental webhook is stored first, then applied by a worker. `dedupe_key` prevents double-apply on redelivery; `attempts`/`next_attempt_at` drive retries. Processed events are kept 30 days. |
| `od_sync_queue` | Outbox of dashboard changes waiting to go to Open Dental. `idempotency_key` makes a double click or retried request produce one change. Finished rows are kept 30 days. |

## 4. Sync strategy

### 4.1 Four layers, each catching what the previous misses

| Layer | Trigger | Fetches | Catches | Deletes? |
|---|---|---|---|---|
| **1. Webhook** | Open Dental pushes a change | The changed record (re-read by key) | Most creates and updates within seconds | Only if the event says so |
| **2. Incremental** | `sync_cursors.next_due_at` | `GET /{resource}?DateTStamp={watermark − 5 min}` | Missed webhooks, and tables without webhooks | No |
| **3. Full pass** | Nightly per resource, and Force Sync | Every page | Deletes, and drift on resources without `DateTStamp` | **Yes** (tombstones) |
| **4. Count check** | Nightly, after the full pass | Open Dental count vs our live count | Anything still wrong, which gets alerted | — |

### 4.2 Freshness tiers

| Tier | Resources | Incremental | Full pass |
|---|---|---|---|
| **hot** | appointments, patients, schedules, operatories, providers | every 1–2 min (plus webhooks) | nightly |
| **warm** | procedurelogs, claims, claimprocs, payments, paysplits, allergies, medications, diseases, recalls, patplans, inssubs, treatplans, commlogs | every 15 min | nightly |
| **cold** | definitions and setup: allergydefs, diseasedefs, procedurecodes, fees, feescheds, carriers, insplans, recalltypes, appointmenttypes, clinics | — | nightly |
| **history** | histappointments, clockevents, periomeasures, procnotes | every 60 min | weekly |

Resources whose endpoint does not accept `DateTStamp` (`supports_tstamp = false`) have no incremental pull. They get a full pass at their tier's interval if small, or nightly if large. Set `supports_tstamp` per resource after checking each endpoint in Open Dental's API docs.

### 4.3 The merge (identical for every layer)

1. Fetch a page (100–1,000 rows) and compute `md5(data::text)` per row.
2. Bulk insert the page into `od_sync_staging` under a new `batch_id` (one batched JDBC call, with `reWriteBatchedInserts=true`).
3. One transaction:

```sql
-- new and changed rows; unchanged rows are not written at all
insert into od_resource_records as t (clinic_id, resource, record_key, pat_num, data, data_hash, od_tstamp, synced_at, seen_at, deleted_at)
select s.clinic_id, s.resource, s.record_key, s.pat_num, s.data, s.data_hash, s.od_tstamp, now(),
       case when :full_pass then now() end, null
from od_sync_staging s
where s.batch_id = :batch
  and not exists (                                 -- never overwrite a change still waiting to go to Open Dental
      select 1 from od_sync_queue q
      where q.clinic_id = s.clinic_id and q.entity_type = 'resource:' || s.resource
        and q.local_id::text = s.record_key and q.status in ('PENDING','IN_PROGRESS','FAILED'))
on conflict (clinic_id, resource, record_key) do update
  set data = excluded.data, data_hash = excluded.data_hash, pat_num = excluded.pat_num,
      od_tstamp = excluded.od_tstamp, synced_at = now(), deleted_at = null,
      seen_at = coalesce(excluded.seen_at, t.seen_at)
  where t.data_hash is distinct from excluded.data_hash
     or t.deleted_at is not null;

-- full pass only: mark unchanged rows as still present (narrow update, no JSON rewritten)
update od_resource_records t set seen_at = now()
from od_sync_staging s
where :full_pass and s.batch_id = :batch
  and t.clinic_id = s.clinic_id and t.resource = s.resource and t.record_key = s.record_key
  and t.data_hash = s.data_hash;

delete from od_sync_staging where batch_id = :batch;
update sync_cursors set watermark = greatest(watermark, :max_tstamp_in_page) where ...;
```

> `seen_at` is not indexed, so the nightly "still present" update is done in place (a HOT update) and does not touch the JSON or its indexes. Incremental and webhook merges write only rows that changed.

> **As built (phase 1):** instead of touching `seen_at` on every unchanged row, each merged page's *keys* are copied (without their JSON) into staging under the run's batch id. The full pass then removes records whose key is not in that batch: `NOT EXISTS (select 1 from od_sync_staging k where k.batch_id = :run …)`. Unchanged rows are never written at all. Phase 1 hard-deletes missing rows (as before), so readers need no `deleted_at` filter yet. Tombstones (below) come with phase 4.

4. **After a complete, error-free full pass** of a resource:

```sql
update od_resource_records set deleted_at = now()
where clinic_id = :c and resource = :r and deleted_at is null and seen_at < :pass_started_at;
```

A pass that failed part-way never tombstones anything. Tombstoned rows are hidden from the dashboard and purged after 30 days.

Typed tables use the same steps. The insert maps JSON fields to columns (`(s.data->>'ClaimFee')::numeric`, …) and keeps the whole record in `raw`.

### 4.4 Webhooks (Open Dental API events)

Facts from Open Dental's docs ([subscriptions](https://www.opendental.com/site/apisubscriptions.html), [events](https://www.opendental.com/site/apievents.html)):

- **Who sends them:** `OpenDental.exe` or `OpenDentalAPIService.exe` running on a computer at the practice. It polls the practice database every `PollingSeconds` for rows changed since the subscription's `DateTimeStart` and POSTs them. That program must be running for events to flow. From version 26.2.1 the subscription's `Workstation` field is required: the machine name, or `All Workstations`.
- **Watchable tables (only these):**
  - Appointment, Operatory, Patient, PatField, Provider, Schedule, ToothInitial, MedicationPat, LabCase;
  - deleted-record events for some of them: AppointmentDeleted, PatFieldDeleted, ScheduleDeleted, ToothInitialDeleted, MedicationPatDeleted, LabCaseDeleted.

  **No billing, insurance, claims, payments, procedures, allergies or recalls.** Those depend on the incremental pull and the full pass.
- **Format:**
  - `POST` to the subscription's `EndPointUrl`;
  - headers `Authorization` (the customer's API key), `Workstation` and `Event-Type`;
  - the body is a **JSON array of full rows**, up to 1,000 per POST, sent in several batches when more rows changed.
- **Missed deliveries:** if a POST fails, `DateTimeStart` is not advanced. The next successful delivery includes all changes from **up to the last 3 days**. An outage longer than 3 days loses events, and the full pass covers that.
- **Authentication:** the only credential is the `Authorization` header (the practice's customer key). There is no signature.

Our handling:

1. **Endpoint:** one URL per watch table, `/api/webhooks/opendental/{table}`. Each route:
   - **rejects the request unless `Authorization` matches a clinic's stored customer key**, which also identifies the clinic;
   - saves one `webhook_events` row per POST (`dedupe_key` = hash of the body);
   - returns 200 at once, without processing on the request thread.
2. **Worker:** claims due events (`FOR UPDATE SKIP LOCKED`) and merges the rows from the body directly via 4.3, because they are full rows and no re-read is needed. `…Deleted` events tombstone the records.
3. **Failures:** retry with backoff (`next_attempt_at`). After 10 attempts the event is marked failed, and the incremental pull or full pass still catches the change.
4. **Subscriptions:** one per watchable table, `PollingSeconds` 30–60. Monitor `SubsequentFailures` and `DateTimeLastFailure` from `GET /subscriptions` on the sync health screen. If failures continue for more than 2 days, warn, because Open Dental only replays 3 days.

### 4.5 Dashboard writes (outbound)

Already built, and it stays:
1. Save in our table first. A new record gets a temporary negative key.
2. Add a row to `od_sync_queue` with an `idempotency_key`.
3. A worker pushes it to Open Dental with retry and backoff. On create, it swaps the temporary key for the real one (`moveToRealId`).

Conflict rule:
- **While a local change is pending, sync never overwrites that record** (the `not exists` in 4.3).
- Once the change is pushed, the next sync brings back Open Dental's version, which wins. Open Dental is the source of truth and may have applied its own rules, for example fee calculation.

### 4.6 Scheduling, leases and rate limits

- **One scheduler loop:** every 30 s it picks due rows from `sync_cursors` with `FOR UPDATE SKIP LOCKED`, takes a lease (`locked_by`, `locked_until = now() + 10 min`), runs, then sets `next_due_at`. If an instance crashes, its lease expires and another instance takes over. Several backend instances can run safely.
- **Per clinic:** at most 2 concurrent Open Dental calls; on HTTP 429, back off exponentially (1 s → 60 s). Hot resources are served before warm, and warm before cold.
- **Failures:** `consecutive_failures` raises `next_due_at` with backoff. Three or more failures, or data older than twice its interval, shows as stale on the sync health screen.

### 4.7 Count check (nightly)

For each resource, compare Open Dental's row count (from the full pass) with our live count (`deleted_at is null`), and store both in `sync_cursors`. If the difference is over 0.5%, flag the resource and schedule another full pass. This catches silent drift that every other layer missed.

### 4.8 First load (new practice)

1. Insert the `clinics` row.
2. Seed `sync_cursors` from `OdResourceCatalog`.
3. Run full passes in dependency order: setup lists → providers/operatories → patients → appointments, procedures → insurance → claims, claim procs → payments, splits → everything else.
4. Then switch the practice to incremental pulls plus webhooks.

At 1,000 rows per page and 2 concurrent calls, 10M rows is about 10k calls, roughly 1–3 hours, run once.

## 5. Reading at scale

- **Paging:** keyset (`where record_key > :last order by record_key limit :n`), never `OFFSET`.
- **Tenant first:** every query and index starts with `clinic_id`. The script adds `(clinic_id, …)` indexes to the existing tables, whose old single-column indexes scan every clinic.
- **Reports:** typed tables plus indexes on date and status. Heavy dashboards, such as monthly production by provider, can use materialized views refreshed after each warm sync.
- **JSON search:** `data @> '{"Field":"value"}'` uses the GIN index.

## 6. Operations

| Area | Setting |
|---|---|
| **Supabase plan** | Pro with Small compute to start; Medium or larger beyond ~20M rows or 10+ practices. Point-in-time recovery on. |
| **Connections** | The backend uses the direct or session-mode connection (port 5432). Transaction-mode pooling breaks batched prepared statements. Pool of 10–20. |
| **JDBC** | `reWriteBatchedInserts=true`, batch size 1,000. |
| **Vacuum** | Because unchanged rows are no longer rewritten, default autovacuum is enough. On `claim_procs` and `od_resource_records`, set `autovacuum_vacuum_scale_factor = 0.02`. |
| **Retention** | Tombstones 30 days, finished outbox and webhook rows 30 days, `sync_runs` 180 days, `audit_log` 6 years. `sync_housekeeping()` runs nightly with pg_cron. |
| **Security** | RLS on every table with no public policies (backend only). Open Dental keys go in Vault. Sign a BAA with Supabase (Team plan + HIPAA add-on). Rotate the database password, which is currently in the public repo. |
| **Monitoring** | Sync health screen from `sync_cursors`; alert on stale resources, failed outbox rows, or a count mismatch. |

## 7. Rollout

| Phase | Work | Visible change |
|---|---|---|
| **1** | Run `supabase-sync-v2.sql`. Add staging, the hash merge, keyset paging and batched inserts to the raw tier. | Syncs write only what changed |
| **2** | Add `sync_cursors` scheduling and leases, plus incremental `DateTStamp` pulls with tiers. | Fresher data with far fewer API calls |
| **3** | Add webhooks as the primary source: shared secret, store-first worker, re-read by key. | Changes appear in seconds; the webhook endpoint is no longer open |
| **4** | Use the typed billing and clinical tables: move claims, claim procs, payments, splits, insurance, allergies and recalls out of the raw tier, and point the dashboard and AI Assistant at them. | Fast, exact billing figures |
| **5** | Add the sync health screen, the nightly count check and housekeeping with pg_cron. | Problems are visible before users notice |
| later | Partition `od_resource_records` and `audit_log` when they reach the thresholds above. | — |

## 8. Assumptions to confirm

- **`DateTStamp` support:** which resource endpoints accept the filter. Check each against Open Dental's API docs and set `supports_tstamp`.
- **Webhooks:** ~~which tables can be watched~~ confirmed in 4.4. Still to confirm: that `OpenDentalAPIService` is installed and always running at each practice, and the practice's Open Dental version (26.2.1+ requires `Workstation`).
- **Page limits:** Open Dental's page-size limit and rate limit for the practice's API plan.
- **Typed column names:** follow Open Dental's documented field names. Check them against a real response when each typed table is wired up; the full record is in `raw` either way.

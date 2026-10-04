-- ============================================================================
-- supabase-sync-v2.sql
-- ============================================================================
-- Storage and sync schema for millions of Open Dental records (API-only access).
-- Design: docs/database-design.md
--
-- ADDITIVE ONLY: creates new tables, adds columns and indexes. Nothing existing is
-- dropped or rewritten, so it is safe to run on the live database. Safe to run more
-- than once.
--
-- Sections
--   1. Raw tier      od_resource_records: change hash, Open Dental timestamp, tombstone
--   2. Staging       od_sync_staging: where each fetched batch lands before merging
--   3. Sync control  sync_cursors (per-resource watermarks and health), sync_runs columns
--   4. Inbound       webhook_events: de-duplication and retry columns
--   5. Outbound      od_sync_queue: idempotency key and housekeeping index
--   6. Typed tier    billing and clinical tables used by reports
--   7. Tenant-first  indexes that lead with clinic_id
--   8. Housekeeping  retention function for logs and finished work
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. Raw tier: od_resource_records
-- ----------------------------------------------------------------------------
-- data_hash   md5 of the record's JSON. A sync only rewrites a row when the hash
--             changes, so unchanged data costs nothing to re-sync.
-- od_tstamp   Open Dental's DateTStamp (last change in Open Dental), when present.
-- seen_at     last time a FULL pass saw the record; rows not seen by a complete
--             pass were deleted in Open Dental.
-- deleted_at  tombstone: the record was deleted in Open Dental. Kept for audit for
--             a retention period instead of disappearing immediately.

alter table public.od_resource_records add column if not exists data_hash text;
alter table public.od_resource_records add column if not exists od_tstamp timestamp without time zone;
alter table public.od_resource_records add column if not exists seen_at timestamp with time zone;
alter table public.od_resource_records add column if not exists deleted_at timestamp with time zone;

-- Live rows of one resource, in key order (keyset paging).
create index if not exists od_resource_records_live_idx
  on public.od_resource_records (clinic_id, resource, record_key)
  where deleted_at is null;

-- Live rows of one patient across resources.
create index if not exists od_resource_records_patient_live_idx
  on public.od_resource_records (clinic_id, pat_num, resource)
  where deleted_at is null and pat_num is not null;

-- Field searches inside the JSON (e.g. data @> '{"ClaimStatus":"S"}').
create index if not exists od_resource_records_data_gin_idx
  on public.od_resource_records using gin (data jsonb_path_ops);


-- ----------------------------------------------------------------------------
-- 2. Staging: od_sync_staging
-- ----------------------------------------------------------------------------
-- Each page fetched from Open Dental is bulk-inserted here under a batch id, then
-- merged into the target table with one set-based statement (insert new, update
-- changed, tombstone missing) and the batch is deleted. UNLOGGED: no write-ahead
-- log, so staging is fast and does not bloat backups; its contents are disposable.

create unlogged table if not exists public.od_sync_staging (
  batch_id uuid not null,
  clinic_id uuid not null,
  resource text not null,
  record_key text not null,
  pat_num bigint,
  od_tstamp timestamp without time zone,
  data jsonb not null,
  data_hash text not null,
  staged_at timestamp with time zone not null default now(),
  primary key (batch_id, resource, record_key)
);

alter table public.od_sync_staging enable row level security;

comment on table public.od_sync_staging is
  'Landing area for fetched Open Dental pages before they are merged. Disposable.';


-- ----------------------------------------------------------------------------
-- 3. Sync control
-- ----------------------------------------------------------------------------
-- sync_cursors: one row per clinic and resource. The single source of truth for
-- "how fresh is this data" and the watermark the incremental pull resumes from.

create table if not exists public.sync_cursors (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  resource text not null,                          -- e.g. 'claims', 'patients'
  tier text not null default 'warm'
    check (tier in ('hot', 'warm', 'cold')),       -- how often it is pulled
  supports_tstamp boolean not null default false,  -- Open Dental accepts ?DateTStamp=
  watermark timestamp without time zone,           -- highest DateTStamp merged so far (Open Dental clock)
  last_incremental_at timestamp with time zone,    -- last successful incremental pull
  last_full_at timestamp with time zone,           -- last complete, error-free full pass
  next_due_at timestamp with time zone not null default now(),
  status text not null default 'idle'
    check (status in ('idle', 'running', 'failed')),
  locked_by text,                                  -- instance holding the lease while running
  locked_until timestamp with time zone,           -- lease expiry, so a crashed worker frees it
  consecutive_failures integer not null default 0,
  last_error text,
  od_count bigint,                                 -- rows Open Dental reported at the last count check
  local_count bigint,                              -- live rows we held at the same time
  counted_at timestamp with time zone,
  updated_at timestamp with time zone not null default now(),
  primary key (clinic_id, resource)
);

create index if not exists sync_cursors_due_idx on public.sync_cursors (next_due_at) where status <> 'running';

alter table public.sync_cursors enable row level security;

comment on table public.sync_cursors is
  'Per clinic and resource: incremental watermark, schedule, lease and health of the Open Dental sync.';

-- sync_runs: record what each run did, per resource, for the sync health screen.
alter table public.sync_runs add column if not exists resource text;
alter table public.sync_runs add column if not exists mode text;            -- webhook | incremental | full | force
alter table public.sync_runs add column if not exists rows_fetched integer default 0;
alter table public.sync_runs add column if not exists rows_inserted integer default 0;
alter table public.sync_runs add column if not exists rows_updated integer default 0;
alter table public.sync_runs add column if not exists rows_unchanged integer default 0;
alter table public.sync_runs add column if not exists rows_deleted integer default 0;
alter table public.sync_runs add column if not exists api_calls integer default 0;

create index if not exists sync_runs_clinic_resource_idx on public.sync_runs (clinic_id, resource, started_at desc);


-- ----------------------------------------------------------------------------
-- 4. Inbound webhooks
-- ----------------------------------------------------------------------------
-- Every webhook is stored first, then applied by a worker. dedupe_key stops the
-- same event being applied twice when Open Dental retries a delivery.

alter table public.webhook_events add column if not exists dedupe_key text;
alter table public.webhook_events add column if not exists resource text;
alter table public.webhook_events add column if not exists record_key text;
alter table public.webhook_events add column if not exists attempts integer not null default 0;
alter table public.webhook_events add column if not exists next_attempt_at timestamp with time zone default now();

create unique index if not exists webhook_events_dedupe_idx
  on public.webhook_events (clinic_id, dedupe_key) where dedupe_key is not null;
create index if not exists webhook_events_due_idx
  on public.webhook_events (status, next_attempt_at) where status in ('received', 'failed');


-- ----------------------------------------------------------------------------
-- 5. Outbound changes (dashboard -> Open Dental)
-- ----------------------------------------------------------------------------
-- idempotency_key: one queue entry per user action, so a double click or a
-- retried request cannot send the same change to Open Dental twice.

alter table public.od_sync_queue add column if not exists idempotency_key text;

create unique index if not exists od_sync_queue_idempotency_idx
  on public.od_sync_queue (clinic_id, idempotency_key) where idempotency_key is not null;
create index if not exists od_sync_queue_finished_idx
  on public.od_sync_queue (updated_at) where status in ('DONE', 'CANCELLED');


-- ----------------------------------------------------------------------------
-- 6. Typed tier: billing and clinical tables used by reports
-- ----------------------------------------------------------------------------
-- These resources move OUT of od_resource_records into real columns (numbers,
-- dates, statuses) so totals, joins and date ranges are fast and exact. Every
-- table keeps the full Open Dental record in `raw` so nothing is lost, plus the
-- same sync columns as the raw tier.
--
-- No foreign keys between synced tables: webhooks and pages arrive in any order,
-- and a claim must not be rejected because its patient arrives a second later.
-- Integrity is checked by the nightly count check instead. Indexes cover joins.

create table if not exists public.carriers (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  carrier_num bigint not null,
  carrier_name text,
  elect_id text,
  phone text,
  raw jsonb not null, data_hash text not null, od_tstamp timestamp, synced_at timestamptz not null default now(),
  seen_at timestamptz, deleted_at timestamptz,
  primary key (clinic_id, carrier_num)
);

create table if not exists public.ins_plans (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  plan_num bigint not null,
  carrier_num bigint,
  group_name text,
  group_num text,
  plan_type text,
  fee_sched bigint,
  raw jsonb not null, data_hash text not null, od_tstamp timestamp, synced_at timestamptz not null default now(),
  seen_at timestamptz, deleted_at timestamptz,
  primary key (clinic_id, plan_num)
);
create index if not exists ins_plans_carrier_idx on public.ins_plans (clinic_id, carrier_num);

create table if not exists public.ins_subs (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  ins_sub_num bigint not null,
  plan_num bigint,
  subscriber bigint,                        -- PatNum of the subscriber
  date_effective date,
  date_term date,
  raw jsonb not null, data_hash text not null, od_tstamp timestamp, synced_at timestamptz not null default now(),
  seen_at timestamptz, deleted_at timestamptz,
  primary key (clinic_id, ins_sub_num)
);
create index if not exists ins_subs_subscriber_idx on public.ins_subs (clinic_id, subscriber);
create index if not exists ins_subs_plan_idx on public.ins_subs (clinic_id, plan_num);

create table if not exists public.pat_plans (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  pat_plan_num bigint not null,
  pat_num bigint,
  ins_sub_num bigint,
  ordinal smallint,                         -- 1 = primary, 2 = secondary
  relationship text,
  raw jsonb not null, data_hash text not null, od_tstamp timestamp, synced_at timestamptz not null default now(),
  seen_at timestamptz, deleted_at timestamptz,
  primary key (clinic_id, pat_plan_num)
);
create index if not exists pat_plans_patient_idx on public.pat_plans (clinic_id, pat_num);

create table if not exists public.claims (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  claim_num bigint not null,
  pat_num bigint,
  plan_num bigint,
  ins_sub_num bigint,
  prov_treat bigint,
  claim_type text,                          -- P primary, S secondary, PreAuth, ...
  claim_status text,                        -- U unsent, W waiting, S sent, R received, ...
  date_service date,
  date_sent date,
  date_received date,
  claim_fee numeric(12,2),
  ins_pay_est numeric(12,2),
  ins_pay_amt numeric(12,2),
  write_off numeric(12,2),
  od_clinic_num bigint,
  raw jsonb not null, data_hash text not null, od_tstamp timestamp, synced_at timestamptz not null default now(),
  seen_at timestamptz, deleted_at timestamptz,
  primary key (clinic_id, claim_num)
);
create index if not exists claims_patient_idx on public.claims (clinic_id, pat_num);
create index if not exists claims_open_idx on public.claims (clinic_id, claim_status, date_sent) where deleted_at is null;
create index if not exists claims_service_idx on public.claims (clinic_id, date_service);

create table if not exists public.claim_procs (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  claim_proc_num bigint not null,
  claim_num bigint,
  proc_num bigint,
  pat_num bigint,
  prov_num bigint,
  plan_num bigint,
  claim_payment_num bigint,
  status text,
  proc_date date,
  date_cp date,
  fee_billed numeric(12,2),
  ins_pay_est numeric(12,2),
  ins_pay_amt numeric(12,2),
  ded_applied numeric(12,2),
  write_off numeric(12,2),
  raw jsonb not null, data_hash text not null, od_tstamp timestamp, synced_at timestamptz not null default now(),
  seen_at timestamptz, deleted_at timestamptz,
  primary key (clinic_id, claim_proc_num)
);
create index if not exists claim_procs_claim_idx on public.claim_procs (clinic_id, claim_num);
create index if not exists claim_procs_patient_idx on public.claim_procs (clinic_id, pat_num);
create index if not exists claim_procs_date_cp_idx on public.claim_procs (clinic_id, date_cp);

create table if not exists public.payments (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  pay_num bigint not null,
  pat_num bigint,
  pay_type bigint,
  pay_date date,
  pay_amt numeric(12,2),
  check_num text,
  od_clinic_num bigint,
  raw jsonb not null, data_hash text not null, od_tstamp timestamp, synced_at timestamptz not null default now(),
  seen_at timestamptz, deleted_at timestamptz,
  primary key (clinic_id, pay_num)
);
create index if not exists payments_date_idx on public.payments (clinic_id, pay_date);
create index if not exists payments_patient_idx on public.payments (clinic_id, pat_num);

create table if not exists public.pay_splits (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  split_num bigint not null,
  pay_num bigint,
  pat_num bigint,
  prov_num bigint,
  proc_num bigint,
  pay_plan_num bigint,
  date_pay date,
  split_amt numeric(12,2),
  od_clinic_num bigint,
  raw jsonb not null, data_hash text not null, od_tstamp timestamp, synced_at timestamptz not null default now(),
  seen_at timestamptz, deleted_at timestamptz,
  primary key (clinic_id, split_num)
);
create index if not exists pay_splits_payment_idx on public.pay_splits (clinic_id, pay_num);
create index if not exists pay_splits_date_prov_idx on public.pay_splits (clinic_id, date_pay, prov_num);
create index if not exists pay_splits_patient_idx on public.pay_splits (clinic_id, pat_num);

create table if not exists public.allergy_defs (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  allergy_def_num bigint not null,
  description text,
  is_hidden boolean,
  raw jsonb not null, data_hash text not null, od_tstamp timestamp, synced_at timestamptz not null default now(),
  seen_at timestamptz, deleted_at timestamptz,
  primary key (clinic_id, allergy_def_num)
);

create table if not exists public.allergies (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  allergy_num bigint not null,
  pat_num bigint,
  allergy_def_num bigint,
  reaction text,
  status_is_active boolean,
  date_adverse_reaction date,
  raw jsonb not null, data_hash text not null, od_tstamp timestamp, synced_at timestamptz not null default now(),
  seen_at timestamptz, deleted_at timestamptz,
  primary key (clinic_id, allergy_num)
);
create index if not exists allergies_patient_idx on public.allergies (clinic_id, pat_num);
create index if not exists allergies_def_idx on public.allergies (clinic_id, allergy_def_num) where status_is_active;

create table if not exists public.recalls (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  recall_num bigint not null,
  pat_num bigint,
  recall_type_num bigint,
  date_due date,
  date_previous date,
  date_scheduled date,
  is_disabled boolean,
  raw jsonb not null, data_hash text not null, od_tstamp timestamp, synced_at timestamptz not null default now(),
  seen_at timestamptz, deleted_at timestamptz,
  primary key (clinic_id, recall_num)
);
create index if not exists recalls_due_idx on public.recalls (clinic_id, date_due) where not is_disabled and deleted_at is null;
create index if not exists recalls_patient_idx on public.recalls (clinic_id, pat_num);

do $$
declare t text;
begin
  foreach t in array array['carriers','ins_plans','ins_subs','pat_plans','claims','claim_procs',
                           'payments','pay_splits','allergy_defs','allergies','recalls'] loop
    execute format('alter table public.%I enable row level security', t);
  end loop;
end $$;


-- ----------------------------------------------------------------------------
-- 7. Tenant-first indexes on the existing typed tables
-- ----------------------------------------------------------------------------
-- Every query is scoped to one clinic, so indexes must lead with clinic_id. The
-- old single-column indexes (e.g. on l_name alone) scan every clinic's rows.

create index if not exists patients_clinic_name_idx on public.patients (clinic_id, lower(l_name), lower(f_name));
create index if not exists patients_clinic_birthdate_idx on public.patients (clinic_id, birthdate);
create index if not exists appointments_clinic_date_idx on public.appointments (clinic_id, apt_date_time);
create index if not exists appointments_clinic_patient_idx on public.appointments (clinic_id, pat_num);
create index if not exists procedure_logs_clinic_date_idx on public.procedure_logs (clinic_id, proc_date);
create index if not exists procedure_logs_clinic_patient_idx on public.procedure_logs (clinic_id, pat_num);
create index if not exists documents_clinic_patient_idx on public.documents (clinic_id, pat_num);


-- ----------------------------------------------------------------------------
-- 8. Housekeeping
-- ----------------------------------------------------------------------------
-- Run nightly (pg_cron on Supabase, or the backend's scheduler). Patient data
-- tombstones are kept for 30 days; logs per the retention policy in the design.
-- audit_log is deliberately NOT pruned here: HIPAA expects 6 years.

create or replace function public.sync_housekeeping() returns void
language plpgsql as $$
begin
  delete from public.od_resource_records where deleted_at < now() - interval '30 days';
  delete from public.od_sync_queue      where status in ('DONE', 'CANCELLED') and updated_at < now() - interval '30 days';
  delete from public.webhook_events     where status = 'processed' and created_at < now() - interval '30 days';
  delete from public.sync_runs          where started_at < now() - interval '180 days';
  -- staging left behind by a crashed worker (a run clears its own batches when it ends)
  delete from public.od_sync_staging where staged_at < now() - interval '1 day';
end $$;

-- To schedule on Supabase (enable the pg_cron extension first):
--   select cron.schedule('sync-housekeeping', '30 3 * * *', 'select public.sync_housekeeping()');

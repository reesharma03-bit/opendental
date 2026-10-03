-- ============================================================================
-- supabase-od-resource-records.sql
-- ============================================================================
-- Copy of every Open Dental API resource that has no dedicated table
-- (allergies, carriers, claims, recalls, insurance plans, treatment plans, ...).
-- One row per Open Dental record, stored as the JSON Open Dental returns.
--
-- Filled by the backend's resource sync (every 15 minutes), the nightly full
-- sync and the dashboard's Force Sync button. Rows Open Dental no longer
-- returns are removed after a complete, error-free fetch of that resource.
--
-- Query example:
--   select data from od_resource_records
--   where resource = 'allergies' and pat_num = 48;
--
-- Safe to run more than once. Run this on an existing database; it is also
-- included in supabase-multitenant-schema.sql for fresh installs.
-- ============================================================================

create table if not exists public.od_resource_records (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  resource text not null,        -- Open Dental API resource, e.g. 'allergies', 'claims'
  record_key text not null,      -- the record's Open Dental key (e.g. AllergyNum); a hash when it has none
  pat_num bigint,                -- patient the record belongs to, when it has one
  data jsonb not null,           -- the record exactly as Open Dental returned it
  synced_at timestamp with time zone not null default now(),
  primary key (clinic_id, resource, record_key)
);

create index if not exists od_resource_records_resource_idx on public.od_resource_records (clinic_id, resource);
create index if not exists od_resource_records_pat_num_idx on public.od_resource_records (clinic_id, pat_num);

alter table public.od_resource_records enable row level security;

comment on table public.od_resource_records is 'Copy of Open Dental API resources without a dedicated table, one JSON row per record.';

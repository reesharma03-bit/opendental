-- ============================================================================
-- supabase-incremental-sync.sql
-- ============================================================================
-- Per-clinic, per-API checkpoints for the incremental Open Dental sync.
--
-- The checkpoints live in sync_cursors (supabase-sync-v2.sql), one row per clinic
-- and API, primary key (clinic_id, resource): listed APIs under their own name
-- (e.g. 'commlogs'), tables with their own schema as 'core:<table>'
-- (e.g. 'core:appointments'). This adds what each run did, and a view with the
-- checkpoint's fields under their plain names.
--
-- ADDITIVE ONLY and safe to run more than once. Run after supabase-sync-v2.sql.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- Appointment text columns: text, as in supabase-multitenant-schema.sql.
-- ----------------------------------------------------------------------------
-- Some databases have these as varchar(255). Open Dental sends longer values (a
-- procedure description listing many teeth, the time pattern of a long
-- appointment), so those appointments failed to save, and the appointments
-- checkpoint, which only moves when every record is saved, stayed put.
-- varchar -> text keeps the data and only changes the column's type.

alter table public.appointments alter column proc_descript type text;
alter table public.appointments alter column pattern type text;
alter table public.appointments alter column pattern_secondary type text;

alter table public.sync_cursors add column if not exists last_sync_started_at timestamp with time zone;
alter table public.sync_cursors add column if not exists last_sync_completed_at timestamp with time zone;
alter table public.sync_cursors add column if not exists records_fetched integer;
alter table public.sync_cursors add column if not exists records_inserted integer;
alter table public.sync_cursors add column if not exists records_updated integer;

comment on column public.sync_cursors.watermark is
  'Last successful checkpoint: newest DateTStamp (HistDateTStamp for histappointments, SecDateTEdit for treatplans) saved, on Open Dental''s clock. Moved only after every fetched record was saved.';
comment on column public.sync_cursors.last_sync_started_at is 'When the last pull started.';
comment on column public.sync_cursors.last_sync_completed_at is 'When the last successful pull finished.';
comment on column public.sync_cursors.records_fetched is 'Records Open Dental returned in the last pull.';
comment on column public.sync_cursors.records_inserted is 'Records the last pull inserted.';
comment on column public.sync_cursors.records_updated is 'Records the last pull updated (changed content only).';

-- One checkpoint per clinic and API, under the names used to discuss them.
create or replace view public.sync_checkpoints as
select
  c.clinic_id,
  case when c.resource like 'core:%' then substr(c.resource, 6) else c.resource end as api_name,
  c.resource,
  c.watermark as last_successful_datestamp,
  c.last_sync_started_at,
  c.last_sync_completed_at,
  c.status as sync_status,
  c.records_fetched,
  c.records_inserted,
  c.records_updated,
  c.last_error as error_message,
  c.supports_tstamp as pulls_changes,
  c.last_full_at,
  c.next_due_at
from public.sync_cursors c;

-- The view runs with the caller's rights, so row level security on sync_cursors applies.
alter view public.sync_checkpoints set (security_invoker = true);

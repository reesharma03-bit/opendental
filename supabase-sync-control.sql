-- ============================================================================
-- supabase-sync-control.sql
-- ============================================================================
-- Controls for the Open Dental sync, changed from the dashboard's Sync settings
-- screen (Admins with the Sync permission):
--
--   sync_control           one row: is all syncing paused, by whom, since when.
--                          While paused nothing is read from Open Dental (change
--                          pulls, resource ticks, nightly and Force Sync) and
--                          dashboard edits wait in od_sync_queue. A running
--                          Force Sync stops after the resource it is on.
--   clinic_sync_resources  per clinic, which screens' data is synced.
--                          resources = NULL means everything.
--
-- Until this script is run the backend syncs everything and cannot be paused.
-- Safe to run more than once.
-- ============================================================================

create table if not exists public.sync_control (
  id smallint primary key default 1 check (id = 1),
  paused boolean not null default false,
  paused_by text,
  paused_at timestamptz,
  reason text,
  updated_at timestamptz not null default now()
);

insert into public.sync_control (id) values (1) on conflict (id) do nothing;

create table if not exists public.clinic_sync_resources (
  clinic_id uuid primary key references public.clinics(id) on delete cascade,
  resources text[],
  updated_by text,
  updated_at timestamptz not null default now()
);

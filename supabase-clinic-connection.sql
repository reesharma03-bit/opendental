-- ============================================================================
-- supabase-clinic-connection.sql
-- ============================================================================
-- Settings of the "Open Dental" page (connect a practice to SmileOS):
--
--   clinic_webhooks   per clinic, the office computer that sends Open Dental's
--                     webhook events (Open Dental's "Workstation": the PC running
--                     the Open Dental API Service), and when SmileOS last checked
--                     the clinic's webhook subscriptions.
--
-- The clinic itself is still a row in public.clinics, now created from the page.
-- Until this script is run, connecting works but webhooks are not set up.
-- Safe to run more than once.
-- ============================================================================

create table if not exists public.clinic_webhooks (
  clinic_id uuid primary key references public.clinics(id) on delete cascade,
  workstation text,
  checked_at timestamptz,
  check_result text,
  updated_at timestamptz not null default now()
);

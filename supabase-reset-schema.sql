-- ============================================================================
-- supabase-reset-schema.sql
-- ============================================================================
-- DANGER: Drops ALL tables and views that `supabase-multitenant-schema.sql`
-- manages, then you re-run that file to recreate them with the NEW structure.
--
-- WHY THIS IS REQUIRED
-- --------------------
-- `create table if not exists` only checks the TABLE NAME. It NEVER alters an
-- object that already exists. The new multi-tenant schema changes the shape of
-- the existing core tables:
--
--   old patients         pat_num  bigint PRIMARY KEY            (no clinic_id)
--   new patients         PRIMARY KEY (clinic_id, pat_num)     (adds clinic_id)
--
--   old appointments     apt_num  bigint PRIMARY KEY
--   new appointments     PRIMARY KEY (clinic_id, apt_num)
--
--   ...and the same for documents & procedure_logs (PK + FK all move to the
--   composite (clinic_id, <num>) key, and SaaS columns like subscription_tier /
--   max_patients were added to clinics).
--
-- Changing a primary key / foreign key cannot be done with ADD COLUMN or a
-- CREATE TABLE IF NOT EXISTS "no-op". So the old-format tables must be dropped
-- and recreated.
--
-- USE ONLY ON A DEV / TEST DATABASE OR ONE YOU CAN RE-SEED.
-- Re-running the consolidated schema after this restores the seed clinics.
-- ============================================================================

-- Drop helper views first (they reference profiles/patients and would block drops).
drop view if exists public.v_clinic_patient_counts;
drop view if exists public.v_clinic_activity;

-- Drop all tenant data tables in dependency order (children before parents).
-- CASCADE is a safety net for any residual foreign keys.
drop table if exists public.webhook_events cascade;
drop table if exists public.sync_runs        cascade;
drop table if exists public.pat_fields       cascade;
drop table if exists public.schedules        cascade;
drop table if exists public.tooth_initials   cascade;
drop table if exists public.providers        cascade;
drop table if exists public.operatories      cascade;
drop table if exists public.od_clinics       cascade;
drop table if exists public.procedure_logs   cascade;
drop table if exists public.documents        cascade;
drop table if exists public.appointments     cascade;
drop table if exists public.patients         cascade;
drop table if exists public.clinics          cascade;

-- ============================================================================
-- NEXT STEP:  Paste `supabase-multitenant-schema.sql` and run it. It will now
-- CREATE the tables fresh with the composite primary keys, clinic_id columns,
-- SaaS fields, RLS policies, triggers, views, and seed data.
-- ============================================================================
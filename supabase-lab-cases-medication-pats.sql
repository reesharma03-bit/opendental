-- ============================================================================
-- supabase-lab-cases-medication-pats.sql
-- ============================================================================
-- Own tables for the two remaining Open Dental webhook resources, so every
-- webhook (database event) now has a table of its own:
--
--   LabCase / LabCaseDeleted              -> lab_cases
--   MedicationPat / MedicationPatDeleted  -> medication_pats
--
-- Filled by the webhooks, and after every sync from the copy in
-- od_resource_records (which the API Catalog screens keep using). A record
-- deleted in Open Dental is soft-deleted here (is_deleted = true).
--
-- Fields follow Open Dental's API: https://www.opendental.com/site/apilabcases.html
-- and https://www.opendental.com/site/apimedicationpats.html
--
-- Safe to run more than once.
-- ============================================================================

create table if not exists public.lab_cases (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  lab_case_num bigint not null,
  pat_num bigint,
  laboratory_num bigint,
  apt_num bigint,
  planned_apt_num bigint,
  date_time_due timestamp,
  date_time_created timestamp,
  date_time_sent timestamp,
  date_time_recd timestamp,
  date_time_checked timestamp,
  prov_num bigint,
  instructions text default '',
  lab_fee numeric(12,2),
  invoice_num text default '',
  date_t_stamp timestamp,
  created_at timestamp with time zone default now(),
  updated_at timestamp with time zone default now(),
  deleted_by bigint,
  deleted_at timestamp,
  is_deleted boolean default false,
  primary key (clinic_id, lab_case_num)
);

create index if not exists lab_cases_patient_idx on public.lab_cases (clinic_id, pat_num);
create index if not exists lab_cases_due_idx on public.lab_cases (clinic_id, date_time_due) where not is_deleted;

create table if not exists public.medication_pats (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  medication_pat_num bigint not null,
  pat_num bigint,
  medication_num bigint,
  med_name text default '',
  pat_note text default '',
  date_start date,
  date_stop date,
  prov_num bigint,
  created_at timestamp with time zone default now(),
  updated_at timestamp with time zone default now(),
  deleted_by bigint,
  deleted_at timestamp,
  is_deleted boolean default false,
  primary key (clinic_id, medication_pat_num)
);

create index if not exists medication_pats_patient_idx on public.medication_pats (clinic_id, pat_num);
create index if not exists medication_pats_medication_idx on public.medication_pats (clinic_id, medication_num);

-- Only the backend (service connection) reads and writes these tables.
alter table public.lab_cases enable row level security;
alter table public.medication_pats enable row level security;

comment on table public.lab_cases is 'Open Dental lab cases (webhook LabCase / LabCaseDeleted, and the sync).';
comment on table public.medication_pats is 'Open Dental patient medications (webhook MedicationPat / MedicationPatDeleted, and the sync).';

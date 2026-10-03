-- ============================================================================
-- Supabase Multi-Tenant SaaS Schema - Consolidated Migration
-- ============================================================================
-- Architecture:
--   Clinic A          Clinic B          Clinic C
--   Open Dental       Open Dental       Open Dental
--   Instance          Instance          Instance
--        |                 |                 |
--        v                 v                 v
--      Webhook / Sync Agent (per clinic)
--        |                 |                 |
--        +-----------------+-----------------+
--                          v
--              Spring Boot Sync Service
--                          |
--         +----------------+----------------+
--         |                |                |
--         v                v                v
--  Patient Sync      Appointment Sync  Procedure Sync
--         |                |                |
--         v                v                v
--       Multi-Tenant Supabase (RLS per clinic)
-- ============================================================================

-- ============================================================================
-- 1. updated_at trigger function (shared)
-- ============================================================================

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- ============================================================================
-- 2. Clinics table - tenant registry
-- ============================================================================

create table if not exists public.clinics (
  id uuid primary key default gen_random_uuid(),
  clinic_name text not null,
  clinic_code text not null unique,
  base_url text not null,
  api_key text default '',
  is_active boolean default true,
  -- Multi-tenant SaaS fields
  subscription_tier text default 'free',          -- free | pro | enterprise
  max_patients integer default 1000,              -- tenant limit
  webhook_secret text default '',                 -- secret for webhook verification
  last_sync_at timestamp with time zone,          -- last successful sync
  last_webhook_at timestamp with time zone,       -- last webhook received
  created_at timestamp with time zone default now(),
  updated_at timestamp with time zone default now()
);

create index if not exists clinics_clinic_code_idx on public.clinics (clinic_code);
create index if not exists clinics_is_active_idx on public.clinics (is_active);

-- updated_at trigger
drop trigger if exists clinics_updated_at on public.clinics;
create trigger clinics_updated_at
  before update on public.clinics
  for each row
  execute function public.set_updated_at();

comment on table public.clinics is 'Tenant registry: each row is a clinic with its own Open Dental instance.';
comment on column public.clinics.webhook_secret is 'Secret used to verify inbound webhook requests from Open Dental.';
comment on column public.clinics.last_sync_at is 'Timestamp of the last successful reconciliation sync.';
comment on column public.clinics.last_webhook_at is 'Timestamp of the last webhook event received.';

-- ============================================================================
-- 3. Patients table - tenant-scoped
-- ============================================================================

create table if not exists public.patients (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  pat_num bigint not null,
  l_name text not null,
  f_name text not null,
  middle_i text default '',
  preferred text default '',
  pat_status text default 'Patient',
  gender text default '',
  position text default '',
  birthdate date,
  ssn text default '',
  address text default '',
  address2 text default '',
  city text default '',
  state text default '',
  zip text default '',
  hm_phone text default '',
  wk_phone text default '',
  wireless_phone text default '',
  guarantor bigint default 0,
  email text default '',
  pri_prov bigint default 1,
  sec_prov bigint default 0,
  fee_sched bigint default 0,
  billing_type text default 'Standard',
  chart_number text default '',
  medicaid_id text default '',
  employer_num bigint default 0,
  date_first_visit date,
  clinic_num bigint default 0,
  clinic_abbr text default '',
  has_ins text default '',
  premed boolean default false,
  ward text default '',
  prefer_confirm_method text default 'None',
  prefer_contact_method text default 'None',
  prefer_recall_method text default 'None',
  language text default '',
  admit_date date,
  site_num bigint default 0,
  site_desc text default '',
  super_family bigint default 0,
  txt_msg_ok text default 'Unknown',
  sec_user_num_entry bigint default 0,
  sec_date_entry date,
  est_balance numeric(12,2) default 0,
  bal_0_30 numeric(12,2) default 0,
  bal_31_60 numeric(12,2) default 0,
  bal_61_90 numeric(12,2) default 0,
  bal_over_90 numeric(12,2) default 0,
  ins_est numeric(12,2) default 0,
  bal_total numeric(12,2) default 0,
  date_time_last_aging timestamp,
  created_at timestamp with time zone default now(),
  updated_at timestamp with time zone default now(),
  primary key (clinic_id, pat_num)
);

create index if not exists patients_last_name_idx on public.patients (l_name);
create index if not exists patients_first_name_idx on public.patients (f_name);
create index if not exists patients_birthdate_idx on public.patients (birthdate);
create index if not exists patients_clinic_id_idx on public.patients (clinic_id);

-- updated_at trigger
drop trigger if exists patients_updated_at on public.patients;
create trigger patients_updated_at
  before update on public.patients
  for each row
  execute function public.set_updated_at();

-- ============================================================================
-- 4. Appointments table - tenant-scoped
-- ============================================================================

create table if not exists public.appointments (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  apt_num bigint not null,
  pat_num bigint not null,
  apt_status text default '',
  pattern text default '',
  confirmed bigint default 0,
  time_locked text default '',
  op bigint default 0,
  note text default '',
  prov_num bigint default 0,
  prov_abbr text default '',
  prov_hyg bigint default 0,
  apt_date_time timestamp,
  next_apt_num bigint default 0,
  unsched_status bigint default 0,
  is_new_patient text default '',
  proc_descript text default '',
  assistant bigint default 0,
  clinic_num bigint default 0,
  is_hygiene text default '',
  date_t_stamp timestamp,
  date_time_arrived timestamp,
  date_time_seated timestamp,
  date_time_dismissed timestamp,
  ins_plan1 bigint default 0,
  ins_plan2 bigint default 0,
  date_time_asked_to_arrive timestamp,
  color_override text default '',
  appointment_type_num bigint default 0,
  sec_user_num_entry bigint default 0,
  sec_date_t_entry timestamp,
  priority text default '',
  pattern_secondary text default '',
  item_order_planned bigint default 0,
  is_mirrored text default '',
  e_service_log_type text default '',
  created_at timestamp with time zone default now(),
  updated_at timestamp with time zone default now(),
  deleted_by bigint,
  datetime_deleted timestamp,
  appointment_num bigint,
  is_deleted boolean default false,
  primary key (clinic_id, apt_num),
  foreign key (clinic_id, pat_num) references public.patients(clinic_id, pat_num) on delete cascade
);

create index if not exists appointments_pat_num_idx on public.appointments (pat_num);
create index if not exists appointments_apt_date_time_idx on public.appointments (apt_date_time);
create index if not exists appointments_apt_status_idx on public.appointments (apt_status);
create index if not exists appointments_prov_num_idx on public.appointments (prov_num);
create index if not exists appointments_clinic_num_idx on public.appointments (clinic_num);
create index if not exists appointments_clinic_id_idx on public.appointments (clinic_id);

-- updated_at trigger
drop trigger if exists appointments_updated_at on public.appointments;
create trigger appointments_updated_at
  before update on public.appointments
  for each row
  execute function public.set_updated_at();

-- ============================================================================
-- 5. Documents table - tenant-scoped
-- ============================================================================

create table if not exists public.documents (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  doc_num bigint not null,
  pat_num bigint not null,
  description text default '',
  note text default '',
  date_created timestamp,
  doc_category bigint default 0,
  file_name text default '',
  img_type text default 'Document',
  tooth_numbers text default '',
  date_t_stamp timestamp,
  prov_num bigint default 0,
  print_heading text default 'false',
  created_at timestamp with time zone default now(),
  updated_at timestamp with time zone default now(),
  primary key (clinic_id, doc_num),
  foreign key (clinic_id, pat_num) references public.patients(clinic_id, pat_num) on delete cascade
);

create index if not exists documents_pat_num_idx on public.documents (pat_num);
create index if not exists documents_date_created_idx on public.documents (date_created);
create index if not exists documents_doc_category_idx on public.documents (doc_category);
create index if not exists documents_clinic_id_idx on public.documents (clinic_id);

-- updated_at trigger
drop trigger if exists documents_updated_at on public.documents;
create trigger documents_updated_at
  before update on public.documents
  for each row
  execute function public.set_updated_at();

-- ============================================================================
-- 6. Procedure Logs table - tenant-scoped
-- ============================================================================

create table if not exists public.procedure_logs (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  proc_num bigint not null,
  pat_num bigint not null,
  apt_num bigint default 0,
  proc_date date,
  proc_fee numeric(12,2) default 0,
  surf text default '',
  tooth_num text default '',
  tooth_range text default '',
  priority bigint default 0,
  proc_status text default '',
  prov_num bigint default 0,
  prov_abbr text default '',
  dx bigint default 0,
  dx_name text default '',
  planned_apt_num bigint default 0,
  place_service text default '',
  prosthesis text default '',
  date_original_prosth date,
  claim_note text default '',
  date_entry_c date,
  clinic_num bigint default 0,
  diagnostic_code text default '',
  is_princ_diag text default '',
  code_num bigint default 0,
  proc_code text default '',
  descript text default '',
  unit_qty integer default 0,
  base_units integer default 0,
  date_tp date,
  site_num bigint default 0,
  hide_graphics text default '',
  canadian_type_codes text default '',
  proc_time text default '',
  proc_time_end text default '',
  date_t_stamp timestamp,
  prognosis bigint default 0,
  is_locked text default '',
  billing_note text default '',
  snomed_body_site text default '',
  diagnostic_code2 text default '',
  diagnostic_code3 text default '',
  diagnostic_code4 text default '',
  discount numeric(12,2) default 0,
  is_date_prosth_est text default '',
  icd_version integer default 0,
  sec_date_entry timestamp,
  discount_plan_amt numeric(12,2) default 0,
  created_at timestamp with time zone default now(),
  updated_at timestamp with time zone default now(),
  primary key (clinic_id, proc_num),
  foreign key (clinic_id, pat_num) references public.patients(clinic_id, pat_num) on delete cascade,
  foreign key (clinic_id, apt_num) references public.appointments(clinic_id, apt_num) on delete set null
);

create index if not exists procedure_logs_pat_num_idx on public.procedure_logs (pat_num);
create index if not exists procedure_logs_apt_num_idx on public.procedure_logs (apt_num);
create index if not exists procedure_logs_proc_date_idx on public.procedure_logs (proc_date);
create index if not exists procedure_logs_proc_status_idx on public.procedure_logs (proc_status);
create index if not exists procedure_logs_prov_num_idx on public.procedure_logs (prov_num);
create index if not exists procedure_logs_clinic_num_idx on public.procedure_logs (clinic_num);
create index if not exists procedure_logs_proc_code_idx on public.procedure_logs (proc_code);
create index if not exists procedure_logs_clinic_id_idx on public.procedure_logs (clinic_id);

-- updated_at trigger
drop trigger if exists procedure_logs_updated_at on public.procedure_logs;
create trigger procedure_logs_updated_at
  before update on public.procedure_logs
  for each row
  execute function public.set_updated_at();

-- ============================================================================
-- 6b. od_clinics - Open Dental clinics within each tenant installation
-- ============================================================================
-- DIFFERENT from `clinics` (tenant registry). Each tenant Open Dental
-- installation can report multiple clinics via GET /api/v1/clinics.
-- Primary key: (clinic_id, clinic_num)

create table if not exists public.od_clinics (
  clinic_id   uuid   not null references public.clinics(id) on delete cascade,
  clinic_num  bigint not null,
  abbr        text,
  description text,
  created_at  timestamp with time zone default now(),
  updated_at  timestamp with time zone default now(),
  primary key (clinic_id, clinic_num)
);

create index if not exists od_clinics_clinic_id_idx on public.od_clinics (clinic_id);

-- updated_at trigger
drop trigger if exists od_clinics_updated_at on public.od_clinics;
create trigger od_clinics_updated_at
  before update on public.od_clinics
  for each row
  execute function public.set_updated_at();

comment on table public.od_clinics is 'Open Dental clinics reported by GET /api/v1/clinics within each tenant installation.';
comment on column public.od_clinics.clinic_id is 'FK to clinics.id — the tenant Open Dental installation.';
comment on column public.od_clinics.clinic_num is 'Open Dental ClinicNum (ClinicNum), unique within an installation.';

-- ============================================================================
-- 6c. operatories - treatment rooms / chairs (tenant-scoped)
-- ============================================================================

create table if not exists public.operatories (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  operatory_num bigint not null,
  abbrev text default '',
  description text default '',
  clinic_num bigint default 0,
  is_hygiene text default '',
  is_disabled text default '',
  is_web_sched text default '',
  order_value bigint default 0,
  created_at timestamp with time zone default now(),
  updated_at timestamp with time zone default now(),
  primary key (clinic_id, operatory_num)
);

create index if not exists operatories_abbrev_idx on public.operatories (abbrev);
create index if not exists operatories_clinic_id_idx on public.operatories (clinic_id);

-- updated_at trigger
drop trigger if exists operatories_updated_at on public.operatories;
create trigger operatories_updated_at
  before update on public.operatories
  for each row
  execute function public.set_updated_at();

comment on table public.operatories is 'Operatory (treatment room/chair) records synced from Open Dental.';
comment on column public.operatories.clinic_id is 'FK to clinics table; separates data per clinic Open Dental instance.';
comment on column public.operatories.operatory_num is 'Open Dental operatory identifier, unique only within a clinic.';

-- ============================================================================
-- 6d. pat_fields - patient custom fields (tenant-scoped)
-- ============================================================================

create table if not exists public.pat_fields (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  pat_field_num bigint not null,
  pat_num bigint not null,
  field_name text default '',
  field_value text default '',
  field_desc text default '',
  field_type text default '',
  clinic_num bigint default 0,
  created_at timestamp with time zone default now(),
  updated_at timestamp with time zone default now(),
  deleted_by bigint,
  deleted_at timestamp,
  is_deleted boolean default false,
  primary key (clinic_id, pat_field_num)
);

create index if not exists pat_fields_pat_num_idx on public.pat_fields (pat_num);
create index if not exists pat_fields_field_name_idx on public.pat_fields (field_name);
create index if not exists pat_fields_clinic_id_idx on public.pat_fields (clinic_id);

-- updated_at trigger
drop trigger if exists pat_fields_updated_at on public.pat_fields;
create trigger pat_fields_updated_at
  before update on public.pat_fields
  for each row
  execute function public.set_updated_at();

comment on table public.pat_fields is 'Patient custom field records synced from Open Dental.';

-- ============================================================================
-- 6e. providers - dentists, hygienists, etc. (tenant-scoped)
-- ============================================================================

create table if not exists public.providers (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  prov_num bigint not null,
  abbrev text default '',
  f_name text default '',
  l_name text default '',
  suffix text default '',
  specialty text default '',
  prov_status text default '',
  prov_type text default '',
  clinic_num bigint default 0,
  created_at timestamp with time zone default now(),
  updated_at timestamp with time zone default now(),
  primary key (clinic_id, prov_num)
);

create index if not exists providers_abbrev_idx on public.providers (abbrev);
create index if not exists providers_l_name_idx on public.providers (l_name);
create index if not exists providers_clinic_id_idx on public.providers (clinic_id);

-- updated_at trigger
drop trigger if exists providers_updated_at on public.providers;
create trigger providers_updated_at
  before update on public.providers
  for each row
  execute function public.set_updated_at();

comment on table public.providers is 'Provider records synced from Open Dental.';

-- ============================================================================
-- 6f. schedules — provider/operatory availability (tenant-scoped)
-- ============================================================================

create table if not exists public.schedules (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  schedule_num bigint not null,
  sched_date date,
  sched_type_num bigint default 0,
  prov_num bigint default 0,
  clinic_num bigint default 0,
  start_time text default '',
  stop_time text default '',
  blockout text default '',
  created_at timestamp with time zone default now(),
  updated_at timestamp with time zone default now(),
  deleted_by bigint,
  deleted_at timestamp,
  is_deleted boolean default false,
  primary key (clinic_id, schedule_num)
);

create index if not exists schedules_sched_date_idx on public.schedules (sched_date);
create index if not exists schedules_prov_num_idx on public.schedules (prov_num);
create index if not exists schedules_clinic_id_idx on public.schedules (clinic_id);

-- updated_at trigger
drop trigger if exists schedules_updated_at on public.schedules;
create trigger schedules_updated_at
  before update on public.schedules
  for each row
  execute function public.set_updated_at();

comment on table public.schedules is 'Schedule records synced from Open Dental.';

-- ============================================================================
-- 6g. tooth_initials - initial tooth charting (tenant-scoped)
-- ============================================================================

create table if not exists public.tooth_initials (
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  tooth_initial_num bigint not null,
  pat_num bigint not null,
  tooth_num text default '',
  tooth_type text default '',
  tooth_group text default '',
  mobility text default '',
  date_t_stamp timestamp,
  created_at timestamp with time zone default now(),
  updated_at timestamp with time zone default now(),
  deleted_by bigint,
  deleted_at timestamp,
  is_deleted boolean default false,
  primary key (clinic_id, tooth_initial_num)
);

create index if not exists tooth_initials_pat_num_idx on public.tooth_initials (pat_num);
create index if not exists tooth_initials_clinic_id_idx on public.tooth_initials (clinic_id);

-- updated_at trigger
drop trigger if exists tooth_initials_updated_at on public.tooth_initials;
create trigger tooth_initials_updated_at
  before update on public.tooth_initials
  for each row
  execute function public.set_updated_at();

comment on table public.tooth_initials is 'Initial tooth charting records synced from Open Dental.';

-- ============================================================================
-- 7. Webhook Events table - tracks all inbound webhook events
-- ============================================================================

create table if not exists public.webhook_events (
  id uuid primary key default gen_random_uuid(),
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  watch_table text not null,               -- Patient | Appointment | Document | ProcedureLog | Query
  event_type text default 'upsert',        -- created | updated | deleted | upsert
  payload jsonb,                           -- raw webhook payload
  status text default 'received',          -- received | processed | failed
  error_message text default '',
  processed_at timestamp with time zone,
  created_at timestamp with time zone default now()
);

create index if not exists webhook_events_clinic_id_idx on public.webhook_events (clinic_id);
create index if not exists webhook_events_watch_table_idx on public.webhook_events (watch_table);
create index if not exists webhook_events_status_idx on public.webhook_events (status);
create index if not exists webhook_events_created_at_idx on public.webhook_events (created_at);

comment on table public.webhook_events is 'Audit log of all inbound webhook events from Open Dental per clinic.';

-- ============================================================================
-- 8. Sync Runs table - tracks reconciliation job executions
-- ============================================================================

create table if not exists public.sync_runs (
  id uuid primary key default gen_random_uuid(),
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  sync_type text not null,                 -- initial | webhook | reconciliation
  entity_type text not null,               -- patient | appointment | document | procedurelog | all
  total_found integer default 0,
  total_synced integer default 0,
  total_failed integer default 0,
  status text default 'running',           -- running | completed | failed
  error_message text default '',
  started_at timestamp with time zone default now(),
  completed_at timestamp with time zone
);

create index if not exists sync_runs_clinic_id_idx on public.sync_runs (clinic_id);
create index if not exists sync_runs_sync_type_idx on public.sync_runs (sync_type);
create index if not exists sync_runs_started_at_idx on public.sync_runs (started_at);

comment on table public.sync_runs is 'Tracks every sync operation (webhook, initial, reconciliation) per clinic.';

-- ============================================================================
-- 8b. Open Dental sync queue - local changes waiting to be pushed to Open Dental
-- ============================================================================
-- Kept in sync with supabase-od-sync-queue.sql (the standalone migration).

create table if not exists public.od_sync_queue (
  id bigserial primary key,
  clinic_id uuid not null references public.clinics(id) on delete cascade,
  entity_type text not null,               -- patient | appointment | document | procedurelog
  operation text not null,                 -- CREATE | UPDATE | DELETE | PLANNED | SCHEDULE_PLANNED | WEBSCHED | BREAK | NOTE | CONFIRM | SET_BY_URL | INSURANCE_HISTORY
  local_id bigint not null,                -- key of the row in our table (negative while temporary)
  od_id bigint,                            -- key assigned by Open Dental, once known
  payload text,                            -- JSON request for Open Dental; cleared once pushed
  status text not null default 'PENDING',  -- PENDING | IN_PROGRESS | DONE | FAILED | CANCELLED
  attempts integer not null default 0,
  last_error text,
  next_attempt_at timestamp with time zone not null default now(),
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now()
);

create index if not exists od_sync_queue_due_idx on public.od_sync_queue (status, next_attempt_at);
create index if not exists od_sync_queue_record_idx on public.od_sync_queue (clinic_id, entity_type, local_id);

alter table public.od_sync_queue enable row level security;

comment on table public.od_sync_queue is 'Outbox of local changes waiting to be pushed to Open Dental.';

-- ============================================================================
-- 8c. Open Dental resource records - every API resource without its own table
-- ============================================================================
-- Kept in sync with supabase-od-resource-records.sql (the standalone migration).

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

-- ============================================================================
-- 9. RLS Policies - Multi-Tenant Data Isolation
-- ============================================================================
-- Enables Row Level Security so each clinic/tenant can only access its own rows.
-- The service role (used by Spring Boot) bypasses RLS.
-- For direct Supabase client access, use a JWT with a `clinic_id` claim.

alter table public.clinics        enable row level security;
alter table public.patients       enable row level security;
alter table public.appointments   enable row level security;
alter table public.documents      enable row level security;
alter table public.procedure_logs enable row level security;
alter table public.webhook_events enable row level security;
alter table public.sync_runs      enable row level security;

-- Clinics: tenants can only see their own clinic row
drop policy if exists clinics_select_policy on public.clinics;
create policy clinics_select_policy on public.clinics
  for select
  using (
    auth.uid() is null
    or id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

-- Patients
drop policy if exists patients_select_policy on public.patients;
create policy patients_select_policy on public.patients
  for select
  using (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

drop policy if exists patients_insert_policy on public.patients;
create policy patients_insert_policy on public.patients
  for insert
  with check (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

drop policy if exists patients_update_policy on public.patients;
create policy patients_update_policy on public.patients
  for update
  using (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

-- Appointments
drop policy if exists appointments_select_policy on public.appointments;
create policy appointments_select_policy on public.appointments
  for select
  using (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

drop policy if exists appointments_insert_policy on public.appointments;
create policy appointments_insert_policy on public.appointments
  for insert
  with check (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

drop policy if exists appointments_update_policy on public.appointments;
create policy appointments_update_policy on public.appointments
  for update
  using (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

-- Documents
drop policy if exists documents_select_policy on public.documents;
create policy documents_select_policy on public.documents
  for select
  using (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

drop policy if exists documents_insert_policy on public.documents;
create policy documents_insert_policy on public.documents
  for insert
  with check (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

drop policy if exists documents_update_policy on public.documents;
create policy documents_update_policy on public.documents
  for update
  using (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

-- Procedure Logs
drop policy if exists procedure_logs_select_policy on public.procedure_logs;
create policy procedure_logs_select_policy on public.procedure_logs
  for select
  using (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

drop policy if exists procedure_logs_insert_policy on public.procedure_logs;
create policy procedure_logs_insert_policy on public.procedure_logs
  for insert
  with check (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

drop policy if exists procedure_logs_update_policy on public.procedure_logs;
create policy procedure_logs_update_policy on public.procedure_logs
  for update
  using (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

-- Webhook events (service-only access)
drop policy if exists webhook_events_select_policy on public.webhook_events;
create policy webhook_events_select_policy on public.webhook_events
  for select
  using (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

-- Sync runs (service-only access)
drop policy if exists sync_runs_select_policy on public.sync_runs;
create policy sync_runs_select_policy on public.sync_runs
  for select
  using (
    auth.uid() is null
    or clinic_id = coalesce(
      (auth.jwt() ->> 'clinic_id')::uuid,
      '00000000-0000-0000-0000-000000000000'::uuid
    )
  );

-- ============================================================================
-- 10. Sample Clinic Seed Data (Multi-Tenant Example)
-- ============================================================================
-- Three sample clinics: A, B, C
-- Each has its own Open Dental instance base URL.

insert into public.clinics (clinic_name, clinic_code, base_url, api_key, is_active, subscription_tier, max_patients)
values
  ('Clinic A - Downtown', 'CLINIC_A', 'https://opendental-a.example.com/api/v1', '', true, 'enterprise', 100000),
  ('Clinic B - Uptown',   'CLINIC_B', 'https://opendental-b.example.com/api/v1', '', true, 'pro',       50000),
  ('Clinic C - Suburb',   'CLINIC_C', 'https://opendental-c.example.com/api/v1', '', true, 'free',      1000)
on conflict (clinic_code) do update set
  clinic_name       = excluded.clinic_name,
  base_url          = excluded.base_url,
  api_key           = excluded.api_key,
  is_active         = excluded.is_active,
  subscription_tier = excluded.subscription_tier,
  max_patients      = excluded.max_patients;

-- ============================================================================
-- 11. Helper views for cross-clinic SaaS analytics
-- ============================================================================

-- Patient counts per clinic
create or replace view public.v_clinic_patient_counts as
select
  c.clinic_code,
  c.clinic_name,
  count(p.pat_num) as patient_count
from public.clinics c
left join public.patients p on p.clinic_id = c.id
group by c.clinic_code, c.clinic_name;

-- Recently active clinics (last sync/webhook activity)
create or replace view public.v_clinic_activity as
select
  clinic_code,
  clinic_name,
  is_active,
  last_sync_at,
  last_webhook_at,
  subscription_tier
from public.clinics
order by greatest(coalesce(last_sync_at, '1970-01-01'), coalesce(last_webhook_at, '1970-01-01')) desc;
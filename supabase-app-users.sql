-- ============================================================================
-- supabase-app-users.sql
-- ============================================================================
-- Dashboard logins, roles and the audit log.
--
-- app_users  : who can sign in to the SmileOS dashboard and with which role.
--              Passwords are stored only as BCrypt hashes.
-- audit_log  : sign-ins, failed sign-ins, user changes and every change made
--              through the API (who, what, when, from where) - HIPAA audit trail.
--
-- The first admin is created by the backend on start-up when app_users is
-- empty and AUTH_BOOTSTRAP_ADMIN_EMAIL / AUTH_BOOTSTRAP_ADMIN_PASSWORD are set.
--
-- Safe to run more than once. Run this on an existing database; it is also
-- included in supabase-multitenant-schema.sql for fresh installs.
-- ============================================================================

create table if not exists public.app_users (
  id uuid primary key default gen_random_uuid(),
  clinic_id uuid references public.clinics(id) on delete set null,
  email text not null,
  full_name text not null,
  role text not null check (role in ('ADMIN', 'DENTIST', 'FRONT_DESK', 'BILLING', 'READ_ONLY')),
  password_hash text not null,
  active boolean not null default true,
  must_change_password boolean not null default false,
  failed_attempts integer not null default 0,
  locked_until timestamp with time zone,
  last_login_at timestamp with time zone,
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now()
);

create unique index if not exists app_users_email_idx on public.app_users (lower(email));

create table if not exists public.audit_log (
  id bigserial primary key,
  user_id uuid,
  user_email text,
  action text not null,          -- LOGIN, LOGIN_FAILED, LOGOUT, PASSWORD_CHANGED, USER_CREATED, USER_UPDATED, API_WRITE, ACCESS_DENIED
  detail text,
  ip_address text,
  created_at timestamp with time zone not null default now()
);

create index if not exists audit_log_created_at_idx on public.audit_log (created_at desc);
create index if not exists audit_log_user_idx on public.audit_log (user_id, created_at desc);

-- Only the backend (service connection) may read or write these tables.
alter table public.app_users enable row level security;
alter table public.audit_log enable row level security;

comment on table public.app_users is 'SmileOS dashboard users and their roles. Passwords are BCrypt hashes.';
comment on table public.audit_log is 'Sign-ins, user changes and data changes made through the API.';

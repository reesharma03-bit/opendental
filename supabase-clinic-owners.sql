-- ============================================================================
-- supabase-clinic-owners.sql
-- ============================================================================
-- Every clinic belongs to a dentist (its owner):
--
--   clinics.owner_user_id  the DENTIST user (app_users.id) the clinic belongs to.
--                          A dentist sees and manages only the clinics they own;
--                          an Admin sees and manages every clinic and can assign
--                          or change the owner. NULL: no owner yet (Admins only).
--
-- Dentists sign up themselves (role DENTIST) or are created by an Admin when the
-- Admin registers a clinic for them. Front desk, Billing and Read-only users still
-- belong to one clinic through app_users.clinic_id.
--
-- Two connected clinics never share a Customer Key (a dentist's clinics each
-- use their own; a disconnected clinic's key can be used by a new clinic).
--
-- Additive only: existing clinics, users and synced data are unchanged. A dentist
-- already linked to a clinic through app_users.clinic_id becomes its owner.
-- Safe to run more than once.
-- ============================================================================

alter table public.clinics
  add column if not exists owner_user_id uuid references public.app_users(id) on delete set null;

create index if not exists clinics_owner_idx on public.clinics (owner_user_id);

-- Two CONNECTED clinics never share a Customer Key (the part after the "/" in
-- "ODFHIR DeveloperKey/CustomerKey"). A disconnected clinic doesn't hold on to
-- its key: a new clinic may use it. (Replaces an earlier, stricter index.)
drop index if exists public.clinics_customer_key_idx;
create unique index if not exists clinics_connected_customer_key_idx
  on public.clinics (split_part(api_key, '/', 2))
  where is_active is true and coalesce(split_part(api_key, '/', 2), '') <> '';

update public.clinics c
   set owner_user_id = u.id
  from public.app_users u
 where u.role = 'DENTIST' and u.clinic_id = c.id and c.owner_user_id is null;

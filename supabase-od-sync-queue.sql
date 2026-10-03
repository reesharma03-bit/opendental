-- ============================================================================
-- supabase-od-sync-queue.sql
-- ============================================================================
-- Outbox for "write to Supabase first, then Open Dental".
--
-- Every create / update / delete made through the API is written to our own
-- tables first and, in the same transaction, recorded here. The backend then
-- pushes each entry to Open Dental (immediately when Open Dental is reachable,
-- otherwise retried by a scheduler with backoff).
--
-- Records created locally get a temporary NEGATIVE key (pat_num, apt_num,
-- doc_num, proc_num = -<queue id>) until Open Dental assigns the real one; the
-- backend then moves the row (and its child rows) to the real key.
--
-- Safe to run more than once. Run this on an existing database; it is also
-- included in supabase-multitenant-schema.sql for fresh installs.
-- ============================================================================

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

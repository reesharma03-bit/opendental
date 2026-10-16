// Sign-in, the signed-in user, and user administration (Admin).
import { request, type Raw } from './backend';

export type Permission =
  | 'PATIENTS_READ' | 'PATIENTS_WRITE' | 'APPOINTMENTS_READ' | 'APPOINTMENTS_WRITE'
  | 'CLINICAL_READ' | 'CLINICAL_WRITE' | 'BILLING_READ' | 'BILLING_WRITE'
  | 'ASSISTANT_USE' | 'SYNC_MANAGE' | 'CLINIC_CONNECT' | 'USERS_MANAGE' | 'SYSTEM_ADMIN';

export type RoleName = 'ADMIN' | 'DENTIST' | 'FRONT_DESK' | 'BILLING' | 'READ_ONLY';

export interface AppUser {
  id: string;
  email: string;
  fullName: string;
  role: RoleName;
  roleLabel: string;
  active: boolean;
  mustChangePassword: boolean;
  locked: boolean;
  lastLoginAt: string | null;
  /** The clinic the user belongs to; null for Admins (they work on any) and Dentists who haven't registered one. */
  clinicCode: string | null;
  clinicName: string | null;
  /** The user's clinic syncs with Open Dental (false once it was disconnected). */
  clinicConnected: boolean;
  /** A dentist's clinics (Users page, for Admins). */
  ownedClinics?: string[];
}

export interface SignedInUser extends AppUser {
  permissions: Permission[];
  sessionMinutes: number;
}

export interface RoleInfo { role: RoleName; label: string; permissions: Permission[] }

export interface AuditEntry { id: number; at: string; user: string | null; action: string; detail: string | null; ip: string | null }

// The backend's global JSON naming is snake_case; accept either spelling.
const field = (raw: Raw, camel: string, snake: string) => raw[camel] ?? raw[snake];

function toUser(raw: Raw): AppUser {
  return {
    id: String(raw.id ?? ''),
    email: String(raw.email ?? ''),
    fullName: String(field(raw, 'fullName', 'full_name') ?? ''),
    role: String(raw.role ?? 'READ_ONLY') as RoleName,
    roleLabel: String(field(raw, 'roleLabel', 'role_label') ?? raw.role ?? ''),
    active: field(raw, 'active', 'active') !== false,
    mustChangePassword: field(raw, 'mustChangePassword', 'must_change_password') === true,
    locked: raw.locked === true,
    lastLoginAt: (field(raw, 'lastLoginAt', 'last_login_at') as string | null) ?? null,
    clinicCode: (field(raw, 'clinicCode', 'clinic_code') as string | null) ?? null,
    clinicName: (field(raw, 'clinicName', 'clinic_name') as string | null) ?? null,
    clinicConnected: field(raw, 'clinicConnected', 'clinic_connected') === true,
    ownedClinics: (field(raw, 'ownedClinics', 'owned_clinics') as string[] | undefined) ?? undefined,
  };
}

function toSignedIn(raw: Raw): SignedInUser {
  return {
    ...toUser(raw),
    permissions: (raw.permissions ?? []) as Permission[],
    sessionMinutes: Number(field(raw, 'sessionMinutes', 'session_minutes') ?? 30),
  };
}

export const signIn = async (email: string, password: string) =>
  toSignedIn(await request<Raw>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }));

/** A dentist creates their account (role DENTIST) and is signed in. */
export const signUpDentist = async (fullName: string, email: string, password: string) =>
  toSignedIn(await request<Raw>('/api/auth/signup', { method: 'POST', body: JSON.stringify({ fullName, email, password }) }));

/** Temporary passwords: 16 characters from a crypto source, letters and digits guaranteed. */
export function temporaryPassword(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';
  const bytes = crypto.getRandomValues(new Uint8Array(14));
  return `${Array.from(bytes, (b) => chars[b % chars.length]).join('')}7a`;
}

export const signOut = () => request<void>('/api/auth/logout', { method: 'POST' });

export const getMe = async () => toSignedIn(await request<Raw>('/api/auth/me'));

export const changePassword = async (currentPassword: string, newPassword: string) =>
  toSignedIn(await request<Raw>('/api/auth/change-password', {
    method: 'POST', body: JSON.stringify({ currentPassword, newPassword }),
  }));

export const listUsers = async () => (await request<Raw[]>('/api/users')).map(toUser);

export const listRoles = async () => (await request<Raw[]>('/api/users/roles')).map((r) => ({
  role: String(r.role) as RoleName,
  label: String(r.label),
  permissions: (r.permissions ?? []) as Permission[],
}));

export const createUser = async (body: { email: string; fullName: string; role: RoleName; password: string; clinicCode?: string }) =>
  toUser(await request<Raw>('/api/users', { method: 'POST', body: JSON.stringify(body) }));

export const updateUser = async (id: string, body: { fullName?: string; role?: RoleName; active?: boolean; clinicCode?: string }) =>
  toUser(await request<Raw>(`/api/users/${id}`, { method: 'PUT', body: JSON.stringify(body) }));

export const resetUserPassword = async (id: string, password: string) =>
  toUser(await request<Raw>(`/api/users/${id}/reset-password`, { method: 'POST', body: JSON.stringify({ password }) }));

export const listAudit = async (limit = 200) => (await request<Raw[]>(`/api/audit?limit=${limit}`)).map((r) => ({
  id: Number(r.id),
  at: String(r.at ?? ''),
  user: (r.user as string | null) ?? null,
  action: String(r.action ?? ''),
  detail: (r.detail as string | null) ?? null,
  ip: (r.ip as string | null) ?? null,
}));

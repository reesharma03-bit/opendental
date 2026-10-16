// The "Open Dental" page: connect the practice with its Customer Key, and keep its webhook
// subscriptions set up (/api/sync/connection). The key is never sent back in full.
import { request } from './backend';

export interface Webhook {
  event: string;
  /** ok: active · failing: Open Dental can't deliver · missing: not set up · off: can't be set up yet · failed: setup error */
  state: 'ok' | 'failing' | 'missing' | 'off' | 'failed';
  detail: string;
}

export interface ConnectedClinic {
  clinicCode: string;
  clinicName: string;
  /** Syncing with Open Dental; false once disconnected (it can be reconnected). */
  connected: boolean;
  baseUrl: string;
  /** Last four characters of the Customer Key, e.g. "••••1234". */
  key: string;
  workstation: string;
  createdAt?: string | null;
  /** In the clinic list (Admins see every clinic). */
  patients?: number;
  users?: number;
  lastSyncAt?: string | null;
  /** The dentist who owns the clinic (null: none yet). */
  ownerUserId?: string | null;
  ownerName?: string | null;
  ownerEmail?: string | null;
}

export interface DentistLookup {
  exists: boolean;
  id?: string;
  fullName?: string;
  email?: string;
  roleLabel?: string;
  active?: boolean;
  isDentist?: boolean;
}

/** (Admins) whether an email belongs to a dentist, before registering a clinic for them. */
export const findDentist = (email: string) =>
  request<DentistLookup>(`/api/sync/connection/dentist?${new URLSearchParams({ email })}`);

export interface ConnectionState {
  /** The user is an Admin: sees every clinic and may register any number. */
  admin: boolean;
  /** May register a new clinic (Admins always; a Dentist until they have one). */
  canRegister: boolean;
  developerKeySet: boolean;
  remoteUrl: string;
  publicUrl: string | null;
  clinic: ConnectedClinic | null;
  /** As of the last check (saved by SmileOS; opening the page doesn't ask Open Dental). */
  webhooks: Webhook[];
  /** Webhooks are being set up in Open Dental right now. */
  settingUp: boolean;
  checkedAt: string | null;
}

export interface ConnectionTest {
  ok: boolean;
  message: string;
  openDentalClinics: number;
}

const json = (method: string, body?: unknown): RequestInit => ({ method, body: body === undefined ? undefined : JSON.stringify(body) });

export const getConnection = () => request<ConnectionState>('/api/sync/connection');

export const testConnection = (customerKey: string, baseUrl: string) =>
  request<ConnectionTest>('/api/sync/connection/test', json('POST', { customerKey, baseUrl }));

export const listClinics = () => request<ConnectedClinic[]>('/api/sync/connection/clinics');

export const registerClinic = (body: { clinicName: string; customerKey: string; baseUrl: string; workstation: string; dentistUserId?: string }) =>
  request<{ message: string; clinicCode: string; clinicName: string }>('/api/sync/connection', json('POST', body));

/** Edits the current clinic; an empty key keeps the current one (a disconnected clinic needs one: it is connected again). */
/** Every action names its clinic by code; the server checks the user may manage it. */
export const updateClinic = (body: { clinicCode: string; clinicName: string; customerKey: string; baseUrl: string; workstation: string; dentistUserId?: string }) =>
  request<{ message: string; clinicCode: string; clinicName: string }>('/api/sync/connection', json('PUT', body));

export const saveWorkstation = (clinicCode: string, workstation: string) =>
  request<{ settingUp: boolean }>('/api/sync/connection/webhooks', json('PUT', { clinicCode, workstation }));

export const repairWebhooks = (clinicCode: string) =>
  request<{ settingUp: boolean }>('/api/sync/connection/webhooks', json('POST', { clinicCode }));

export const disconnectPractice = (clinicCode: string) => request<void>('/api/sync/connection', json('DELETE', { clinicCode }));

/** Removes a disconnected clinic and its copied data for good; {@code confirmName} is its name, typed by the user. */
export const removeClinic = (clinicCode: string, confirmName: string) =>
  request<{ removed: string }>(`/api/sync/connection/clinics/${encodeURIComponent(clinicCode)}`, json('DELETE', { confirmName }));

/** Headline for the webhooks card. */
export function webhookSummary(webhooks: Webhook[]): { tone: 'ok' | 'warn' | 'off'; text: string } {
  if (!webhooks.length) return { tone: 'off', text: 'Not checked yet' };
  if (webhooks.every((w) => w.state === 'off')) return { tone: 'off', text: webhooks[0]!.detail };
  const ok = webhooks.filter((w) => w.state === 'ok').length;
  return ok === webhooks.length
    ? { tone: 'ok', text: `All ${ok} webhooks active` }
    : { tone: 'warn', text: `${ok} of ${webhooks.length} webhooks active` };
}

/** "AppointmentDeleted" -> "Appointment deleted". */
export const eventLabel = (event: string) =>
  event.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/ (\w)/g, (_, c: string) => ` ${c.toLowerCase()}`);

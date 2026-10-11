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
  baseUrl: string;
  /** Last four characters of the Customer Key, e.g. "••••1234". */
  key: string;
  workstation: string;
}

export interface ConnectionState {
  developerKeySet: boolean;
  remoteUrl: string;
  publicUrl: string | null;
  clinic: ConnectedClinic | null;
  webhooks: Webhook[];
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

export const connectPractice = (body: { clinicName: string; customerKey: string; baseUrl: string; workstation: string }) =>
  request<{ message: string; webhooks: Webhook[] }>('/api/sync/connection', json('POST', body));

export const changeKey = (customerKey: string, baseUrl: string) =>
  request<{ message: string; webhooks: Webhook[] }>('/api/sync/connection/key', json('PUT', { customerKey, baseUrl }));

export const saveWorkstation = (workstation: string) =>
  request<{ webhooks: Webhook[] }>('/api/sync/connection/webhooks', json('PUT', { workstation }));

export const repairWebhooks = () => request<{ webhooks: Webhook[] }>('/api/sync/connection/webhooks', json('POST'));

export const disconnectPractice = () => request<void>('/api/sync/connection', json('DELETE'));

/** Headline for the webhooks card. */
export function webhookSummary(webhooks: Webhook[]): { tone: 'ok' | 'warn' | 'off'; text: string } {
  if (!webhooks.length) return { tone: 'off', text: 'Not set up' };
  if (webhooks.every((w) => w.state === 'off')) return { tone: 'off', text: webhooks[0]!.detail };
  const ok = webhooks.filter((w) => w.state === 'ok').length;
  return ok === webhooks.length
    ? { tone: 'ok', text: `All ${ok} webhooks active` }
    : { tone: 'warn', text: `${ok} of ${webhooks.length} webhooks active` };
}

/** "AppointmentDeleted" -> "Appointment deleted". */
export const eventLabel = (event: string) =>
  event.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/ (\w)/g, (_, c: string) => ` ${c.toLowerCase()}`);

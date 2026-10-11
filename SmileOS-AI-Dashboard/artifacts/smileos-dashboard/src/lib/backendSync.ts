// Force Sync: POST /api/sync/force starts a full copy of Open Dental into Supabase
// in the background; GET /api/sync/force/status reports its progress.
// Sync settings: pause / resume all syncing, and which screens' data each clinic syncs.
import { request } from './backend';
import { apiNavigationGroups, formatApiResourceName } from '../apiNavigation';

export interface SyncResourceResult {
  clinic: string;
  resource: string;
  records: number;
  status: 'ok' | 'failed';
  error?: string;
}

export interface SyncStatus {
  state: 'idle' | 'running' | 'completed' | 'completed_with_errors' | 'failed' | 'paused';
  type?: 'full' | 'resources';
  trigger?: string;
  startedAt?: string;
  finishedAt?: string | null;
  currentStep?: string | null;
  done?: number;
  total?: number;
  records?: number;
  failures?: number;
  error?: string | null;
  results?: SyncResourceResult[];
}

export const startForceSync = () => request<SyncStatus>('/api/sync/force', { method: 'POST' });

export const getSyncStatus = () => request<SyncStatus>('/api/sync/force/status');

// ---------------------------------------------------------------- pause / resume

export interface SyncControlState {
  paused: boolean;
  pausedBy: string | null;
  pausedAt: string | null;
  reason: string | null;
  fullSyncRunning: boolean;
}

/** Fired after pause / resume so the sidebar's Force Sync button updates right away. */
export const SYNC_CONTROL_EVENT = 'smileos:sync-control';

const announce = (state: SyncControlState) => {
  window.dispatchEvent(new CustomEvent<SyncControlState>(SYNC_CONTROL_EVENT, { detail: state }));
  return state;
};

export const getSyncControl = () => request<SyncControlState>('/api/sync/control');

export const pauseSync = (reason: string) =>
  request<SyncControlState>('/api/sync/control/pause', { method: 'POST', body: JSON.stringify({ reason }) }).then(announce);

export const resumeSync = () =>
  request<SyncControlState>('/api/sync/control/resume', { method: 'POST' }).then(announce);

// ------------------------------------------------------- what each clinic syncs

export interface ClinicSyncSelection {
  clinicCode: string;
  clinicName: string | null;
  active: boolean;
  /** true: everything is synced (resources is empty). */
  everything: boolean;
  resources: string[];
  /** Synced too, because a chosen screen is read through them (e.g. patients for allergies). */
  alsoSynced: string[];
}

export interface SyncSelections {
  syncable: string[];
  clinics: ClinicSyncSelection[];
}

export const getSyncSelections = () => request<SyncSelections>('/api/sync/clinics');

export const saveSyncSelection = (clinicCode: string, everything: boolean, resources: string[]) =>
  request<SyncSelections>(`/api/sync/clinics/${encodeURIComponent(clinicCode)}/resources`, {
    method: 'PUT',
    body: JSON.stringify(everything ? { everything: true } : { everything: false, resources }),
  });

export interface SyncGroup {
  label: string;
  resources: { name: string; label: string }[];
}

/** Display names that the menu doesn't have (tables without a catalog screen). */
const OTHER_LABELS: Record<string, string> = {
  documents: 'Documents (images)',
  subscriptions: 'Subscriptions (webhooks)',
};

/**
 * What can be synced, grouped like the menu. Names the menu doesn't list (documents,
 * webhook subscriptions, ...) go under "Other".
 */
export function groupSyncable(syncable: string[]): SyncGroup[] {
  const left = new Set(syncable);
  const groups: SyncGroup[] = [];
  for (const group of apiNavigationGroups) {
    const resources = (group.resources as readonly string[])
      .filter((name) => left.has(name.toLowerCase()))
      .map((name) => ({ name: name.toLowerCase(), label: formatApiResourceName(name) }));
    resources.forEach((r) => left.delete(r.name));
    if (resources.length) groups.push({ label: group.label, resources });
  }
  if (left.size) {
    groups.push({
      label: 'Other',
      resources: [...left].sort().map((name) => ({ name, label: OTHER_LABELS[name] ?? name.charAt(0).toUpperCase() + name.slice(1) })),
    });
  }
  return groups;
}

/** Display name of a syncable resource. */
export function syncLabel(groups: SyncGroup[], name: string): string {
  for (const group of groups) {
    const found = group.resources.find((r) => r.name === name);
    if (found) return found.label;
  }
  return name;
}

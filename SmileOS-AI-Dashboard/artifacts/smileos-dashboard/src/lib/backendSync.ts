// Force Sync: POST /api/sync/force starts a full copy of Open Dental into Supabase
// in the background; GET /api/sync/force/status reports its progress.
import { request } from './backend';

export interface SyncResourceResult {
  clinic: string;
  resource: string;
  records: number;
  status: 'ok' | 'failed';
  error?: string;
}

export interface SyncStatus {
  state: 'idle' | 'running' | 'completed' | 'completed_with_errors' | 'failed';
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

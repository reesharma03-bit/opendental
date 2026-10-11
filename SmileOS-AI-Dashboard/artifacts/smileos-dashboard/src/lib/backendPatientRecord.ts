// Everything about one patient, read from our database (synced from Open Dental). Each
// section of the patient record loads on its own, so one the user's role can't see
// (403) or one that isn't synced yet doesn't stop the others.
import { pick, request, type Raw } from './backend';

export type Row = Raw;

/** The patient's records of one resource (Open Dental field names). */
export async function listForPatient(resource: string, patNum: number): Promise<Row[]> {
  const rows = await request<Raw[]>(`/api/database/${resource}?${new URLSearchParams({ PatNum: String(patNum), Limit: '500' })}`);
  return Array.isArray(rows) ? rows : [];
}

/** One record by its key, or null when it isn't in our database. */
export async function getRecord(resource: string, key: string | number): Promise<Row | null> {
  try {
    return await request<Raw>(`/api/database/${resource}/${encodeURIComponent(String(key))}`);
  } catch {
    return null;
  }
}

/** Documents are listed live from Open Dental (they live in its image store). */
export async function listDocuments(patNum: number): Promise<Row[]> {
  const rows = await request<Raw[]>(`/api/documents?${new URLSearchParams({ PatNum: String(patNum) })}`);
  return Array.isArray(rows) ? rows : [];
}

/** A field, whatever casing the record uses; Open Dental's "no date" becomes empty. */
export function field(row: Row, ...keys: string[]): string {
  const variants = keys.flatMap((k) => [k, k.charAt(0).toLowerCase() + k.slice(1), toSnake(k)]);
  const value = pick(row, ...variants);
  return value.startsWith('0001-01-01') ? '' : value;
}

function toSnake(key: string) {
  return key.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase();
}

/** Newest first by the first date-like field present. */
export function newestFirst(rows: Row[], ...dateKeys: string[]): Row[] {
  return [...rows].sort((a, b) => field(b, ...dateKeys).localeCompare(field(a, ...dateKeys)));
}

export const formatDate = (value: string) => (value ? value.slice(0, 10) : '');
export const formatDateTime = (value: string) => (value ? value.replace('T', ' ').slice(0, 16) : '');
export const money = (value: string) => {
  const n = Number(value);
  return value === '' || Number.isNaN(n) ? '' : n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};

/** Open Dental procedure status codes, in words. */
export const PROC_STATUS: Record<string, string> = {
  TP: 'Planned', C: 'Completed', EC: 'Existing (current provider)', EO: 'Existing (other provider)',
  R: 'Referred out', D: 'Deleted', Cn: 'Condition', TPi: 'Planned (inactive)',
};

/** Insurance chain for one patient plan: subscriber → plan → carrier. */
export async function insuranceFor(patPlan: Row): Promise<{ sub: Row | null; plan: Row | null; carrier: Row | null }> {
  const sub = await getRecord('inssubs', field(patPlan, 'InsSubNum'));
  const plan = sub ? await getRecord('insplans', field(sub, 'PlanNum')) : null;
  const carrier = plan ? await getRecord('carriers', field(plan, 'CarrierNum')) : null;
  return { sub, plan, carrier };
}

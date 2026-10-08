// Account adjustments (discounts, write-offs, extra charges), read from our database and
// saved there first; the backend then sends them to Open Dental. Open Dental has no delete
// for adjustments. Rules: https://www.opendental.com/site/apiadjustments.html
import { pick, pickNum, request, type Raw } from './backend';

export interface Adjustment {
  adjNum: number;
  patNum: number;
  adjDate: string;
  adjAmt: number;
  adjType: number;
  adjTypeName: string;
  provNum: number;
  procNum: number;
  procDate: string;
  clinicNum: number;
  note: string;
  dateEntry: string;
}

export interface AdjustmentDraft {
  adjDate: string;
  adjAmt: string;
  adjType: string;
  provNum: string;
  procNum: string;
  procDate: string;
  note: string;
}

/** An adjustment type: a definition in Open Dental's "AdjTypes" category (1). */
export interface AdjustmentType { defNum: number; name: string; sign: '+' | '-' }

const date = (value: string) => (value && !value.startsWith('0001') ? value.slice(0, 10) : '');

export function mapAdjustment(raw: Raw): Adjustment {
  return {
    adjNum: pickNum(raw, 'AdjNum') ?? 0,
    patNum: pickNum(raw, 'PatNum') ?? 0,
    adjDate: date(pick(raw, 'AdjDate')),
    adjAmt: pickNum(raw, 'AdjAmt') ?? 0,
    adjType: pickNum(raw, 'AdjType') ?? 0,
    // Open Dental sends the type's name as "adjType" beside its number "AdjType".
    adjTypeName: typeof raw.adjType === 'string' ? raw.adjType : '',
    provNum: pickNum(raw, 'ProvNum') ?? 0,
    procNum: pickNum(raw, 'ProcNum') ?? 0,
    procDate: date(pick(raw, 'ProcDate')),
    clinicNum: pickNum(raw, 'ClinicNum') ?? 0,
    note: pick(raw, 'AdjNote'),
    dateEntry: date(pick(raw, 'DateEntry')),
  };
}

export async function listAdjustments(patNum: number): Promise<Adjustment[]> {
  const rows = await request<Raw[]>(`/api/database/adjustments?${new URLSearchParams({ PatNum: String(patNum), Limit: '500' })}`);
  return rows.map(mapAdjustment).sort((a, b) => b.adjDate.localeCompare(a.adjDate) || b.adjNum - a.adjNum);
}

/** Adjustment types from the synced Definitions (category 1, "+" or "-"), visible ones only. */
export async function listAdjustmentTypes(): Promise<AdjustmentType[]> {
  const rows = await request<Raw[]>('/api/database/definitions?Limit=1000');
  return rows
    .filter((raw) => ['1', 'AdjTypes'].includes(pick(raw, 'Category', 'category')))
    .filter((raw) => pick(raw, 'IsHidden', 'isHidden').toLowerCase() !== 'true')
    .map((raw) => ({ defNum: pickNum(raw, 'DefNum') ?? 0, name: pick(raw, 'ItemName') || `Type ${pick(raw, 'DefNum')}`, sign: pick(raw, 'ItemValue').trim() as '+' | '-' }))
    .filter((t) => t.defNum > 0 && (t.sign === '+' || t.sign === '-'))
    .sort((a, b) => a.name.localeCompare(b.name));
}

function fields(draft: AdjustmentDraft): Record<string, string | number> {
  const body: Record<string, string | number> = {
    AdjDate: draft.adjDate,
    AdjAmt: Number(draft.adjAmt),
    AdjType: Number(draft.adjType),
    AdjNote: draft.note.trim(),
  };
  if (draft.provNum) body.ProvNum = Number(draft.provNum);
  if (draft.procNum) body.ProcNum = Number(draft.procNum);
  return body;
}

export async function createAdjustment(patNum: number, draft: AdjustmentDraft): Promise<Adjustment> {
  const body = { PatNum: patNum, ...fields(draft), ...(draft.procDate ? { ProcDate: draft.procDate } : {}) };
  return mapAdjustment(await request<Raw>('/api/database/adjustments', { method: 'POST', body: JSON.stringify(body) }));
}

/** Sends only what changed (Open Dental can't change the patient or procedure date). */
export async function updateAdjustment(original: Adjustment, draft: AdjustmentDraft): Promise<Adjustment> {
  const before = fields(toDraft(original));
  const changes = Object.fromEntries(Object.entries(fields(draft)).filter(([key, value]) => before[key] !== value));
  if (Object.keys(changes).length === 0) return original;
  return mapAdjustment(await request<Raw>(`/api/database/adjustments/${original.adjNum}`, { method: 'PUT', body: JSON.stringify(changes) }));
}

export function toDraft(adjustment: Adjustment): AdjustmentDraft {
  return {
    adjDate: adjustment.adjDate,
    adjAmt: String(adjustment.adjAmt),
    adjType: adjustment.adjType ? String(adjustment.adjType) : '',
    provNum: adjustment.provNum ? String(adjustment.provNum) : '',
    procNum: adjustment.procNum ? String(adjustment.procNum) : '',
    procDate: adjustment.procDate,
    note: adjustment.note,
  };
}

/** The same checks Open Dental makes, so a mistake shows before saving. */
export function draftProblem(draft: AdjustmentDraft, type: AdjustmentType | undefined, today: string): string {
  if (!draft.adjType) return 'Choose an adjustment type.';
  if (!draft.adjDate) return 'Enter the adjustment date.';
  if (draft.adjDate > today) return 'The adjustment date can’t be in the future.';
  const amount = Number(draft.adjAmt);
  if (!draft.adjAmt.trim() || Number.isNaN(amount)) return 'Enter an amount, e.g. 25.00.';
  if (amount === 0) return 'The amount can’t be zero.';
  if (type?.sign === '-' && amount > 0) return `${type.name} reduces the balance: enter a negative amount (e.g. -${amount}).`;
  if (type?.sign === '+' && amount < 0) return `${type.name} adds to the balance: enter a positive amount (e.g. ${-amount}).`;
  return '';
}

// Practice setup lists for pickers, read from our database (filled by the Open Dental sync).
import { pick, pickNum, request, type Raw } from './backend';

export interface ProviderOption { ProvNum: number; name: string }
export interface OperatoryOption { Op: number; name: string }

const visible = (raw: Raw) => pick(raw, 'IsHidden', 'isHidden').toLowerCase() !== 'true';

export async function listProviders(): Promise<ProviderOption[]> {
  const rows = await request<Raw[]>('/api/database/providers?Limit=1000');
  return rows.filter(visible).map((raw) => {
    const full = `${pick(raw, 'FName', 'fName')} ${pick(raw, 'LName', 'lName')}`.trim();
    return {
      ProvNum: pickNum(raw, 'ProvNum', 'provNum') ?? 0,
      name: full ? `Dr. ${full}` : pick(raw, 'Abbr', 'abbr') || `Provider ${pick(raw, 'ProvNum')}`,
    };
  }).filter((p) => p.ProvNum > 0);
}

export async function listOperatories(): Promise<OperatoryOption[]> {
  const rows = await request<Raw[]>('/api/database/operatories?Limit=1000');
  return rows.filter(visible).map((raw) => ({
    Op: pickNum(raw, 'OperatoryNum', 'operatoryNum') ?? 0,
    name: pick(raw, 'OpName', 'opName') || pick(raw, 'Abbrev', 'abbrev') || `Operatory ${pick(raw, 'OperatoryNum')}`,
  })).filter((o) => o.Op > 0);
}

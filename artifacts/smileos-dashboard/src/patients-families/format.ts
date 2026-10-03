import type { PreviewField, PreviewRecord } from './types';

export function displayValue(field: PreviewField | undefined, v: string | boolean | undefined): string {
  if (field?.type === 'checkbox' || typeof v === 'boolean') return v === true || v === 'true' ? 'Yes' : 'No';
  if (v === undefined || v === '') return 'Not entered';
  const s = String(v);
  if (field?.type === 'date') {
    const d = /^\d{4}-\d{2}-\d{2}$/.test(s) ? new Date(`${s}T00:00:00`) : new Date(NaN);
    return Number.isNaN(d.getTime()) ? s : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(d);
  }
  return s;
}

const ACCENTS = [
  { text: 'text-blue-700', soft: 'bg-blue-50', bar: 'bg-[#315fe7]' },
  { text: 'text-teal-700', soft: 'bg-teal-50', bar: 'bg-teal-600' },
  { text: 'text-violet-700', soft: 'bg-violet-50', bar: 'bg-violet-600' },
  { text: 'text-rose-700', soft: 'bg-rose-50', bar: 'bg-rose-500' },
  { text: 'text-emerald-700', soft: 'bg-emerald-50', bar: 'bg-emerald-600' },
  { text: 'text-sky-700', soft: 'bg-sky-50', bar: 'bg-sky-600' },
];
export function accentFor(resource: string) {
  let h = 0;
  for (const c of resource) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return ACCENTS[h % ACCENTS.length];
}

export function recordSearchText(r: PreviewRecord): string {
  return Object.values(r.values).map(String).join(' ').toLowerCase();
}

export const inputClass =
  'mt-1.5 w-full rounded-xl border border-slate-200 bg-white px-3 text-[12px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70';

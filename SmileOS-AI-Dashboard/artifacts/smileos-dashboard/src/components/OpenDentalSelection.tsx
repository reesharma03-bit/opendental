import { useEffect, useState } from 'react';
import { MonitorCheck } from 'lucide-react';
import { pick, pickNum, request, type Raw } from '../lib/backend';

export interface OpenDentalSelectionItem { workstation: string; patNum: number; name: string; at: string }

/** Which patient is open in Open Dental on each workstation (Open Dental's PatientSelected UI event). */
export async function listOpenDentalSelections(): Promise<OpenDentalSelectionItem[]> {
  return (await request<Raw[]>('/api/patients/selected')).map((raw) => ({
    workstation: pick(raw, 'workstation'), patNum: pickNum(raw, 'pat_num', 'patNum') ?? 0, name: pick(raw, 'name'), at: pick(raw, 'at'),
  }));
}

const POLL_MS = 20_000;

/**
 * Top-bar chip: the patient most recently opened in Open Dental, one click to open them
 * here. Shows nothing until a PatientSelected subscription sends something.
 */
export default function OpenDentalSelection({ onOpen }: { onOpen: (patient: { patNum: number; name: string }) => void }) {
  const [items, setItems] = useState<OpenDentalSelectionItem[]>([]);

  useEffect(() => {
    let live = true;
    const load = () => listOpenDentalSelections().then((rows) => { if (live) setItems(rows); }).catch(() => { if (live) setItems([]); });
    void load();
    const timer = window.setInterval(load, POLL_MS);
    return () => { live = false; window.clearInterval(timer); };
  }, []);

  const latest = items[0];
  if (!latest) return null;
  const others = items.slice(1).map((s) => `${s.workstation}: ${s.name || `#${s.patNum}`}`).join('\n');
  return (
    <button type="button" onClick={() => onOpen({ patNum: latest.patNum, name: latest.name })} data-testid="button-open-dental-selection"
      title={`Open in SmileOS${others ? `\nAlso open in Open Dental:\n${others}` : ''}`}
      className="hidden max-w-[260px] items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-left text-emerald-800 transition hover:bg-emerald-100 lg:flex">
      <MonitorCheck size={15} className="shrink-0" />
      <span className="min-w-0">
        <span className="block truncate text-[11px] font-bold">{latest.name || `Patient #${latest.patNum}`}</span>
        <span className="block truncate text-[9px] font-medium text-emerald-700/80">Open in Open Dental · {latest.workstation}{items.length > 1 ? ` · +${items.length - 1}` : ''}</span>
      </span>
    </button>
  );
}

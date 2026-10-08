import { useEffect, useMemo, useState } from 'react';
import { ChevronDown, Search } from 'lucide-react';
import { listSupabasePatients, type BackendPatient } from '../lib/backendPatients';

export const fieldClass = 'mt-1.5 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-[12px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70';

export function patientName(patient: BackendPatient) {
  return patient.preferred || `${patient.firstName} ${patient.lastName}`.trim() || `Patient #${patient.patNum}`;
}

/** Search box plus patient list, read from our database. Only patients saved in Open Dental can be chosen. */
export default function PatientPicker({
  id, value, onChange, onError,
}: {
  id: string;
  value: number | null;
  onChange: (patNum: number | null, patient?: BackendPatient) => void;
  onError: (message: string) => void;
}) {
  const [patients, setPatients] = useState<BackendPatient[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let live = true;
    listSupabasePatients('')
      .then((rows) => { if (live) setPatients(rows.filter((p) => p.patNum > 0)); })
      .catch((error: Error) => { if (live) onError(`Unable to load patients: ${error.message}`); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [onError]);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return term ? patients.filter((p) => `${patientName(p)} ${p.patNum}`.toLowerCase().includes(term)) : patients;
  }, [patients, search]);
  const selected = patients.find((p) => p.patNum === value);

  return (
    <section className="mb-5 rounded-2xl border border-slate-200/75 bg-white p-5 shadow-[0_2px_10px_rgba(26,49,91,0.025)] sm:p-6" aria-labelledby={`${id}-label`}>
      <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(260px,1fr)] md:items-end">
        <div>
          <label id={`${id}-label`} htmlFor={`${id}-search`} className="text-[12px] font-bold text-slate-700">Find a patient</label>
          <div className="relative mt-1.5">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input id={`${id}-search`} value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search patient name or number"
              data-testid={`input-${id}-search`} className={`${fieldClass} mt-0 pl-9`} />
          </div>
        </div>
        <label className="text-[12px] font-bold text-slate-700" htmlFor={`${id}-select`}>
          Patient
          <span className="relative mt-1.5 block">
            <select id={`${id}-select`} value={value ?? ''} disabled={loading || patients.length === 0} data-testid={`select-${id}`}
              onChange={(e) => {
                const patNum = e.target.value ? Number(e.target.value) : null;
                onChange(patNum, patients.find((p) => p.patNum === patNum));
              }}
              className={`${fieldClass} appearance-none pr-9`}>
              <option value="">{loading ? 'Loading patients…' : patients.length ? 'Choose a patient' : 'No patients available'}</option>
              {filtered.map((p) => <option key={p.patNum} value={p.patNum}>{patientName(p)} · #{p.patNum}</option>)}
            </select>
            <ChevronDown size={15} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-slate-400" />
          </span>
        </label>
      </div>
      {selected && (
        <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-slate-100 pt-4 text-[11px]">
          <span className="font-bold text-slate-800">{patientName(selected)}</span>
          <span className="text-slate-400">Patient #{selected.patNum}</span>
          {selected.chartNumber && <span className="text-slate-400">Chart {selected.chartNumber}</span>}
        </div>
      )}
    </section>
  );
}

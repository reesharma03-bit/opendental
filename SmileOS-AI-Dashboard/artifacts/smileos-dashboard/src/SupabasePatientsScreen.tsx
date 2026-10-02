import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, ChevronLeft, ChevronRight, Mail, MapPin, RefreshCw, Search, Smartphone } from 'lucide-react';
import { backendAvailable } from './lib/backend';
import { listSupabasePatients, type BackendPatient } from './lib/backendPatients';

const PAGE_SIZE = 10;

function formatBirthdate(value: string) {
  if (!value) return '';
  const date = new Date(`${value.slice(0, 10)}T00:00:00`);
  return Number.isNaN(date.getTime())
    ? ''
    : new Intl.DateTimeFormat('en', { year: 'numeric', month: 'short', day: 'numeric' }).format(date);
}

export default function SupabasePatientsScreen({
  search,
  onSearchChange,
}: {
  search: string;
  onSearchChange: (value: string) => void;
}) {
  const [patients, setPatients] = useState<BackendPatient[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [refreshCount, setRefreshCount] = useState(0);

  useEffect(() => {
    if (!backendAvailable()) {
      setPatients([]);
      setError('The patient database service is offline. Live records are unavailable.');
      setLoading(false);
      return;
    }

    let current = true;
    setLoading(true);
    setError('');
    const timer = window.setTimeout(() => {
      listSupabasePatients(search.trim())
        .then((rows) => {
          if (current) setPatients(rows);
        })
        .catch((cause: Error) => {
          if (current) {
            setPatients([]);
            setError(cause.message);
          }
        })
        .finally(() => {
          if (current) setLoading(false);
        });
    }, search.trim() ? 350 : 0);

    return () => {
      current = false;
      window.clearTimeout(timer);
    };
  }, [search, refreshCount]);

  const pageCount = Math.max(1, Math.ceil(patients.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const visiblePatients = useMemo(
    () => patients.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE),
    [patients, currentPage],
  );

  useEffect(() => setPage(0), [search]);
  useEffect(() => {
    if (page !== currentPage) setPage(currentPage);
  }, [page, currentPage]);

  const firstRecord = patients.length ? currentPage * PAGE_SIZE + 1 : 0;
  const lastRecord = Math.min((currentPage + 1) * PAGE_SIZE, patients.length);

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9" data-testid="screen-live-patients">
      <header className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[10px] font-bold uppercase tracking-[1.3px] text-blue-600">
            <Smartphone size={13} /> Practice directory
          </p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]">
            Patients<span className="text-blue-600">.</span>
          </h1>
          <p className="mt-1.5 text-[12px] text-slate-500">Live patient records from your Supabase database.</p>
        </div>
        <button
          type="button"
          onClick={() => setRefreshCount((count) => count + 1)}
          disabled={loading}
          data-testid="button-refresh-patients"
          className="flex h-10 items-center gap-2 self-start rounded-xl border border-slate-200 bg-white px-3 text-[11px] font-semibold text-slate-600 transition hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700 disabled:cursor-wait disabled:opacity-60 sm:self-auto"
        >
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
        </button>
      </header>

      <aside role="note" className="mb-5 flex items-start gap-3 rounded-2xl border border-sky-200/80 bg-sky-50 px-4 py-3.5 text-sky-950">
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-sky-100 text-sky-700">
          <CalendarDays size={16} />
        </span>
        <div>
          <p className="text-[12px] font-bold">Live Supabase data · Read-only</p>
          <p className="mt-0.5 text-[11px] leading-5 text-sky-900/80">
            This directory reads patient records from Supabase. It does not create, edit, or delete patient information.
          </p>
        </div>
      </aside>

      <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]">
        <div className="flex flex-col gap-4 border-b border-slate-100 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="font-[Manrope] text-[15px] font-extrabold tracking-[-.3px] text-slate-800">Patient directory</h2>
              <span className="rounded-md bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700" data-testid="text-patient-count">{patients.length}</span>
            </div>
            <p className="mt-1 text-[10px] text-slate-400">Read-only · refresh to load the latest records</p>
          </div>
          <label className="relative block w-full sm:max-w-[310px]">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              value={search}
              onChange={(event) => { onSearchChange(event.target.value); setPage(0); }}
              placeholder="Search name, ID, email or phone"
              aria-label="Search live patient records"
              data-testid="input-patient-search"
              className="h-10 w-full rounded-xl border border-slate-200 bg-[#fbfcfe] pl-9 pr-9 text-[11px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70"
            />
            {search && (
              <button type="button" onClick={() => { onSearchChange(''); setPage(0); }} aria-label="Clear patient search"
                className="absolute right-2 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100">×</button>
            )}
          </label>
        </div>

        {error && (
          <div role="alert" className="m-5 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-semibold text-rose-700">
            Could not load live Supabase patient records: {error}
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] border-collapse text-left">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50/65 text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">
                <th className="px-5 py-3 sm:px-6">Patient</th>
                <th className="px-3 py-3">Patient ID</th>
                <th className="px-3 py-3">Contact</th>
                <th className="px-3 py-3">Location</th>
                <th className="px-3 py-3">Status</th>
                <th className="px-3 py-3">Clinic</th>
              </tr>
            </thead>
            <tbody>
              {visiblePatients.map((patient, index) => {
                const name = patient.preferred || [patient.firstName, patient.middleInitial, patient.lastName].filter(Boolean).join(' ');
                const initials = `${patient.firstName[0] ?? ''}${patient.lastName[0] ?? ''}`.toUpperCase();
                return (
                  <tr key={patient.recordKey} className="border-b border-slate-100/80 last:border-0 transition hover:bg-slate-50/60" data-testid={`row-live-patient-${patient.recordKey}`}>
                    <td className="px-5 py-3.5 sm:px-6">
                      <div className="flex items-center gap-2.5">
                        <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[10px] font-bold ${['bg-sky-100 text-sky-700', 'bg-violet-100 text-violet-700', 'bg-amber-100 text-amber-700', 'bg-rose-100 text-rose-700', 'bg-emerald-100 text-emerald-700'][index % 5]}`}>{initials || 'P'}</span>
                        <div className="min-w-0">
                          <p className="truncate text-[11px] font-bold text-slate-700">{name || 'Unnamed patient'}</p>
                          <p className="mt-0.5 text-[9px] text-slate-400">{patient.gender || 'Gender not specified'}{patient.birthdate ? ` · ${formatBirthdate(patient.birthdate)}` : ''}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-3 py-3"><span className="rounded-md bg-slate-50 px-2 py-1 font-mono text-[10px] font-semibold text-slate-500">P-{patient.patNum}</span></td>
                    <td className="px-3 py-3">
                      <div className="space-y-1">
                        {(patient.phone || patient.homePhone) && <p className="flex items-center gap-1.5 text-[10px] text-slate-600"><Smartphone size={11} className="text-slate-400" />{patient.phone || patient.homePhone}</p>}
                        {patient.email && <p className="flex items-center gap-1.5 text-[10px] text-slate-500"><Mail size={11} className="text-slate-400" /><span className="max-w-[180px] truncate">{patient.email}</span></p>}
                        {!patient.phone && !patient.homePhone && !patient.email && <span className="text-[10px] text-slate-400">No contact details</span>}
                      </div>
                    </td>
                    <td className="px-3 py-3"><p className="flex items-center gap-1.5 text-[10px] text-slate-500">{patient.city || patient.state ? <MapPin size={11} className="text-slate-400" /> : null}{[patient.city, patient.state].filter(Boolean).join(', ') || '—'}</p></td>
                    <td className="px-3 py-3"><span className={`rounded-full px-2 py-1 text-[9px] font-semibold ${patient.status === 'Patient' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}>{patient.status || 'Unspecified'}</span></td>
                    <td className="px-3 py-3 text-[10px] text-slate-500">{patient.clinicAbbr || '—'}</td>
                  </tr>
                );
              })}
              {visiblePatients.length === 0 && (
                <tr><td colSpan={6} className="px-6 py-12 text-center">
                  <span className="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-400"><Search size={17} /></span>
                  <p className="mt-3 text-[12px] font-semibold text-slate-600">{loading ? 'Loading live patient data…' : error ? 'Live records could not be loaded.' : search.trim() ? 'No matching patients' : 'No patient records found in Supabase.'}</p>
                  <p className="mt-1 text-[10px] text-slate-400">{loading ? 'Please wait while the directory refreshes.' : error ? 'Check the database connection and refresh this directory.' : search.trim() ? 'Try a different name, ID, email or phone number.' : 'Records will appear here when the connected database contains patients.'}</p>
                </td></tr>
              )}
            </tbody>
          </table>
        </div>

        <footer className="flex flex-col gap-3 border-t border-slate-100 px-5 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <span className="text-[10px] text-slate-400" data-testid="text-patient-results-count">
            Showing {patients.length ? firstRecord : 0}–{lastRecord} of {patients.length} matching patients
          </span>
          <nav className="flex items-center justify-center gap-1" aria-label="Patient pages">
            <button type="button" onClick={() => setPage(currentPage - 1)} disabled={currentPage === 0} aria-label="Previous patient page"
              className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2 text-[10px] font-semibold text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"><ChevronLeft size={14} />Previous</button>
            <span className="px-2 text-[10px] text-slate-500">Page {currentPage + 1} of {pageCount}</span>
            <button type="button" onClick={() => setPage(currentPage + 1)} disabled={currentPage >= pageCount - 1} aria-label="Next patient page"
              className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2 text-[10px] font-semibold text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40">Next<ChevronRight size={14} /></button>
          </nav>
          <span className="flex items-center justify-center gap-1.5 text-[9px] font-medium text-slate-400 sm:justify-end"><CalendarDays size={12} />Supabase · read-only</span>
        </footer>
      </section>
    </div>
  );
}
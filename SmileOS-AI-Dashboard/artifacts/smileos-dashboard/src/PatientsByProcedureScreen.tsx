import { useEffect, useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight, FileText, Plus, Search, UsersRound, Filter } from 'lucide-react';
import { listPatientsByProcedure, listProcedureCodes, type BackendPatient } from './lib/backendPatients';
import { backendAvailable } from './lib/backend';
import PatientWorkspace, { type WorkspacePatient } from './PatientWorkspace';
import LoadingPanel from './components/LoadingPanel';

const STAGES = ['All stages', 'Lead', 'Consult Set', 'Presented', 'Accepted', 'In Treatment', 'Completed'];

const stageColor = (stage: string) => {
  switch (stage) {
    case 'Lead': return 'bg-amber-50 text-amber-700 border-amber-100';
    case 'Consult Set': return 'bg-blue-50 text-blue-700 border-blue-100';
    case 'Presented': return 'bg-orange-50 text-orange-700 border-orange-100';
    case 'Accepted': return 'bg-emerald-50 text-emerald-700 border-emerald-100';
    case 'In Treatment': return 'bg-indigo-50 text-indigo-700 border-indigo-100';
    case 'Completed': return 'bg-emerald-100 text-emerald-800 border-emerald-200';
    default: return 'bg-slate-50 text-slate-600 border-slate-100';
  }
};

const demoSource = (name: string) => {
  const sources = ['Instagram', 'Facebook', 'TikTok', 'Phone', 'Existing patient', 'Referral'];
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = (hash << 5) - hash + name.charCodeAt(i);
  return sources[Math.abs(hash) % sources.length];
};

const demoStage = (name: string) => {
  const stages = STAGES.slice(1);
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = (hash << 5) - hash + name.charCodeAt(i);
  return stages[Math.abs(hash) % stages.length];
};

const demoCaseValue = (name: string) => {
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = (hash << 5) - hash + name.charCodeAt(i);
  return 1500 + (Math.abs(hash) % 35000);
};

const demoNextStep = (stage: string) => {
  const steps: Record<string, string[]> = {
    Lead: ['Schedule consultation', 'Send intro packet', 'Call to book'],
    'Consult Set': ['Confirm virtual consultation', 'Send pre-visit forms', 'Verify insurance'],
    Presented: ['Discuss financing options', 'Review treatment proposal', 'Answer treatment questions'],
    Accepted: ['Book scan appointment', 'Confirm crown appointment', 'Schedule procedure'],
    'In Treatment': ['Post-op check-in', 'Review lab try-in', 'Take impressions'],
    Completed: ['Hygiene recall', '6-month checkup', 'Annual exam'],
  };
  const list = steps[stage] ?? ['Follow up'];
  let hash = 0;
  for (let i = 0; i < stage.length; i++) hash = (hash << 5) - hash + stage.charCodeAt(i);
  return list[Math.abs(hash) % list.length];
};

export interface ProcedurePatientRow {
  patNum: number;
  name: string;
  initials: string;
  procedure: string;
  source: string;
  stage: string;
  caseValue: number;
  nextStep: string;
}

function toProcedureRow(patient: BackendPatient): ProcedurePatientRow {
  const name = patient.preferred?.trim() || `${patient.firstName} ${patient.lastName}`;
  return {
    patNum: patient.patNum,
    name,
    initials: `${patient.firstName[0] || ''}${patient.lastName[0] || ''}`.toUpperCase(),
    procedure: patient.procedure || 'All procedures',
    source: demoSource(name),
    stage: demoStage(name),
    caseValue: demoCaseValue(name),
    nextStep: demoNextStep(demoStage(name)),
  };
}

export default function PatientsByProcedureScreen() {
  const PAGE_SIZE = 10;
  const [procedure, setProcedure] = useState<string>('All procedures');
  const [stage, setStage] = useState<string>('All stages');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<ProcedurePatientRow[]>([]);
  const [procedureCodes, setProcedureCodes] = useState<string[]>([]);
  const [openRecord, setOpenRecord] = useState<WorkspacePatient | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [loadProgress, setLoadProgress] = useState(0);
  const [error, setError] = useState('');

  const procCode = procedure === 'All procedures' ? null : procedure;

  // Fetch the list of procedures currently present in Open Dental.
  useEffect(() => {
    if (!backendAvailable()) return;
    listProcedureCodes()
      .then((codes) => setProcedureCodes(codes ?? []))
      .catch(() => setProcedureCodes([]));
  }, []);

  useEffect(() => {
    if (!backendAvailable()) {
      setError('Backend is offline in this build. No patient records can be loaded.');
      return;
    }
    setSyncing(true);
    setLoadProgress(0);
    setPage(0);
    let progress = 0;
    const timer = window.setInterval(() => {
      progress = Math.min(progress + 8, 90);
      setLoadProgress(progress);
    }, 50);
    listPatientsByProcedure(procCode)
      .then((patients) => {
        const next = patients.map((p) => toProcedureRow(p));
        setRows(next);
        setError('');
      })
      .catch((e: Error) => {
        setError(e.message);
        setRows([]);
      })
      .finally(() => {
        window.clearInterval(timer);
        setLoadProgress(100);
        setSyncing(false);
        window.setTimeout(() => setLoadProgress(0), 400);
      });
  }, [procCode]);

  const filtered = useMemo(() => {
    const s = search.trim().toLowerCase();
    const st = stage;
    return rows.filter((r) => {
      if (s && !r.name.toLowerCase().includes(s) && !r.source.toLowerCase().includes(s) && !r.nextStep.toLowerCase().includes(s)) return false;
      if (st !== 'All stages' && r.stage !== st) return false;
      return true;
    });
  }, [rows, search, stage]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  useEffect(() => { if (page !== safePage) setPage(safePage); }, [page, safePage]);
  const visible = filtered.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  const toWorkspacePatient = (row: ProcedurePatientRow): WorkspacePatient => ({
    patNum: row.patNum,
    name: row.name,
    initials: row.initials,
    preferred: '',
    gender: '',
    birthdate: '',
    status: '',
    phone: '',
    email: '',
    address: '',
  });

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9">
      <header className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[10px] font-bold uppercase tracking-[1.3px] text-blue-600">
            <UsersRound size={13} /> Patient Workspace
          </p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]">
            Every patient.<span className="text-blue-600"> A clear next step.</span>
          </h1>
          <p className="mt-1.5 max-w-2xl text-[12px] leading-5 text-slate-500">
            Organized by procedure, connected from lead to completed treatment.
          </p>
        </div>
        <button type="button" className="flex h-10 items-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[11px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] hover:bg-[#244fcf]">
          <Plus size={15} /> Add patient
        </button>
      </header>

      {error && (
        <p role="alert" className="mb-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-medium text-rose-700">
          {error}
        </p>
      )}

      {loadProgress > 0 && (
        <div className="mb-4">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-[10px] font-semibold text-slate-500">Loading patients</span>
            <span className="text-[10px] font-semibold text-blue-600">{loadProgress}%</span>
          </div>
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-full rounded-full bg-gradient-to-r from-blue-500 via-indigo-500 to-blue-600"
              style={{ width: `${Math.min(loadProgress, 100)}%` }}
            />
          </div>
        </div>
      )}

      <section className="mb-3 flex flex-wrap gap-2" data-testid="procedure-chips">
        {['All procedures', ...procedureCodes].map((label) => (
          <button
            key={label}
            type="button"
            onClick={() => setProcedure(label)}
            data-testid={`chip-procedure-${label.replace(/\s/g, '')}`}
            className={`flex items-center gap-1.5 rounded-full border px-3 py-1 text-[10px] font-semibold transition ${
              procedure === label
                ? 'bg-blue-600 border-blue-600 text-white'
                : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50 hover:border-slate-300'
            }`}
          >
            {label}
          </button>
        ))}
      </section>
      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2 sm:items-end">
        <label className="relative block">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search name, source, or next step…"
            aria-label="Search patients"
            className="h-10 w-full rounded-xl border border-slate-200 bg-[#fbfcfe] pl-9 pr-9 text-[11px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70"
          />
        </label>
        <label className="relative block sm:max-w-[220px]">
          <Filter size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <select
            value={stage}
            onChange={(e) => setStage(e.target.value)}
            aria-label="Filter by stage"
            className="h-10 w-full rounded-xl border border-slate-200 bg-white pl-9 pr-9 text-[11px] text-slate-700 outline-none transition focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70 appearance-none"
          >
            {STAGES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
      </div>
      <section className={`overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)] ${syncing && rows.length === 0 ? 'hidden' : ''}`} data-testid="section-patients-by-procedure">
        <div className="flex flex-col gap-4 border-b border-slate-100 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="font-[Manrope] text-[15px] font-extrabold tracking-[-.3px] text-slate-800">
                Patients by procedure
              </h2>
              <span className="rounded-md bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700" data-testid="text-patient-count">
                {visible.length} patients
              </span>
            </div>
            <p className="mt-1 text-[10px] text-slate-400">
              {visible.length === 0 ? 'No patients match the current filters.' : 'Procedure overview from Open Dental.'}
            </p>
          </div>
        </div>

        {syncing && rows.length === 0 && (
          <LoadingPanel title="Loading patients by procedure" steps={['Patient directory', 'Procedure mapping', 'Contact details']} testId="patients-procedure-loading" />
        )}

        <div className="overflow-x-auto">
          <table className="w-full min-w-[980px] border-collapse text-left">
            <thead className="bg-[#fbfcfe] text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">
              <tr>
                <th className="px-5 py-3 sm:px-6">Patient</th>
                <th className="px-3 py-3">Procedure</th>
                <th className="px-3 py-3">Source</th>
                <th className="px-3 py-3">Stage</th>
                <th className="px-3 py-3 text-right">Case value</th>
                <th className="px-3 py-3">Next step</th>
                <th className="sticky right-0 z-20 bg-white px-5 py-3 text-right shadow-[-8px_0_10px_rgba(15,23,42,.04)] sm:px-6">Action</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((row, index) => (
                <tr key={`row-${row.patNum}-${index}`} className="border-b border-slate-100/80 last:border-0 transition hover:bg-slate-50/60" data-testid={`row-procedure-patient-${row.patNum}`}>
                  <td className="px-5 py-3.5 sm:px-6">
                    <div className="flex items-center gap-2.5">
                      <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[10px] font-bold ${['bg-sky-100 text-sky-700','bg-violet-100 text-violet-700','bg-amber-100 text-amber-700','bg-rose-100 text-rose-700','bg-emerald-100 text-emerald-700'][index % 5]}`}>
                        {row.initials}
                      </span>
                      <div className="min-w-0">
                        <p className="truncate text-[11px] font-bold text-slate-700" data-testid={`text-patient-name-${row.patNum}`}>{row.name}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-3 py-3">
                    <span className="text-[11px] font-semibold text-slate-700">{row.procedure}</span>
                  </td>
                  <td className="px-3 py-3">
                    <span className="text-[11px] text-slate-600">{row.source}</span>
                  </td>
                  <td className="px-3 py-3">
                    <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[9px] font-semibold ${stageColor(row.stage)}`}>
                      {row.stage}
                    </span>
                  </td>
                  <td className="px-3 py-3 text-right">
                    <span className="text-[11px] font-semibold text-slate-900 tabular-nums">${row.caseValue.toLocaleString()}</span>
                  </td>
                  <td className="px-3 py-3">
                    <span className="text-[11px] text-slate-600">{row.nextStep}</span>
                  </td>
                  <td className="sticky right-0 z-10 bg-white px-5 py-3 text-right shadow-[-8px_0_10px_rgba(15,23,42,.04)] sm:px-6">
                    <button type="button" onClick={() => { setOpenRecord(toWorkspacePatient(row)); window.scrollTo({ top: 0, behavior: 'smooth' }); }} data-testid={`button-open-record-${row.patNum}`} aria-label={`Open record for ${row.name}`} className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-blue-200 bg-blue-50 px-2.5 text-[10px] font-semibold text-blue-700 transition hover:bg-blue-100">
                      <FileText size={12} /> View
                    </button>
                  </td>
                </tr>
              ))}
              {visible.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center" data-testid="empty-patient-results">
                    <span className="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-400">
                      <Search size={17} />
                    </span>
                    <p className="mt-3 text-[12px] font-semibold text-slate-600">No matching patients</p>
                    <p className="mt-1 text-[10px] text-slate-400">Try a different procedure, stage, or search term.</p>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {visible.length > 0 && (
        <footer className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 px-5 py-3 text-[10px]">
          <span className="text-slate-500">
            Showing{' '}
            <span className="font-semibold text-slate-700">
              {Math.min(safePage * PAGE_SIZE + 1, filtered.length)} –{' '}
              {Math.min((safePage + 1) * PAGE_SIZE, filtered.length)}
            </span>{' '}
            of {filtered.length}
          </span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(p - 1, 0))}
              disabled={safePage === 0}
              data-testid="button-pagination-previous"
              className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2.5 font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <ChevronLeft size={12} /> Previous
            </button>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(p + 1, totalPages - 1))}
              disabled={safePage >= totalPages - 1}
              data-testid="button-pagination-next"
              className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2.5 font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              Next <ChevronRight size={12} />
            </button>
            <span className="ml-2 text-slate-400">
              Page {safePage + 1} of {totalPages}
            </span>
          </div>
        </footer>
      )}

      {openRecord && <PatientWorkspace key={openRecord.patNum} patient={openRecord} onClose={() => setOpenRecord(null)} />}
    </div>
  );
}

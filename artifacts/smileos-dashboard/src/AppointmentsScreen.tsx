import { useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  CalendarDays, Check, ChevronDown, ChevronLeft, ChevronRight, CircleAlert,
  Clock3, Pencil, Plus, Search, ShieldAlert, X,
} from 'lucide-react';

export type AppointmentRecord = {
  AptNum: number;
  PatNum: number;
  AptStatus: 'Scheduled' | 'Complete' | 'UnschedList' | 'Broken' | 'Planned' | 'PtNote' | 'PtNoteCompleted';
  Pattern: string;
  Note: string;
  Op: number;
  ProvNum: number;
  AptDateTime: string;
  IsHygiene: boolean;
  IsNewPatient: boolean;
  Priority: 'Normal' | 'ASAP';
};

type AppointmentDraft = Omit<AppointmentRecord, 'AptNum' | 'AptDateTime'> & { AptDateTime: string };

const createStatuses: AppointmentRecord['AptStatus'][] = [
  'Scheduled', 'Complete', 'UnschedList', 'PtNote', 'PtNoteCompleted',
];
const updateStatuses: AppointmentRecord['AptStatus'][] = [
  'Scheduled', 'Complete', 'UnschedList', 'Broken', 'Planned', 'PtNote', 'PtNoteCompleted',
];
const patients = [
  { PatNum: 1048, name: 'Aarav Mehta' },
  { PatNum: 1032, name: 'Ananya Kapoor' },
  { PatNum: 1009, name: 'Rohan Desai' },
  { PatNum: 988, name: 'Mira Iyer' },
  { PatNum: 974, name: 'Kabir Singh' },
  { PatNum: 956, name: 'Nisha Patel' },
];
const providers = [
  { ProvNum: 1, name: 'Dr. Sharma' },
  { ProvNum: 2, name: 'Dr. Mehta' },
  { ProvNum: 3, name: 'Dr. Rao' },
];
const operatories = [
  { Op: 1, name: 'Operatory 1' },
  { Op: 2, name: 'Operatory 2' },
  { Op: 3, name: 'Hygiene 1' },
];
const appointmentDate = (days: number, hour: number, minute: number) => {
  const date = new Date();
  date.setDate(date.getDate() + days);
  date.setHours(hour, minute, 0, 0);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00`;
};
const initialAppointments: AppointmentRecord[] = [
  { AptNum: 4801, PatNum: 1048, AptStatus: 'Scheduled', Pattern: '/XX/', Note: 'Routine cleaning', Op: 3, ProvNum: 1, AptDateTime: appointmentDate(0, 9, 0), IsHygiene: true, IsNewPatient: false, Priority: 'Normal' },
  { AptNum: 4802, PatNum: 956, AptStatus: 'Scheduled', Pattern: '/XX/', Note: 'Dental consultation', Op: 1, ProvNum: 1, AptDateTime: appointmentDate(0, 9, 45), IsHygiene: false, IsNewPatient: false, Priority: 'Normal' },
  { AptNum: 4803, PatNum: 1032, AptStatus: 'Planned', Pattern: '/XXXX/', Note: 'Root canal therapy', Op: 2, ProvNum: 2, AptDateTime: appointmentDate(0, 10, 30), IsHygiene: false, IsNewPatient: false, Priority: 'ASAP' },
  { AptNum: 4804, PatNum: 1009, AptStatus: 'Scheduled', Pattern: '/XX/', Note: 'New patient exam', Op: 1, ProvNum: 1, AptDateTime: appointmentDate(0, 11, 15), IsHygiene: false, IsNewPatient: true, Priority: 'Normal' },
  { AptNum: 4805, PatNum: 974, AptStatus: 'Complete', Pattern: '/XXX/', Note: 'Crown fitting', Op: 2, ProvNum: 2, AptDateTime: appointmentDate(0, 13, 0), IsHygiene: false, IsNewPatient: false, Priority: 'Normal' },
  { AptNum: 4806, PatNum: 988, AptStatus: 'Scheduled', Pattern: '/XX/', Note: 'Whitening consultation', Op: 1, ProvNum: 3, AptDateTime: appointmentDate(1, 9, 30), IsHygiene: false, IsNewPatient: false, Priority: 'Normal' },
  { AptNum: 4807, PatNum: 1048, AptStatus: 'PtNote', Pattern: '/XX/', Note: 'Review sensitivity', Op: 2, ProvNum: 2, AptDateTime: appointmentDate(1, 11, 0), IsHygiene: false, IsNewPatient: false, Priority: 'Normal' },
  { AptNum: 4808, PatNum: 956, AptStatus: 'Scheduled', Pattern: '/XXX/', Note: 'Follow-up visit', Op: 1, ProvNum: 1, AptDateTime: appointmentDate(2, 14, 0), IsHygiene: false, IsNewPatient: false, Priority: 'Normal' },
];

const emptyDraft = (): AppointmentDraft => ({
  PatNum: 0, AptStatus: 'Scheduled', Pattern: '/XX/', Note: '', Op: 0,
  ProvNum: 1, AptDateTime: '', IsHygiene: false, IsNewPatient: false, Priority: 'Normal',
});
const inputClass = 'mt-1.5 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-[12px] text-slate-700 outline-none transition focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70';
const pageSize = 5;

function toLocalInput(value: string) {
  return value.replace(' ', 'T').slice(0, 16);
}
function toApiDateTime(value: string) {
  return `${value.replace('T', ' ')}:00`;
}
function formatDateTime(value: string) {
  const date = new Date(`${value.replace(' ', 'T')}`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }).format(date);
}

export default function AppointmentsScreen({ search, onSearchChange, announce, createRequest }: {
  search: string;
  onSearchChange: (value: string) => void;
  announce: (message: string) => void;
  createRequest: number;
}) {
  const [records, setRecords] = useState<AppointmentRecord[]>(initialAppointments);
  const [editing, setEditing] = useState<AppointmentRecord | null>(null);
  const [draft, setDraft] = useState<AppointmentDraft>(emptyDraft());
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formOpen, setFormOpen] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);

  const visible = useMemo(() => records.filter((item) => {
    const patient = patients.find((person) => person.PatNum === item.PatNum)?.name || '';
    const provider = providers.find((person) => person.ProvNum === item.ProvNum)?.name || '';
    return `${item.AptNum} ${item.PatNum} ${patient} ${provider} ${item.Op} ${item.Note} ${item.AptStatus} ${item.Priority}`
      .toLowerCase().includes(search.trim().toLowerCase());
  }), [records, search]);
  const pageCount = Math.max(1, Math.ceil(visible.length / pageSize));
  const page = Math.min(currentPage, pageCount);
  const pageRecords = visible.slice((page - 1) * pageSize, page * pageSize);

  useEffect(() => setCurrentPage(1), [search]);
  useEffect(() => {
    if (currentPage > pageCount) setCurrentPage(pageCount);
  }, [currentPage, pageCount]);

  const openCreate = () => {
    setEditing(null);
    setDraft(emptyDraft());
    setErrors({});
    setFormOpen(true);
  };
  const openEdit = (record: AppointmentRecord) => {
    setEditing(record);
    setDraft({ ...record, AptDateTime: toLocalInput(record.AptDateTime) });
    setErrors({});
    setFormOpen(true);
  };
  useEffect(() => {
    if (createRequest > 0) openCreate();
  }, [createRequest]);

  const closeForm = () => {
    setFormOpen(false);
    setErrors({});
    announce('Changes discarded. No appointment record was changed.');
  };
  const saveAppointment = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    if (!draft.PatNum) nextErrors.PatNum = 'Choose a sample patient.';
    if (!draft.Op) nextErrors.Op = 'Choose an operatory.';
    if (!draft.AptDateTime) nextErrors.AptDateTime = 'Appointment date and time are required.';
    else if (Number.isNaN(new Date(draft.AptDateTime).getTime())) nextErrors.AptDateTime = 'Enter a valid appointment date and time.';
    if (!draft.Pattern.trim() || !/^[X/]+$/.test(draft.Pattern)) {
      nextErrors.Pattern = 'Use only X and / characters; each character represents a 5-minute increment.';
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) {
      document.getElementById(Object.keys(nextErrors)[0])?.focus();
      return;
    }
    const normalized = { ...draft, AptDateTime: toApiDateTime(draft.AptDateTime), Pattern: draft.Pattern.trim() };
    if (editing) {
      setRecords((current) => current.map((item) => item.AptNum === editing.AptNum ? { ...normalized, AptNum: editing.AptNum } : item));
      announce(`Appointment ${editing.AptNum} updated in this sample only.`);
    } else {
      const AptNum = Math.max(0, ...records.map((item) => item.AptNum)) + 1;
      setRecords((current) => [{ ...normalized, AptNum }, ...current]);
      setCurrentPage(1);
      announce(`Appointment ${AptNum} added to this sample list.`);
    }
    setFormOpen(false);
  };

  const fieldError = (name: string) => errors[name] && <span id={`${name}-error`} role="alert" className="mt-1 flex items-center gap-1 text-[9px] font-medium text-rose-600">{errors[name]}</span>;
  const fieldClass = (name: string) => `${inputClass} ${errors[name] ? 'border-rose-300 focus:border-rose-300 focus:ring-rose-100' : ''}`;

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9" data-testid="screen-appointments">
      <div className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[11px] font-semibold text-slate-400"><CalendarDays size={13} className="text-blue-500" /> PRACTICE SCHEDULE</p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]" data-testid="text-appointments-title">Appointments<span className="text-blue-600">.</span></h1>
          <p className="mt-1.5 text-[12px] text-slate-500">Browse and manage the local sample schedule.</p>
        </div>
        <button type="button" onClick={openCreate} data-testid="button-create-appointment" className="flex h-10 items-center justify-center gap-2 self-start rounded-xl bg-[#315fe7] px-4 text-[12px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf] sm:self-auto"><Plus size={16} /> New appointment</button>
      </div>

      <div role="note" data-testid="notice-appointments-sample-only" className="mb-5 flex items-start gap-3 rounded-2xl border border-amber-200/80 bg-[#fff8e9] px-4 py-3.5 text-amber-950 sm:items-center">
        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-amber-100 text-amber-700 sm:mt-0"><ShieldAlert size={17} /></span>
        <div className="min-w-0 flex-1"><p className="text-[12px] font-bold">Sample-only · Not connected to OpenDental</p><p className="mt-0.5 text-[11px] leading-5 text-amber-900/75">Search, create, and edit update browser state only. Nothing is sent to OpenDental or saved; all changes reset when you reload.</p></div>
        <span className="hidden rounded-full border border-amber-300/80 px-2.5 py-1 text-[9px] font-bold uppercase tracking-[.8px] text-amber-800 sm:inline-flex">Local demo</span>
      </div>

      <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]" data-testid="section-appointment-directory">
        <div className="flex flex-col gap-4 border-b border-slate-100 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div><div className="flex items-center gap-2"><h2 className="font-[Manrope] text-[15px] font-extrabold tracking-[-.3px] text-slate-800">Schedule records</h2><span className="rounded-md bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700" data-testid="text-appointment-count">{records.length}</span></div><p className="mt-1 text-[10px] text-slate-400">Sample appointments · edits stay in this tab</p></div>
          <label className="relative block w-full sm:max-w-[310px]"><Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" /><input value={search} onChange={(event) => onSearchChange(event.target.value)} placeholder="Search patient, ID, status…" aria-label="Search appointments" data-testid="input-appointment-search" className="h-10 w-full rounded-xl border border-slate-200 bg-[#fbfcfe] pl-9 pr-9 text-[11px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70" />{search && <button type="button" onClick={() => onSearchChange('')} aria-label="Clear appointment search" data-testid="button-clear-appointment-search" className="absolute right-2 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100"><X size={13} /></button>}</label>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[880px] border-collapse text-left">
            <thead><tr className="border-b border-slate-100 bg-slate-50/65 text-[9px] font-bold uppercase tracking-[.8px] text-slate-400"><th className="px-5 py-3 sm:px-6">Appointment</th><th className="px-3 py-3">Patient · PatNum</th><th className="px-3 py-3">Operatory</th><th className="px-3 py-3">Provider</th><th className="px-3 py-3">Status</th><th className="px-3 py-3">Pattern</th><th className="px-5 py-3 text-right sm:px-6">Action</th></tr></thead>
            <tbody>
              {pageRecords.map((record) => {
                const patient = patients.find((person) => person.PatNum === record.PatNum);
                const provider = providers.find((person) => person.ProvNum === record.ProvNum);
                return <tr key={record.AptNum} className="border-b border-slate-100/80 last:border-0 transition hover:bg-slate-50/60" data-testid={`row-appointment-record-${record.AptNum}`}>
                  <td className="px-5 py-3.5 sm:px-6"><div className="flex items-center gap-2"><Clock3 size={13} className="text-blue-500" /><div><p className="text-[11px] font-bold text-slate-700">{formatDateTime(record.AptDateTime)}</p><p className="mt-0.5 text-[9px] text-slate-400">AptNum {record.AptNum}{record.Note ? ` · ${record.Note}` : ''}</p></div></div></td>
                  <td className="px-3 py-3"><p className="text-[11px] font-semibold text-slate-700">{patient?.name || 'Sample patient'}</p><p className="mt-0.5 font-mono text-[9px] text-slate-400">{record.PatNum}</p></td>
                  <td className="px-3 py-3 text-[10px] text-slate-600">{operatories.find((operatory) => operatory.Op === record.Op)?.name || `Operatory ${record.Op}`}</td><td className="px-3 py-3 text-[10px] text-slate-600">{provider?.name || `Provider ${record.ProvNum}`}</td>
                  <td className="px-3 py-3"><span className={`rounded-full px-2 py-1 text-[9px] font-semibold ${record.AptStatus === 'Complete' ? 'bg-emerald-50 text-emerald-700' : 'bg-blue-50 text-blue-700'}`} data-testid={`status-appointment-${record.AptNum}`}>{record.AptStatus}</span>{record.Priority === 'ASAP' && <span className="ml-1 rounded-full bg-rose-50 px-2 py-1 text-[9px] font-semibold text-rose-700">ASAP</span>}</td>
                  <td className="px-3 py-3"><code className="rounded bg-slate-50 px-2 py-1 font-mono text-[10px] text-slate-600">{record.Pattern}</code></td>
                  <td className="px-5 py-3 text-right sm:px-6"><button type="button" onClick={() => openEdit(record)} data-testid={`button-edit-appointment-${record.AptNum}`} aria-label={`Edit appointment ${record.AptNum}`} className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-slate-200 px-2.5 text-[10px] font-semibold text-slate-600 transition hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700"><Pencil size={12} /> Edit</button></td>
                </tr>;
              })}
              {visible.length === 0 && <tr><td colSpan={7} className="px-6 py-12 text-center" data-testid="empty-appointment-results"><span className="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-400"><Search size={17} /></span><p className="mt-3 text-[12px] font-semibold text-slate-600">No matching appointments</p><p className="mt-1 text-[10px] text-slate-400">Try another patient, appointment ID, status or operatory.</p></td></tr>}
            </tbody>
          </table>
        </div>
        <div className="flex flex-col gap-3 border-t border-slate-100 px-5 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <span className="text-[10px] text-slate-400" data-testid="text-appointment-results-count">Showing {visible.length ? (page - 1) * pageSize + 1 : 0}–{Math.min(page * pageSize, visible.length)} of {visible.length} matching appointments</span>
          <nav className="flex items-center justify-center gap-1" aria-label="Appointment pages" data-testid="pagination-appointments">
            <button type="button" onClick={() => setCurrentPage(page - 1)} disabled={page === 1} aria-label="Previous appointment page" data-testid="button-appointments-previous" className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2 text-[10px] font-semibold text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"><ChevronLeft size={14} /><span className="hidden sm:inline">Previous</span></button>
            <span className="px-2 text-[10px] font-semibold text-slate-500" data-testid="text-appointments-page">{page} / {pageCount}</span>
            <button type="button" onClick={() => setCurrentPage(page + 1)} disabled={page === pageCount} aria-label="Next appointment page" data-testid="button-appointments-next" className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2 text-[10px] font-semibold text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"><span className="hidden sm:inline">Next</span><ChevronRight size={14} /></button>
          </nav>
          <span className="flex items-center justify-center gap-1.5 text-[9px] font-medium text-slate-400 sm:justify-end"><CalendarDays size={12} /> Local data · resets on reload</span>
        </div>
      </section>

      {formOpen && <div className="fixed inset-0 z-[90] flex items-end justify-center bg-slate-950/35 p-0 backdrop-blur-[2px] sm:items-center sm:p-5" onMouseDown={(event) => { if (event.target === event.currentTarget) closeForm(); }} data-testid="dialog-appointment-form-overlay">
        <section role="dialog" aria-modal="true" aria-labelledby="appointment-form-title" className="flex max-h-[94dvh] w-full max-w-[760px] flex-col overflow-hidden rounded-t-[24px] bg-[#fbfcfe] shadow-2xl sm:rounded-[24px]" data-testid="dialog-appointment-form">
          <header className="flex items-center justify-between border-b border-slate-200/80 bg-white px-5 py-4 sm:px-7"><div><p className="text-[9px] font-bold uppercase tracking-[1.4px] text-blue-600">Local sample record</p><h2 id="appointment-form-title" className="mt-1 font-[Manrope] text-[18px] font-extrabold tracking-[-.5px] text-slate-900">{editing ? `Edit appointment · ${editing.AptNum}` : 'New appointment'}</h2></div><button type="button" onClick={closeForm} aria-label="Close appointment form" data-testid="button-close-appointment-form" className="flex h-9 w-9 items-center justify-center rounded-xl text-slate-500 transition hover:bg-slate-100"><X size={18} /></button></header>
          <form id="appointment-record-form" onSubmit={saveAppointment} noValidate className="dashboard-scroll overflow-y-auto px-5 py-5 sm:px-7">
            <div className="mb-5 flex items-start gap-2.5 rounded-xl border border-blue-100 bg-blue-50/70 px-3.5 py-3 text-blue-900"><CircleAlert size={15} className="mt-0.5 shrink-0 text-blue-600" /><p className="text-[10px] leading-[1.6]">This form changes only the local sample list. Date and time are entered in your local time zone; no availability check is performed.</p></div>
            <fieldset className="mb-5"><legend className="mb-3 w-full border-b border-slate-200 pb-2 text-[11px] font-bold text-slate-700">Required appointment details</legend>
              <div className="grid grid-cols-1 gap-x-4 gap-y-3 sm:grid-cols-2">
                <label htmlFor="PatNum" className="block text-[10px] font-semibold text-slate-600">Patient · PatNum <span className="text-rose-500" aria-hidden="true">*</span><span className="relative block"><select id="PatNum" name="PatNum" value={draft.PatNum || ''} onChange={(event) => setDraft((current) => ({ ...current, PatNum: Number(event.target.value) }))} aria-invalid={!!errors.PatNum} aria-describedby={errors.PatNum ? 'PatNum-error' : undefined} data-testid="select-appointment-PatNum" className={`${fieldClass('PatNum')} appearance-none pr-9`}><option value="">Select sample patient…</option>{patients.map((patient) => <option key={patient.PatNum} value={patient.PatNum}>{patient.name} · {patient.PatNum}</option>)}</select><ChevronDown size={14} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-slate-400" /></span>{fieldError('PatNum')}</label>
                <label htmlFor="Op" className="block text-[10px] font-semibold text-slate-600">Operatory · Op <span className="text-rose-500" aria-hidden="true">*</span><span className="relative block"><select id="Op" name="Op" value={draft.Op || ''} onChange={(event) => setDraft((current) => ({ ...current, Op: Number(event.target.value) }))} aria-invalid={!!errors.Op} aria-describedby={errors.Op ? 'Op-error' : undefined} data-testid="select-appointment-Op" className={`${fieldClass('Op')} appearance-none pr-9`}><option value="">Select sample operatory…</option>{operatories.map((operatory) => <option key={operatory.Op} value={operatory.Op}>{operatory.name} · {operatory.Op}</option>)}</select><ChevronDown size={14} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-slate-400" /></span>{fieldError('Op')}</label>
                <label htmlFor="AptDateTime" className="block text-[10px] font-semibold text-slate-600">Appointment date &amp; time · AptDateTime <span className="text-rose-500" aria-hidden="true">*</span><input id="AptDateTime" name="AptDateTime" type="datetime-local" value={draft.AptDateTime} onChange={(event) => setDraft((current) => ({ ...current, AptDateTime: event.target.value }))} aria-invalid={!!errors.AptDateTime} aria-describedby={errors.AptDateTime ? 'AptDateTime-error' : undefined} data-testid="input-appointment-AptDateTime" className={fieldClass('AptDateTime')} />{fieldError('AptDateTime')}</label>
                <label htmlFor="Pattern" className="block text-[10px] font-semibold text-slate-600">Pattern <span className="text-rose-500" aria-hidden="true">*</span><input id="Pattern" name="Pattern" value={draft.Pattern} onChange={(event) => setDraft((current) => ({ ...current, Pattern: event.target.value }))} placeholder="/XX/" aria-invalid={!!errors.Pattern} aria-describedby={errors.Pattern ? 'Pattern-error' : 'pattern-help'} data-testid="input-appointment-Pattern" className={fieldClass('Pattern')} /><span id="pattern-help" className="mt-1 block text-[9px] font-normal text-slate-400">Only X and / characters; 5-minute increments.</span>{fieldError('Pattern')}</label>
              </div>
            </fieldset>
            <fieldset className="mb-5"><legend className="mb-3 w-full border-b border-slate-200 pb-2 text-[11px] font-bold text-slate-700">Additional OpenDental fields</legend>
              <div className="grid grid-cols-1 gap-x-4 gap-y-3 sm:grid-cols-2">
                <label htmlFor="AptStatus" className="block text-[10px] font-semibold text-slate-600">AptStatus<select id="AptStatus" name="AptStatus" value={draft.AptStatus} onChange={(event) => setDraft((current) => ({ ...current, AptStatus: event.target.value as AppointmentRecord['AptStatus'] }))} data-testid="select-appointment-AptStatus" className={fieldClass('AptStatus')}>{(editing ? updateStatuses : createStatuses).map((status) => <option key={status} value={status}>{status}</option>)}</select></label>
                <label htmlFor="Priority" className="block text-[10px] font-semibold text-slate-600">Priority<select id="Priority" name="Priority" value={draft.Priority} onChange={(event) => setDraft((current) => ({ ...current, Priority: event.target.value as AppointmentRecord['Priority'] }))} data-testid="select-appointment-Priority" className={fieldClass('Priority')}><option value="Normal">Normal</option><option value="ASAP">ASAP</option></select></label>
                <label htmlFor="ProvNum" className="block text-[10px] font-semibold text-slate-600">Provider · ProvNum<select id="ProvNum" name="ProvNum" value={draft.ProvNum} onChange={(event) => setDraft((current) => ({ ...current, ProvNum: Number(event.target.value) }))} data-testid="select-appointment-ProvNum" className={fieldClass('ProvNum')}>{providers.map((provider) => <option key={provider.ProvNum} value={provider.ProvNum}>{provider.name} · {provider.ProvNum}</option>)}</select></label>
                <label htmlFor="Note" className="block text-[10px] font-semibold text-slate-600">Note<input id="Note" name="Note" value={draft.Note} onChange={(event) => setDraft((current) => ({ ...current, Note: event.target.value }))} data-testid="input-appointment-Note" className={fieldClass('Note')} /></label>
              </div>
              <div className="mt-4 flex flex-wrap gap-5">
                <label className="inline-flex items-center gap-2 text-[10px] font-semibold text-slate-600"><input type="checkbox" checked={draft.IsHygiene} onChange={(event) => setDraft((current) => ({ ...current, IsHygiene: event.target.checked }))} data-testid="checkbox-appointment-IsHygiene" className="h-4 w-4 rounded border-slate-300 accent-blue-600" /> IsHygiene</label>
                <label className="inline-flex items-center gap-2 text-[10px] font-semibold text-slate-600"><input type="checkbox" checked={draft.IsNewPatient} onChange={(event) => setDraft((current) => ({ ...current, IsNewPatient: event.target.checked }))} data-testid="checkbox-appointment-IsNewPatient" className="h-4 w-4 rounded border-slate-300 accent-blue-600" /> IsNewPatient</label>
              </div>
            </fieldset>
          </form>
          <footer className="flex flex-col-reverse gap-2 border-t border-slate-200/80 bg-white px-5 py-4 sm:flex-row sm:justify-between sm:px-7"><span className="flex items-center gap-1.5 text-[9px] leading-4 text-slate-400"><ShieldAlert size={12} className="shrink-0 text-amber-600" />Sample only — no OpenDental calls</span><div className="flex justify-end gap-2"><button type="button" onClick={closeForm} data-testid="button-cancel-appointment" className="h-9 rounded-xl border border-slate-200 px-4 text-[11px] font-semibold text-slate-600 transition hover:bg-slate-50">Cancel</button><button type="submit" form="appointment-record-form" data-testid="button-save-appointment" className="flex h-9 items-center gap-1.5 rounded-xl bg-[#315fe7] px-4 text-[11px] font-bold text-white transition hover:bg-[#244fcf]"><Check size={14} />{editing ? 'Save changes' : 'Create appointment'}</button></div></footer>
        </section>
      </div>}
    </div>
  );
}
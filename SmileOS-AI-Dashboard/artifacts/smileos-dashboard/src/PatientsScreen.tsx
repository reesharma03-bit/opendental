import { useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  ArrowUpRight, CalendarDays, Check, ChevronDown, ChevronLeft, ChevronRight, CircleAlert, Mail,
  MapPin, Pencil, Plus, Search, ShieldAlert, Smartphone,
  Users, X,
} from 'lucide-react';
import { BACKEND_URL, backendAvailable } from './lib/backend';
import { createBackendPatient, listBackendPatients, mapBackendPatient, type BackendPatient } from './lib/backendPatients';

function toRecord(p: BackendPatient): PatientRecord {
  return {
    id: `P-${p.patNum}`,
    FName: p.firstName, LName: p.lastName, MiddleI: p.middleInitial, Preferred: p.preferred,
    PatStatus: p.status || 'Patient', Gender: p.gender, Position: '',
    Birthdate: (p.birthdate || '').slice(0, 10),
    Address: p.address, Address2: p.address2, City: p.city, State: p.state, Zip: p.zip,
    HmPhone: p.homePhone, WkPhone: '', WirelessPhone: p.phone,
    Email: p.email, PreferContactMethod: p.preferContactMethod, TxtMsgOk: 'Unknown',
  };
}

export type PatientRecord = {
  id: string;
  FName: string;
  LName: string;
  MiddleI: string;
  Preferred: string;
  PatStatus: string;
  Gender: string;
  Position: string;
  Birthdate: string;
  Address: string;
  Address2: string;
  City: string;
  State: string;
  Zip: string;
  HmPhone: string;
  WkPhone: string;
  WirelessPhone: string;
  Email: string;
  PreferContactMethod: string;
  TxtMsgOk: string;
};

const emptyPatient = (): Omit<PatientRecord, 'id'> => ({
  FName: '', LName: '', MiddleI: '', Preferred: '', PatStatus: 'Patient',
  Gender: '', Position: '', Birthdate: '', Address: '', Address2: '',
  City: '', State: '', Zip: '', HmPhone: '', WkPhone: '', WirelessPhone: '',
  Email: '', PreferContactMethod: '', TxtMsgOk: 'Unknown',
});

const initialPatients: PatientRecord[] = [
  { ...emptyPatient(), id: 'P-1048', FName: 'Aarav', LName: 'Mehta', Preferred: 'Aarav', PatStatus: 'Patient', Gender: 'Male', Birthdate: '1988-04-12', WirelessPhone: '+91 98765 41048', Email: 'aarav.mehta@example.test', City: 'Mumbai', State: 'MH' },
  { ...emptyPatient(), id: 'P-1032', FName: 'Ananya', LName: 'Kapoor', Preferred: 'Ananya', PatStatus: 'Patient', Gender: 'Female', Birthdate: '1992-09-03', WirelessPhone: '+91 98765 41032', Email: 'ananya.kapoor@example.test', City: 'Mumbai', State: 'MH' },
  { ...emptyPatient(), id: 'P-1009', FName: 'Rohan', LName: 'Desai', Preferred: 'Rohan', PatStatus: 'Patient', Gender: 'Male', Birthdate: '1979-02-21', WirelessPhone: '+91 98765 41009', Email: 'rohan.desai@example.test', City: 'Pune', State: 'MH' },
  { ...emptyPatient(), id: 'P-0988', FName: 'Mira', LName: 'Iyer', Preferred: 'Mira', PatStatus: 'Patient', Gender: 'Female', Birthdate: '1995-11-18', WirelessPhone: '+91 98765 40988', Email: 'mira.iyer@example.test', City: 'Mumbai', State: 'MH' },
  { ...emptyPatient(), id: 'P-0974', FName: 'Kabir', LName: 'Singh', Preferred: 'Kabir', PatStatus: 'Patient', Gender: 'Male', Birthdate: '1984-06-28', WirelessPhone: '+91 98765 40974', Email: 'kabir.singh@example.test', City: 'Navi Mumbai', State: 'MH' },
  { ...emptyPatient(), id: 'P-0956', FName: 'Nisha', LName: 'Patel', Preferred: 'Nisha', PatStatus: 'Patient', Gender: 'Female', Birthdate: '1987-01-14', WirelessPhone: '+91 98765 40956', Email: 'nisha.patel@example.test', City: 'Thane', State: 'MH' },
];

const fieldGroups = [
  {
    title: 'Patient details',
    fields: [
      { name: 'FName', label: 'First name', required: true },
      { name: 'LName', label: 'Last name', required: true },
      { name: 'MiddleI', label: 'Middle initial' },
      { name: 'Preferred', label: 'Preferred name' },
      { name: 'Birthdate', label: 'Date of birth', type: 'date' },
      { name: 'Gender', label: 'Gender', options: ['Male', 'Female', 'Unknown'] },
      { name: 'Position', label: 'Marital status', options: ['Single', 'Married', 'Child', 'Widowed', 'Divorced'] },
      { name: 'PatStatus', label: 'Patient status', options: ['Patient', 'NonPatient', 'Inactive', 'Archived', 'Deceased', 'Prospective'] },
    ],
  },
  {
    title: 'Contact details',
    fields: [
      { name: 'WirelessPhone', label: 'Mobile phone', type: 'tel' },
      { name: 'HmPhone', label: 'Home phone', type: 'tel' },
      { name: 'WkPhone', label: 'Work phone', type: 'tel' },
      { name: 'Email', label: 'Email address', type: 'email' },
      { name: 'PreferContactMethod', label: 'Preferred contact', options: ['None', 'DoNotCall', 'HmPhone', 'WkPhone', 'WirelessPh', 'Email', 'SeeNotes', 'Mail', 'TextMessage'] },
      { name: 'TxtMsgOk', label: 'Text message permission', options: ['Unknown', 'Yes', 'No'] },
      { name: 'Address', label: 'Address' },
      { name: 'Address2', label: 'Address line 2' },
      { name: 'City', label: 'City' },
      { name: 'State', label: 'State / region' },
      { name: 'Zip', label: 'Postal code' },
    ],
  },
];

const inputClass = 'mt-1.5 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-[12px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70';
const PATIENTS_PER_PAGE = 5;

export default function PatientsScreen({ search, onSearchChange, announce, createRequest }: {
  search: string;
  onSearchChange: (value: string) => void;
  announce: (message: string) => void;
  createRequest: number;
}) {
  const [patients, setPatients] = useState<PatientRecord[]>(initialPatients);
  const [backendOn, setBackendOn] = useState(false);
  const [backendError, setBackendError] = useState('');
  const [syncing, setSyncing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<PatientRecord | null>(null);
  const [draft, setDraft] = useState<Omit<PatientRecord, 'id'>>(emptyPatient());
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formOpen, setFormOpen] = useState(false);
  const [savedId, setSavedId] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(1);

  useEffect(() => {
    if (!backendAvailable()) return;
    let live = true;
    setSyncing(true);
    listBackendPatients('')
      .then((rows) => {
        if (!live) return;
        if (rows.length > 0) setPatients(rows.map(toRecord));
        setBackendOn(true);
        setBackendError('');
      })
      .catch((e: Error) => { if (live) setBackendError(e.message); })
      .finally(() => { if (live) setSyncing(false); });
    return () => { live = false; };
  }, []);

  // Debounced server search: after 400ms of idle typing, ask the backend so
  // results beyond the loaded page are found. Falls back silently to local
  // filtering when the backend is unreachable.
  useEffect(() => {
    if (!backendOn || !backendAvailable()) return;
    const term = search.trim();
    if (!term) return;
    const t = window.setTimeout(() => {
      listBackendPatients(term)
        .then((rows) => {
          if (rows.length === 0) return;
          setPatients((current) => {
            const ids = new Set(current.map((p) => p.id));
            const fresh = rows.map(toRecord).filter((p) => !ids.has(p.id));
            return [...fresh, ...current];
          });
          setBackendError('');
        })
        .catch((e: Error) => setBackendError(e.message));
    }, 400);
    return () => window.clearTimeout(t);
  }, [search, backendOn]);
  const visiblePatients = useMemo(() => patients.filter((patient) =>
    `${patient.FName} ${patient.MiddleI} ${patient.LName} ${patient.Preferred} ${patient.id} ${patient.Email} ${patient.WirelessPhone}`
      .toLowerCase().includes(search.trim().toLowerCase())), [patients, search]);
  const pageCount = Math.max(1, Math.ceil(visiblePatients.length / PATIENTS_PER_PAGE));
  const displayedPage = Math.min(currentPage, pageCount);
  const pagePatients = visiblePatients.slice(
    (displayedPage - 1) * PATIENTS_PER_PAGE,
    displayedPage * PATIENTS_PER_PAGE,
  );
  const firstPatientNumber = visiblePatients.length === 0
    ? 0
    : (displayedPage - 1) * PATIENTS_PER_PAGE + 1;
  const lastPatientNumber = Math.min(displayedPage * PATIENTS_PER_PAGE, visiblePatients.length);
  const pageWindowStart = Math.max(1, Math.min(displayedPage - 2, pageCount - 4));
  const pageNumbers = Array.from(
    { length: Math.min(5, pageCount) },
    (_, index) => pageWindowStart + index,
  );

  useEffect(() => {
    setCurrentPage(1);
  }, [search]);

  useEffect(() => {
    if (currentPage > pageCount) setCurrentPage(pageCount);
  }, [currentPage, pageCount]);

  const openCreate = () => {
    setEditing(null);
    setDraft(emptyPatient());
    setErrors({});
    setFormOpen(true);
  };

  const openEdit = (patient: PatientRecord) => {
    const { id: _id, ...values } = patient;
    setEditing(patient);
    setDraft(values);
    setErrors({});
    setFormOpen(true);
  };

  useEffect(() => {
    if (createRequest > 0) openCreate();
  }, [createRequest]);

  const closeForm = () => {
    setFormOpen(false);
    setErrors({});
    announce('Changes discarded. No patient record was changed.');
  };

  const savePatient = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    if (!draft.FName.trim()) nextErrors.FName = 'First name is required.';
    if (!draft.LName.trim()) nextErrors.LName = 'Last name is required.';
    if (draft.Email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(draft.Email.trim())) {
      nextErrors.Email = 'Enter a valid email address.';
    }
    if (draft.Birthdate && new Date(`${draft.Birthdate}T00:00:00`) > new Date()) {
      nextErrors.Birthdate = 'Date of birth cannot be in the future.';
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) {
      document.getElementById(Object.keys(nextErrors)[0])?.focus();
      return;
    }
    const normalized = { ...draft, FName: draft.FName.trim(), LName: draft.LName.trim(), Email: draft.Email.trim() };
    if (editing) {
      setPatients((current) => current.map((patient) => patient.id === editing.id ? { ...normalized, id: editing.id } : patient));
      setSavedId(editing.id);
      announce(`${normalized.FName} ${normalized.LName} updated in this sample only.`);
    } else if (backendOn) {
      setSaving(true);
      try {
        const saved = await createBackendPatient({
          firstName: normalized.FName, lastName: normalized.LName,
          birthdate: normalized.Birthdate, phone: normalized.WirelessPhone,
          middleName: normalized.MiddleI, preferredName: normalized.Preferred,
          gender: normalized.Gender, status: normalized.PatStatus,
          homePhone: normalized.HmPhone, email: normalized.Email,
          address: normalized.Address, address2: normalized.Address2,
          city: normalized.City, state: normalized.State, zip: normalized.Zip,
          preferContactMethod: normalized.PreferContactMethod,
        });
        const record = toRecord(saved);
        setPatients((current) => [record, ...current]);
        setCurrentPage(1);
        setSavedId(record.id);
        setBackendError('');
        announce(`Patient ${record.FName} ${record.LName} created on the server as ${record.id}.`);
      } catch (e) {
        setBackendError((e as Error).message);
        const newId = `P-${String(Math.floor(10000 + Math.random() * 89999))}`;
        setPatients((current) => [{ ...normalized, id: newId }, ...current]);
        setCurrentPage(1);
        setSavedId(newId);
        announce(`Server create failed — ${(e as Error).message}. Saved locally instead.`);
      } finally {
        setSaving(false);
      }
    } else {
      const newId = `P-${String(Math.floor(10000 + Math.random() * 89999))}`;
      setPatients((current) => [{ ...normalized, id: newId }, ...current]);
      setCurrentPage(1);
      setSavedId(newId);
      announce(`${normalized.FName} ${normalized.LName} added to this sample list.`);
    }
    setFormOpen(false);
  };

  const patientName = (patient: PatientRecord) => patient.Preferred.trim() || `${patient.FName} ${patient.LName}`;
  const initials = (patient: PatientRecord) => `${patient.FName[0] || ''}${patient.LName[0] || ''}`.toUpperCase();

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9" data-testid="screen-patients">
      {(syncing || saving || backendError) && (
        <div data-testid="patients-backend-status" className={`mb-4 flex items-center gap-2 rounded-xl border px-4 py-2.5 text-[11px] font-semibold ${backendError ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-sky-200 bg-sky-50 text-sky-700'}`}>
          {syncing ? 'Syncing patients from clinic server…' : saving ? 'Saving patient to clinic server…' : `Clinic server error — ${backendError} (showing sample data).`}
        </div>
      )}
      <div className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[11px] font-semibold text-slate-400"><Users size={13} className="text-blue-500" /> PRACTICE DIRECTORY</p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]" data-testid="text-patients-title">Patients<span className="text-blue-600">.</span></h1>
          <p className="mt-1.5 text-[12px] text-slate-500">A sample directory for exploring the SmileOS workflow.</p>
        </div>
        <button onClick={openCreate} data-testid="button-create-patient" className="flex h-10 items-center justify-center gap-2 self-start rounded-xl bg-[#315fe7] px-4 text-[12px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf] sm:self-auto"><Plus size={16} /> Add patient</button>
      </div>

      <div role="note" data-testid="notice-sample-only" className="mb-5 flex items-start gap-3 rounded-2xl border border-amber-200/80 bg-[#fff8e9] px-4 py-3.5 text-amber-950 sm:items-center">
        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-amber-100 text-amber-700 sm:mt-0"><ShieldAlert size={17} /></span>
        <div className="min-w-0 flex-1"><p className="text-[12px] font-bold">Sample-only · Not connected to OpenDental</p><p className="mt-0.5 text-[11px] leading-5 text-amber-900/75">Search, create, and edit here are local browser state only. Nothing is written to a practice record, and changes reset when you reload.</p></div>
        <span className="hidden rounded-full border border-amber-300/80 px-2.5 py-1 text-[9px] font-bold uppercase tracking-[.8px] text-amber-800 sm:inline-flex">Local demo</span>
      </div>

      <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]" data-testid="section-patient-directory">
        <div className="flex flex-col gap-4 border-b border-slate-100 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div><div className="flex items-center gap-2"><h2 className="font-[Manrope] text-[15px] font-extrabold tracking-[-.3px] text-slate-800">Patient directory</h2><span className="rounded-md bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700" data-testid="text-patient-count">{patients.length}</span></div><p className="mt-1 text-[10px] text-slate-400">Sample records · edits stay in this tab</p></div>
          <label className="relative block w-full sm:max-w-[310px]"><Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" /><input value={search} onChange={(event) => onSearchChange(event.target.value)} placeholder="Search name, ID, email or phone" aria-label="Search patients" data-testid="input-patient-search" className="h-10 w-full rounded-xl border border-slate-200 bg-[#fbfcfe] pl-9 pr-9 text-[11px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70" />{search && <button type="button" onClick={() => onSearchChange('')} aria-label="Clear patient search" data-testid="button-clear-patient-search" className="absolute right-2 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100"><X size={13} /></button>}</label>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] border-collapse text-left">
            <thead><tr className="border-b border-slate-100 bg-slate-50/65 text-[9px] font-bold uppercase tracking-[.8px] text-slate-400"><th className="px-5 py-3 sm:px-6">Patient</th><th className="px-3 py-3">Patient ID</th><th className="px-3 py-3">Contact</th><th className="px-3 py-3">Location</th><th className="px-3 py-3">Status</th><th className="px-5 py-3 text-right sm:px-6">Action</th></tr></thead>
            <tbody>
              {pagePatients.map((patient, index) => <tr key={patient.id} className={`border-b border-slate-100/80 last:border-0 transition hover:bg-slate-50/60 ${savedId === patient.id ? 'bg-emerald-50/30' : ''}`} data-testid={`row-patient-record-${patient.id}`}>
                <td className="px-5 py-3.5 sm:px-6"><div className="flex items-center gap-2.5"><span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[10px] font-bold ${['bg-sky-100 text-sky-700', 'bg-violet-100 text-violet-700', 'bg-amber-100 text-amber-700', 'bg-rose-100 text-rose-700', 'bg-emerald-100 text-emerald-700'][index % 5]}`}>{initials(patient)}</span><div className="min-w-0"><p className="truncate text-[11px] font-bold text-slate-700" data-testid={`text-patient-name-${patient.id}`}>{patientName(patient)}</p><p className="mt-0.5 text-[9px] text-slate-400">{patient.Gender || 'Gender not specified'}{patient.Birthdate ? ` · ${new Intl.DateTimeFormat('en', { year: 'numeric', month: 'short', day: 'numeric' }).format(new Date(`${patient.Birthdate}T00:00:00`))}` : ''}</p></div></div></td>
                <td className="px-3 py-3"><span className="rounded-md bg-slate-50 px-2 py-1 font-mono text-[10px] font-semibold text-slate-500" data-testid={`text-patient-id-${patient.id}`}>{patient.id}</span></td>
                <td className="px-3 py-3"><div className="space-y-1">{patient.WirelessPhone && <p className="flex items-center gap-1.5 text-[10px] text-slate-600"><Smartphone size={11} className="text-slate-400" />{patient.WirelessPhone}</p>}{patient.Email && <p className="flex items-center gap-1.5 text-[10px] text-slate-500"><Mail size={11} className="text-slate-400" /><span className="max-w-[180px] truncate">{patient.Email}</span></p>}{!patient.WirelessPhone && !patient.Email && <span className="text-[10px] text-slate-400">No contact details</span>}</div></td>
                <td className="px-3 py-3"><p className="flex items-center gap-1.5 text-[10px] text-slate-500">{patient.City || patient.State ? <MapPin size={11} className="text-slate-400" /> : null}{[patient.City, patient.State].filter(Boolean).join(', ') || '—'}</p></td>
                <td className="px-3 py-3"><span className={`rounded-full px-2 py-1 text-[9px] font-semibold ${patient.PatStatus === 'Patient' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`} data-testid={`status-patient-${patient.id}`}>{patient.PatStatus || 'Unspecified'}</span></td>
                <td className="px-5 py-3 text-right sm:px-6"><button onClick={() => openEdit(patient)} data-testid={`button-edit-patient-${patient.id}`} aria-label={`Edit ${patientName(patient)}`} className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-slate-200 px-2.5 text-[10px] font-semibold text-slate-600 transition hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700"><Pencil size={12} /> Edit</button></td>
              </tr>)}
              {visiblePatients.length === 0 && <tr><td colSpan={6} className="px-6 py-12 text-center" data-testid="empty-patient-results"><span className="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-400"><Search size={17} /></span><p className="mt-3 text-[12px] font-semibold text-slate-600">No matching patients</p><p className="mt-1 text-[10px] text-slate-400">Try a different name, ID, email or phone number.</p></td></tr>}
            </tbody>
          </table>
        </div>
        <div className="flex flex-col gap-3 border-t border-slate-100 px-5 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <span className="text-[10px] text-slate-400" data-testid="text-patient-results-count">
            Showing {firstPatientNumber}–{lastPatientNumber} of {visiblePatients.length} matching patients
          </span>
          <nav className="flex items-center justify-center gap-1" aria-label="Patient pages" data-testid="pagination-patients">
            <button
              type="button"
              onClick={() => setCurrentPage(displayedPage - 1)}
              disabled={displayedPage === 1}
              aria-label="Previous patient page"
              data-testid="button-patients-previous"
              className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2 text-[10px] font-semibold text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              <ChevronLeft size={14} /><span className="hidden sm:inline">Previous</span>
            </button>
            {pageNumbers.map((pageNumber) => (
              <button
                key={pageNumber}
                type="button"
                onClick={() => setCurrentPage(pageNumber)}
                aria-label={`Go to patient page ${pageNumber}`}
                aria-current={displayedPage === pageNumber ? 'page' : undefined}
                data-testid={`button-patients-page-${pageNumber}`}
                className={`h-8 min-w-8 rounded-lg px-2 text-[10px] font-semibold transition ${displayedPage === pageNumber ? 'bg-blue-600 text-white shadow-sm' : 'text-slate-500 hover:bg-slate-100'}`}
              >
                {pageNumber}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setCurrentPage(displayedPage + 1)}
              disabled={displayedPage === pageCount}
              aria-label="Next patient page"
              data-testid="button-patients-next"
              className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2 text-[10px] font-semibold text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              <span className="hidden sm:inline">Next</span><ChevronRight size={14} />
            </button>
          </nav>
          <span className="flex items-center justify-center gap-1.5 text-[9px] font-medium text-slate-400 sm:justify-end"><CalendarDays size={12} /> Local data · resets on reload</span>
        </div>
      </section>

      {formOpen && <div className="fixed inset-0 z-[90] flex items-end justify-center bg-slate-950/35 p-0 backdrop-blur-[2px] sm:items-center sm:p-5" onMouseDown={(event) => { if (event.target === event.currentTarget) closeForm(); }} data-testid="dialog-patient-form-overlay">
        <section role="dialog" aria-modal="true" aria-labelledby="patient-form-title" className="flex max-h-[94dvh] w-full max-w-[760px] flex-col overflow-hidden rounded-t-[24px] bg-[#fbfcfe] shadow-2xl sm:rounded-[24px]" data-testid="dialog-patient-form">
          <header className="flex items-center justify-between border-b border-slate-200/80 bg-white px-5 py-4 sm:px-7">
            <div><p className="text-[9px] font-bold uppercase tracking-[1.4px] text-blue-600">Local sample record</p><h2 id="patient-form-title" className="mt-1 font-[Manrope] text-[18px] font-extrabold tracking-[-.5px] text-slate-900">{editing ? 'Edit patient' : 'Add patient'}</h2></div>
            <button type="button" onClick={closeForm} aria-label="Close patient form" data-testid="button-close-patient-form" className="flex h-9 w-9 items-center justify-center rounded-xl text-slate-500 transition hover:bg-slate-100"><X size={18} /></button>
          </header>
          <form id="patient-record-form" onSubmit={savePatient} noValidate className="dashboard-scroll overflow-y-auto px-5 py-5 sm:px-7">
            <div className="mb-5 flex items-start gap-2.5 rounded-xl border border-blue-100 bg-blue-50/70 px-3.5 py-3 text-blue-900"><CircleAlert size={15} className="mt-0.5 shrink-0 text-blue-600" /><p className="text-[10px] leading-[1.6]">This form updates only the local sample list. First and last name are required. No SSN is collected.</p></div>
            {fieldGroups.map((group) => <fieldset key={group.title} className="mb-5 last:mb-0">
              <legend className="mb-3 w-full border-b border-slate-200 pb-2 text-[11px] font-bold text-slate-700">{group.title}</legend>
              <div className="grid grid-cols-1 gap-x-4 gap-y-3 sm:grid-cols-2">
                {group.fields.map((field) => <label key={field.name} className="block text-[10px] font-semibold text-slate-600" htmlFor={field.name}>{field.label}{'required' in field && field.required && <span className="ml-1 text-rose-500" aria-hidden="true">*</span>}
                  {'options' in field ? <span className="relative block"><select id={field.name} name={field.name} value={String(draft[field.name as keyof typeof draft] ?? '')} onChange={(event) => setDraft((current) => ({ ...current, [field.name]: event.target.value }))} data-testid={`select-patient-${field.name}`} className={`${inputClass} appearance-none pr-9`}><option value="">Select…</option>{field.options?.map((option) => <option key={option} value={option}>{option}</option>)}</select><ChevronDown size={14} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-slate-400" /></span>
                  : <input id={field.name} name={field.name} type={'type' in field ? field.type : 'text'} value={String(draft[field.name as keyof typeof draft] ?? '')} onChange={(event) => setDraft((current) => ({ ...current, [field.name]: event.target.value }))} required={'required' in field && field.required} aria-invalid={!!errors[field.name]} aria-describedby={errors[field.name] ? `${field.name}-error` : undefined} data-testid={`input-patient-${field.name}`} className={`${inputClass} ${errors[field.name] ? 'border-rose-300 focus:border-rose-300 focus:ring-rose-100' : ''}`} />}
                  {errors[field.name] && <span id={`${field.name}-error`} role="alert" className="mt-1 flex items-center gap-1 text-[9px] font-medium text-rose-600">{errors[field.name]}</span>}
                </label>)}
              </div>
            </fieldset>)}
          </form>
          <footer className="flex flex-col-reverse gap-2 border-t border-slate-200/80 bg-white px-5 py-4 sm:flex-row sm:justify-between sm:px-7">
            <span className="flex items-center gap-1.5 text-[9px] leading-4 text-slate-400"><ShieldAlert size={12} className="shrink-0 text-amber-600" />Sample only — not connected to OpenDental</span>
            <div className="flex justify-end gap-2"><button type="button" onClick={closeForm} data-testid="button-cancel-patient" className="h-9 rounded-xl border border-slate-200 px-4 text-[11px] font-semibold text-slate-600 transition hover:bg-slate-50">Cancel</button><button type="submit" form="patient-record-form" data-testid="button-save-patient" className="flex h-9 items-center gap-1.5 rounded-xl bg-[#315fe7] px-4 text-[11px] font-bold text-white transition hover:bg-[#244fcf]"><Check size={14} />{editing ? 'Save changes' : 'Create patient'}<ArrowUpRight size={12} /></button></div>
          </footer>
        </section>
      </div>}
    </div>
  );
}
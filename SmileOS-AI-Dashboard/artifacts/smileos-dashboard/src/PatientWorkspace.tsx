import { useEffect, useState, type ReactNode } from 'react';
import {
  Activity, AlertTriangle, CalendarDays, Check, CircleAlert, FileText, HeartPulse, Image,
  ShieldAlert, Stethoscope, Wallet, X,
} from 'lucide-react';
import {
  field, formatDate, formatDateTime, insuranceFor, listDocuments, listForPatient, money, newestFirst,
  PROC_STATUS, type Row,
} from './lib/backendPatientRecord';
import { formatMoney, getAging, type Aging } from './lib/backendAccountModules';
import SectionLoading from './components/SectionLoading';

/** The parts of a patient the directory already holds (shown at once, before anything loads). */
export interface WorkspacePatient {
  patNum: number;
  name: string;
  initials: string;
  preferred?: string;
  gender?: string;
  birthdate?: string;
  status?: string;
  phone?: string;
  email?: string;
  address?: string;
}

const tabs = [
  { id: 'profile', label: 'Profile', icon: Stethoscope },
  { id: 'timeline', label: 'Timeline', icon: CalendarDays },
  { id: 'history', label: 'Medical history', icon: HeartPulse },
  { id: 'plans', label: 'Treatment plans', icon: FileText },
  { id: 'notes', label: 'Clinical notes', icon: FileText },
  { id: 'images', label: 'Images', icon: Image },
  { id: 'billing', label: 'Billing', icon: Wallet },
  { id: 'insurance', label: 'Insurance', icon: ShieldAlert },
  { id: 'forms', label: 'Forms', icon: Check },
  { id: 'chart', label: 'Dental chart', icon: Activity },
] as const;
type TabId = (typeof tabs)[number]['id'];

/** Universal numbering (Open Dental): upper 1–16 from the patient's right, lower 32–17. */
const UPPER = Array.from({ length: 16 }, (_, i) => String(i + 1));
const LOWER = Array.from({ length: 16 }, (_, i) => String(32 - i));

/** A section's data: loaded once, with its own error (e.g. the user's role can't see it). */
function useSection<T>(load: () => Promise<T>, active: boolean): { data: T | null; error: string; loading: boolean } {
  const [state, setState] = useState<{ data: T | null; error: string; loading: boolean; started: boolean }>(
    { data: null, error: '', loading: false, started: false });
  useEffect(() => {
    if (!active || state.started) return;
    let live = true;
    setState((s) => ({ ...s, loading: true, started: true }));
    load()
      .then((data) => { if (live) setState({ data, error: '', loading: false, started: true }); })
      .catch((e: Error) => {
        if (!live) return;
        const denied = /doesn't allow|403|not allowed|forbidden/i.test(e.message);
        setState({ data: null, error: denied ? 'Your role doesn’t include this section.' : e.message, loading: false, started: true });
      });
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active]);
  return state;
}

/** Real patient record: every section reads this patient's data from our database. */
export default function PatientWorkspace({ patient, onClose }: { patient: WorkspacePatient; onClose: () => void }) {
  const [activeTab, setActiveTab] = useState<TabId>('profile');
  const p = patient.patNum;
  const on = (id: TabId) => activeTab === id;
  const loadingLabel = `Loading ${(tabs.find((t) => t.id === activeTab)?.label ?? 'record').toLowerCase()}`;

  const profile = useSection(async () => ({
    notes: (await listForPatient('patientnotes', p))[0] ?? null,
    popups: await listForPatient('popups', p).catch(() => []),
  }), on('profile'));
  const timeline = useSection(async () => ({
    appointments: newestFirst(await listForPatient('appointments', p), 'AptDateTime'),
    commlogs: newestFirst(await listForPatient('commlogs', p).catch(() => []), 'CommDateTime'),
  }), on('timeline'));
  const history = useSection(async () => ({
    allergies: await listForPatient('allergies', p),
    medications: await listForPatient('medicationpats', p),
    problems: await listForPatient('diseases', p),
    vitals: newestFirst(await listForPatient('vitalsigns', p), 'DateTaken'),
  }), on('history'));
  const plans = useSection(async () => {
    const procedures = await listForPatient('procedurelogs', p);
    return {
      plans: newestFirst(await listForPatient('treatplans', p), 'DateTP'),
      planned: procedures.filter((r) => ['TP', 'TPi'].includes(field(r, 'ProcStatus'))),
    };
  }, on('plans'));
  const notes = useSection(async () => ({
    procNotes: newestFirst(await listForPatient('procnotes', p).catch(() => []), 'EntryDateTime'),
    completed: newestFirst((await listForPatient('procedurelogs', p)).filter((r) => field(r, 'ProcStatus') === 'C'), 'ProcDate'),
  }), on('notes'));
  const images = useSection(() => listDocuments(p).then((rows) => newestFirst(rows, 'DateCreated')), on('images'));
  const billing = useSection(async () => ({
    aging: await getAging(p).catch(() => null as Aging | null),
    payments: newestFirst(await listForPatient('payments', p), 'PayDate'),
    adjustments: newestFirst(await listForPatient('adjustments', p), 'AdjDate'),
    statements: newestFirst(await listForPatient('statements', p).catch(() => []), 'DateSent'),
  }), on('billing'));
  const insurance = useSection(async () => {
    const patPlans = (await listForPatient('patplans', p)).sort((a, b) => Number(field(a, 'Ordinal')) - Number(field(b, 'Ordinal')));
    return Promise.all(patPlans.map(async (pp) => ({ pp, ...(await insuranceFor(pp)) })));
  }, on('insurance'));
  const forms = useSection(() => listForPatient('sheets', p).then((rows) => newestFirst(rows, 'DateTimeSheet')), on('forms'));
  const chart = useSection(async () => ({
    initials: await listForPatient('toothinitials', p).catch(() => []),
    procedures: await listForPatient('procedurelogs', p),
  }), on('chart'));

  return (
    <section className="mb-6 overflow-hidden rounded-xl border border-blue-200 bg-white shadow-[0_8px_24px_rgba(37,99,235,.08)]" data-testid="patient-workspace">
      <header className="flex flex-col gap-3 border-b border-blue-100 bg-blue-50/55 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-blue-100 text-sm font-bold text-blue-800">{patient.initials}</span>
          <div>
            <h2 className="text-lg font-bold tracking-tight text-slate-900">{patient.name}</h2>
            <p className="text-xs text-slate-600">Patient #{p}{patient.status ? ` · ${patient.status}` : ''}{patient.birthdate ? ` · Born ${patient.birthdate}` : ''}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span className="rounded-full border border-blue-200 bg-white px-3 py-1 text-[10px] font-semibold text-blue-800">Live record · synced from Open Dental</span>
          <button type="button" onClick={onClose} aria-label="Close patient record" data-testid="button-close-patient-workspace" className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 hover:bg-white hover:text-slate-800"><X size={17} /></button>
        </div>
      </header>

      <div className="grid min-w-0 lg:grid-cols-[210px_minmax(0,1fr)]">
        <nav className="grid grid-cols-2 gap-1.5 border-b border-slate-200 bg-slate-50/70 p-3 sm:grid-cols-3 lg:grid-cols-1 lg:border-b-0 lg:border-r" aria-label="Patient record sections">
          {tabs.map(({ id, label, icon: Icon }) => (
            <button key={id} type="button" onClick={() => setActiveTab(id)} aria-current={activeTab === id ? 'page' : undefined} data-testid={`button-patient-tab-${id}`}
              className={`flex min-h-10 items-center gap-2 rounded-lg px-3 py-2 text-left text-xs font-semibold transition ${activeTab === id ? 'bg-blue-100 text-blue-900' : 'text-slate-600 hover:bg-white hover:text-slate-900'}`}>
              <Icon size={15} aria-hidden="true" className="shrink-0" /><span>{label}</span>
            </button>
          ))}
        </nav>

        <div className="min-w-0 p-4 sm:p-6">
          <h3 className="mb-4 text-lg font-bold text-slate-900">{tabs.find((t) => t.id === activeTab)?.label}</h3>

          {on('profile') && (
            <Loaded label={loadingLabel} state={profile}>{(d) => (
              <div className="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(240px,.8fr)]">
                <Card title="Details">
                  <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
                    <Item label="Preferred name" value={patient.preferred} />
                    <Item label="Gender" value={patient.gender} />
                    <Item label="Birthdate" value={patient.birthdate} />
                    <Item label="Status" value={patient.status} />
                    <Item label="Phone" value={patient.phone} />
                    <Item label="Email" value={patient.email} />
                    <Item label="Address" value={patient.address} wide />
                  </dl>
                </Card>
                <div className="space-y-4">
                  {d.popups.filter((r) => !field(r, 'DateTimeDisabled')).length > 0 && (
                    <Card title="Alerts" tone="amber">
                      {d.popups.filter((r) => !field(r, 'DateTimeDisabled')).map((r, i) => (
                        <p key={i} className="flex gap-2 text-xs text-amber-900"><AlertTriangle size={13} className="mt-0.5 shrink-0" />{field(r, 'Description')}</p>
                      ))}
                    </Card>
                  )}
                  <Card title="Patient notes">
                    {d.notes ? (
                      <dl className="space-y-2.5">
                        <Item label="Medical" value={field(d.notes, 'Medical')} />
                        <Item label="Medical (comprehensive)" value={field(d.notes, 'MedicalComp')} />
                        <Item label="Treatment" value={field(d.notes, 'Treatment')} />
                        <Item label="Service" value={field(d.notes, 'Service')} />
                        <Item label="Family financial" value={field(d.notes, 'FamFinancial')} />
                        <Item label="Emergency contact" value={[field(d.notes, 'ICEName'), field(d.notes, 'ICEPhone')].filter(Boolean).join(' · ')} />
                      </dl>
                    ) : <Empty text="No patient notes synced." />}
                  </Card>
                </div>
              </div>
            )}</Loaded>
          )}

          {on('timeline') && (
            <Loaded label={loadingLabel} state={timeline}>{(d) => (
              <div className="grid gap-4 xl:grid-cols-2">
                <Card title={`Appointments (${d.appointments.length})`}>
                  <Table rows={d.appointments} empty="No appointments." cols={[
                    ['When', (r) => formatDateTime(field(r, 'AptDateTime'))],
                    ['Status', (r) => field(r, 'AptStatus')],
                    ['Procedures', (r) => field(r, 'ProcDescript')],
                    ['Note', (r) => field(r, 'Note')],
                  ]} />
                </Card>
                <Card title={`Communications (${d.commlogs.length})`}>
                  <Table rows={d.commlogs} empty="No communication logs." cols={[
                    ['When', (r) => formatDateTime(field(r, 'CommDateTime'))],
                    ['How', (r) => field(r, 'Mode_')],
                    ['Note', (r) => field(r, 'Note')],
                  ]} />
                </Card>
              </div>
            )}</Loaded>
          )}

          {on('history') && (
            <Loaded label={loadingLabel} state={history}>{(d) => (
              <div className="grid gap-4 xl:grid-cols-2">
                <Card title={`Allergies (${d.allergies.length})`} tone={d.allergies.some((r) => field(r, 'StatusIsActive') !== 'false') ? 'rose' : undefined}>
                  <Table rows={d.allergies} empty="No allergies recorded." cols={[
                    ['Allergy', (r) => field(r, 'defDescription', 'Description') || `#${field(r, 'AllergyDefNum')}`],
                    ['Reaction', (r) => field(r, 'Reaction')],
                    ['Active', (r) => (field(r, 'StatusIsActive') === 'false' ? 'No' : 'Yes')],
                  ]} />
                </Card>
                <Card title={`Medications (${d.medications.length})`}>
                  <Table rows={d.medications} empty="No medications recorded." cols={[
                    ['Medication', (r) => field(r, 'medName') || `#${field(r, 'MedicationNum')}`],
                    ['Note', (r) => field(r, 'PatNote')],
                    ['Started', (r) => formatDate(field(r, 'DateStart'))],
                    ['Stopped', (r) => formatDate(field(r, 'DateStop'))],
                  ]} />
                </Card>
                <Card title={`Problems (${d.problems.length})`}>
                  <Table rows={d.problems} empty="No problems recorded." cols={[
                    ['Problem', (r) => field(r, 'diseaseDefName') || `#${field(r, 'DiseaseDefNum')}`],
                    ['Status', (r) => field(r, 'ProbStatus')],
                    ['Since', (r) => formatDate(field(r, 'DateStart'))],
                    ['Note', (r) => field(r, 'PatNote')],
                  ]} />
                </Card>
                <Card title="Latest vital signs">
                  {d.vitals[0] ? (
                    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                      <Item label="Taken" value={formatDate(field(d.vitals[0], 'DateTaken'))} />
                      <Item label="Blood pressure" value={[field(d.vitals[0], 'BpSystolic'), field(d.vitals[0], 'BpDiastolic')].every(Boolean) ? `${field(d.vitals[0], 'BpSystolic')}/${field(d.vitals[0], 'BpDiastolic')} mmHg` : ''} />
                      <Item label="Pulse" value={field(d.vitals[0], 'Pulse') && `${field(d.vitals[0], 'Pulse')} bpm`} />
                      <Item label="Height" value={field(d.vitals[0], 'Height') && `${field(d.vitals[0], 'Height')} in`} />
                      <Item label="Weight" value={field(d.vitals[0], 'Weight') && `${field(d.vitals[0], 'Weight')} lb`} />
                    </dl>
                  ) : <Empty text="No vital signs recorded." />}
                </Card>
              </div>
            )}</Loaded>
          )}

          {on('plans') && (
            <Loaded label={loadingLabel} state={plans}>{(d) => (
              <div className="space-y-4">
                <Card title={`Treatment plans (${d.plans.length})`}>
                  <Table rows={d.plans} empty="No treatment plans." cols={[
                    ['Plan', (r) => field(r, 'Heading')],
                    ['Status', (r) => field(r, 'TPStatus')],
                    ['Date', (r) => formatDate(field(r, 'DateTP'))],
                    ['Note', (r) => field(r, 'Note')],
                  ]} />
                </Card>
                <Card title={`Planned procedures (${d.planned.length})`}>
                  <Table rows={d.planned} empty="No planned procedures." cols={[
                    ['Code', (r) => field(r, 'procCode', 'ProcCode')],
                    ['Description', (r) => field(r, 'descript', 'Descript')],
                    ['Tooth', (r) => [field(r, 'ToothNum'), field(r, 'Surf')].filter(Boolean).join(' ')],
                    ['Fee', (r) => money(field(r, 'ProcFee'))],
                    ['Priority', (r) => field(r, 'priority', 'Priority')],
                  ]} />
                </Card>
              </div>
            )}</Loaded>
          )}

          {on('notes') && (
            <Loaded label={loadingLabel} state={notes}>{(d) => (
              <div className="space-y-4">
                <Card title={`Procedure notes (${d.procNotes.length})`}>
                  <Table rows={d.procNotes} empty="No procedure notes synced." cols={[
                    ['When', (r) => formatDateTime(field(r, 'EntryDateTime'))],
                    ['Procedure', (r) => `#${field(r, 'ProcNum')}`],
                    ['Note', (r) => field(r, 'Note')],
                  ]} />
                </Card>
                <Card title={`Completed procedures (${d.completed.length})`}>
                  <Table rows={d.completed} empty="No completed procedures." cols={[
                    ['Date', (r) => formatDate(field(r, 'ProcDate'))],
                    ['Code', (r) => field(r, 'procCode', 'ProcCode')],
                    ['Description', (r) => field(r, 'descript', 'Descript')],
                    ['Tooth', (r) => [field(r, 'ToothNum'), field(r, 'Surf')].filter(Boolean).join(' ')],
                    ['Fee', (r) => money(field(r, 'ProcFee'))],
                  ]} />
                </Card>
              </div>
            )}</Loaded>
          )}

          {on('images') && (
            <Loaded label={loadingLabel} state={images}>{(rows) => (
              <Card title={`Documents and images (${rows.length})`}>
                <Table rows={rows} empty="No documents for this patient." cols={[
                  ['Created', (r) => formatDate(field(r, 'DateCreated'))],
                  ['Description', (r) => field(r, 'Description')],
                  ['Type', (r) => field(r, 'ImgType')],
                  ['Category', (r) => field(r, 'DocCategory')],
                  ['File', (r) => field(r, 'FileName')],
                ]} />
                <p className="mt-3 text-[10px] text-slate-400">Listed live from Open Dental, which keeps the files themselves.</p>
              </Card>
            )}</Loaded>
          )}

          {on('billing') && (
            <Loaded label={loadingLabel} state={billing}>{(d) => (
              <div className="space-y-4">
                {d.aging ? (
                  <div className="grid gap-3 sm:grid-cols-4">
                    <Stat label="Family balance" value={d.aging.total} />
                    <Stat label="Insurance estimate" value={d.aging.insEst} />
                    <Stat label="Patient portion" value={d.aging.patEstBal} strong />
                    <Stat label="Over 90 days" value={d.aging.balOver90} />
                  </div>
                ) : <p className="rounded-lg bg-slate-50 px-3 py-2 text-[11px] text-slate-500">Balance and aging come live from Open Dental and aren’t available right now.</p>}
                <div className="grid gap-4 xl:grid-cols-2">
                  <Card title={`Payments (${d.payments.length})`}>
                    <Table rows={d.payments} empty="No payments." cols={[
                      ['Date', (r) => formatDate(field(r, 'PayDate'))],
                      ['Amount', (r) => money(field(r, 'PayAmt'))],
                      ['Check #', (r) => field(r, 'CheckNum')],
                      ['Note', (r) => field(r, 'PayNote')],
                    ]} />
                  </Card>
                  <Card title={`Adjustments (${d.adjustments.length})`}>
                    <Table rows={d.adjustments} empty="No adjustments." cols={[
                      ['Date', (r) => formatDate(field(r, 'AdjDate'))],
                      ['Type', (r) => (typeof r.adjType === 'string' ? r.adjType : `#${field(r, 'AdjType')}`)],
                      ['Amount', (r) => money(field(r, 'AdjAmt'))],
                      ['Note', (r) => field(r, 'AdjNote')],
                    ]} />
                  </Card>
                </div>
                <Card title={`Statements (${d.statements.length})`}>
                  <Table rows={d.statements} empty="No statements." cols={[
                    ['Sent', (r) => formatDate(field(r, 'DateSent'))],
                    ['Note', (r) => field(r, 'Note')],
                  ]} />
                </Card>
              </div>
            )}</Loaded>
          )}

          {on('insurance') && (
            <Loaded label={loadingLabel} state={insurance}>{(plansFound) => plansFound.length === 0 ? <Empty text="No insurance on file." /> : (
              <div className="grid gap-4 xl:grid-cols-2">
                {plansFound.map(({ pp, sub, plan, carrier }, i) => (
                  <Card key={i} title={`${Number(field(pp, 'Ordinal')) === 1 ? 'Primary' : Number(field(pp, 'Ordinal')) === 2 ? 'Secondary' : `Plan ${field(pp, 'Ordinal')}`} insurance`}>
                    <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
                      <Item label="Carrier" value={carrier ? field(carrier, 'CarrierName') : ''} />
                      <Item label="Carrier phone" value={carrier ? field(carrier, 'Phone') : ''} />
                      <Item label="Group" value={plan ? [field(plan, 'GroupName'), field(plan, 'GroupNum')].filter(Boolean).join(' · ') : ''} />
                      <Item label="Subscriber ID" value={sub ? field(sub, 'SubscriberID') : ''} />
                      <Item label="Relationship" value={field(pp, 'Relationship')} />
                      <Item label="Patient ID" value={field(pp, 'PatID')} />
                      <Item label="Effective" value={sub ? formatDate(field(sub, 'DateEffective')) : ''} />
                      <Item label="Ends" value={sub ? formatDate(field(sub, 'DateTerm')) : ''} />
                    </dl>
                    {!sub && <p className="mt-3 text-[10px] text-slate-400">Subscriber or plan details aren’t synced yet.</p>}
                  </Card>
                ))}
              </div>
            )}</Loaded>
          )}

          {on('forms') && (
            <Loaded label={loadingLabel} state={forms}>{(rows) => (
              <Card title={`Forms (${rows.length})`}>
                <Table rows={rows} empty="No forms for this patient." cols={[
                  ['Date', (r) => formatDateTime(field(r, 'DateTimeSheet'))],
                  ['Form', (r) => field(r, 'Description')],
                  ['Type', (r) => field(r, 'SheetType')],
                ]} />
              </Card>
            )}</Loaded>
          )}

          {on('chart') && (
            <Loaded label={loadingLabel} state={chart}>{(d) => <DentalChart initials={d.initials} procedures={d.procedures} />}</Loaded>
          )}
        </div>
      </div>
    </section>
  );
}

function DentalChart({ initials, procedures }: { initials: Row[]; procedures: Row[] }) {
  const [selected, setSelected] = useState<string | null>(null);
  const missing = new Set(initials.filter((r) => field(r, 'InitialType') === 'Missing').map((r) => field(r, 'ToothNum')));
  const byTooth = new Map<string, Row[]>();
  procedures.forEach((r) => {
    const tooth = field(r, 'ToothNum');
    if (tooth) byTooth.set(tooth, [...(byTooth.get(tooth) ?? []), r]);
  });
  const state = (tooth: string) => {
    if (missing.has(tooth)) return 'missing';
    const statuses = (byTooth.get(tooth) ?? []).map((r) => field(r, 'ProcStatus'));
    if (statuses.includes('TP')) return 'planned';
    if (statuses.some((s) => ['C', 'EC', 'EO'].includes(s))) return 'done';
    return 'none';
  };
  const style: Record<string, string> = {
    missing: 'border-slate-300 bg-slate-200 text-slate-400 line-through',
    planned: 'border-amber-300 bg-amber-50 text-amber-800',
    done: 'border-emerald-300 bg-emerald-50 text-emerald-800',
    none: 'border-slate-200 bg-white text-slate-700',
  };
  const tooth = (t: string) => (
    <button key={t} type="button" onClick={() => setSelected(selected === t ? null : t)} aria-pressed={selected === t} aria-label={`Tooth ${t}`}
      className={`flex h-11 flex-col items-center justify-center rounded-lg border text-xs font-semibold transition ${style[state(t)]} ${selected === t ? 'ring-2 ring-blue-500' : ''}`}>{t}</button>
  );
  const list = selected ? byTooth.get(selected) ?? [] : [];
  return (
    <div className="space-y-4">
      <div className="space-y-2 overflow-x-auto rounded-xl border border-slate-200 p-4">
        <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">Upper</p>
        <div className="grid min-w-[620px] gap-1.5" style={{ gridTemplateColumns: 'repeat(16, minmax(0, 1fr))' }}>{UPPER.map(tooth)}</div>
        <div className="grid min-w-[620px] gap-1.5" style={{ gridTemplateColumns: 'repeat(16, minmax(0, 1fr))' }}>{LOWER.map(tooth)}</div>
        <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">Lower</p>
      </div>
      <div className="flex flex-wrap gap-3 text-[10px] text-slate-600">
        <Legend cls="bg-emerald-50 border-emerald-300" label="Completed / existing work" />
        <Legend cls="bg-amber-50 border-amber-300" label="Planned treatment" />
        <Legend cls="bg-slate-200 border-slate-300" label="Missing" />
      </div>
      {selected && (
        <Card title={`Tooth ${selected}${missing.has(selected) ? ' · missing' : ''}`}>
          <Table rows={list} empty="No procedures on this tooth." cols={[
            ['Date', (r) => formatDate(field(r, 'ProcDate'))],
            ['Status', (r) => PROC_STATUS[field(r, 'ProcStatus')] ?? field(r, 'ProcStatus')],
            ['Code', (r) => field(r, 'procCode', 'ProcCode')],
            ['Surface', (r) => field(r, 'Surf')],
            ['Fee', (r) => money(field(r, 'ProcFee'))],
          ]} />
        </Card>
      )}
    </div>
  );
}

function Loaded<T>({ state, label, children }: { state: { data: T | null; error: string; loading: boolean }; label: string; children: (data: T) => ReactNode }) {
  if (state.loading || (!state.data && !state.error)) {
    return <SectionLoading label={label} />;
  }
  if (state.error) {
    return <div role="alert" className="flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-medium text-rose-700"><CircleAlert size={15} className="mt-0.5 shrink-0" />{state.error}</div>;
  }
  return <>{children(state.data as T)}</>;
}

function Card({ title, children, tone }: { title: string; children: ReactNode; tone?: 'amber' | 'rose' }) {
  const border = tone === 'amber' ? 'border-amber-200 bg-amber-50/40' : tone === 'rose' ? 'border-rose-200' : 'border-slate-200';
  return (
    <section className={`min-w-0 rounded-xl border p-4 ${border}`}>
      <h4 className="mb-3 text-sm font-semibold text-slate-900">{title}</h4>
      {children}
    </section>
  );
}

function Item({ label, value, wide }: { label: string; value?: string; wide?: boolean }) {
  return (
    <div className={wide ? 'sm:col-span-2' : ''}>
      <dt className="text-[11px] font-medium text-slate-500">{label}</dt>
      <dd className="mt-0.5 whitespace-pre-line text-[12px] font-semibold text-slate-800">{value || <span className="font-normal text-slate-400">—</span>}</dd>
    </div>
  );
}

function Table({ rows, cols, empty }: { rows: Row[]; cols: [string, (r: Row) => string][]; empty: string }) {
  if (rows.length === 0) return <Empty text={empty} />;
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[420px] border-collapse text-left">
        <thead><tr className="text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">{cols.map(([label]) => <th key={label} className="px-2 py-1.5">{label}</th>)}</tr></thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-t border-slate-100 text-[11px] text-slate-700">
              {cols.map(([label, get]) => <td key={label} className="max-w-[280px] truncate px-2 py-2" title={get(r)}>{get(r) || <span className="text-slate-300">—</span>}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Empty({ text }: { text: string }) {
  return <p className="py-3 text-[11px] text-slate-500">{text}</p>;
}

function Stat({ label, value, strong }: { label: string; value: number; strong?: boolean }) {
  return (
    <div className="rounded-xl border border-slate-200 px-4 py-3">
      <p className="text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">{label}</p>
      <p className={`mt-1 font-[Manrope] text-[18px] font-extrabold tabular-nums ${strong ? 'text-amber-700' : 'text-slate-900'}`}>{formatMoney(value)}</p>
    </div>
  );
}

function Legend({ cls, label }: { cls: string; label: string }) {
  return <span className="inline-flex items-center gap-1.5"><span className={`h-3 w-3 rounded border ${cls}`} />{label}</span>;
}


import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle, ArrowDownUp, ArrowUpRight, BellRing, Check, CheckCircle2,
  Clock3, Database, ExternalLink, LoaderCircle, Monitor, Pencil, Plus, RefreshCw, Search, Trash2, X,
} from 'lucide-react';
import {
  createSubscription, deleteSubscription, listSubscriptions, updateSubscription,
  type EventKind, type Subscription, type SubscriptionValues,
} from './lib/backendSubscriptions';

const blankSubscription = (): SubscriptionValues => ({
  endpoint: '', workstation: '', eventKind: 'Database', watchTable: 'Appointment',
  uiEventType: '', pollingSeconds: 30, dateTimeStart: new Date().toISOString().slice(0, 10),
  dateTimeStop: '', note: '',
});

/** Watch tables the backend has a webhook route for (/api/webhooks/opendental/{table}). */
const RECEIVED_TABLES = new Set([
  'Appointment', 'AppointmentDeleted', 'Operatory', 'PatField', 'PatFieldDeleted', 'Patient',
  'Provider', 'Schedule', 'ScheduleDeleted', 'ToothInitial', 'ToothInitialDeleted',
]);

const inputClass ='mt-1 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-[12px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70';

function SubscriptionDialog({
  record, onClose, onSave, saving, error,
}: {
  record: Subscription | null;
  onClose: () => void;
  onSave: (values: SubscriptionValues) => void;
  saving: boolean;
  error: string;
}) {
  const [values, setValues] = useState(() => {
    if (!record) return blankSubscription();
    const { id: _id, failureReason: _failure, subsequentFailures: _count, lastFailure: _last, nextRetry: _retry, ...formValues } = record;
    return formValues;
  });
  const set = <K extends keyof typeof values>(key: K, value: (typeof values)[K]) =>
    setValues((current) => ({ ...current, [key]: value }));
  // Open Dental can't change what a subscription watches once it exists (PUT accepts only
  // EndPointUrl, Workstation, PollingSeconds, DateTimeStart, DateTimeStop and Note).
  const locked = Boolean(record);
  const received = values.eventKind === 'Database' && RECEIVED_TABLES.has(values.watchTable);
  const webhookUrl = received ? `${window.location.origin}/api/webhooks/opendental/${values.watchTable.toLowerCase()}` : null;

  return (
    <div className="fixed inset-0 z-[90] flex items-center justify-center overflow-y-auto bg-slate-950/35 p-4 backdrop-blur-[2px]" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section role="dialog" aria-modal="true" aria-labelledby="subscription-dialog-title" className="my-auto max-h-[90dvh] w-full max-w-[600px] overflow-y-auto rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl sm:p-7">
        <header className="flex items-start justify-between gap-4">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-[1.2px] text-blue-600">Saved here first, then in Open Dental</p>
            <h2 id="subscription-dialog-title" className="mt-1 font-[Manrope] text-[20px] font-extrabold text-slate-900">{record ? 'Edit subscription' : 'New subscription'}</h2>
            <p className="mt-1 text-[11px] leading-5 text-slate-500">Choose which Open Dental events are sent to your endpoint. Saving writes to our database and sends the subscription to Open Dental.</p>
          </div>
          <button type="button" aria-label="Close" onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700"><X size={17} /></button>
        </header>

        <form className="mt-5 space-y-4" onSubmit={(event) => { event.preventDefault(); onSave(values); }}>
          <fieldset>
            <legend className="mb-2 text-[11px] font-bold text-slate-700">Event source</legend>
            <div className="grid grid-cols-2 gap-2">
              {(['Database', 'UI'] as const).map((kind) => (
                <button key={kind} type="button" disabled={locked && values.eventKind !== kind} aria-pressed={values.eventKind === kind} onClick={() => set('eventKind', kind)} className={`flex min-h-12 items-center gap-2 rounded-xl border px-3 text-left text-[11px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-40 ${values.eventKind === kind ? 'border-blue-300 bg-blue-50 text-blue-700' : 'border-slate-200 text-slate-500 hover:bg-slate-50'}`}>
                  {kind === 'Database' ? <Database size={15} /> : <Monitor size={15} />}
                  <span>{kind === 'Database' ? 'Database event' : 'UI event'}</span>
                  {values.eventKind === kind && <Check size={14} className="ml-auto" />}
                </button>
              ))}
            </div>
            {locked
              ? <p className="mt-1.5 text-[10px] leading-4 text-slate-400">Open Dental can't change what a subscription watches. To watch something else, remove this subscription and add a new one.</p>
              : values.eventKind === 'UI' && <p className="mt-1.5 text-[10px] leading-4 text-amber-600">UI events are sent by Open Dental on the workstation, usually to a program running on that computer. SmileOS doesn't receive UI events yet.</p>}
          </fieldset>

          <label className="block text-[11px] font-semibold text-slate-700">
            Endpoint URL
            <input className={inputClass} type="url" required value={values.endpoint} onChange={(event) => set('endpoint', event.target.value)} placeholder="https://your-server.example/events" />
            {values.eventKind === 'Database' && !received && (
              <span className="mt-1 block text-[10px] font-normal leading-4 text-amber-600">SmileOS doesn't receive {values.watchTable} events yet; they are picked up by the regular sync instead.</span>
            )}
            {webhookUrl && (
              <span className="mt-1 block text-[10px] font-normal leading-4 text-slate-400">
                To send these events to SmileOS: <code className="break-all text-slate-500">{webhookUrl}</code>{' '}
                <button type="button" onClick={() => set('endpoint', webhookUrl)} className="font-semibold text-blue-600 hover:underline">Use this</button>
              </span>
            )}
          </label>
          <label className="block text-[11px] font-semibold text-slate-700">
            Workstation
            <input className={inputClass} required value={values.workstation} onChange={(event) => set('workstation', event.target.value)} placeholder="Workstation name" />
            <span className="mt-1 block text-[10px] font-normal leading-4 text-slate-400">Open Dental requires a workstation; use “All Workstations” to receive events from all machines.</span>
          </label>

          {values.eventKind === 'Database' ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-[11px] font-semibold text-slate-700">
                Watch table
                <select className={`${inputClass} disabled:bg-slate-50 disabled:text-slate-500`} disabled={locked} value={values.watchTable} onChange={(event) => set('watchTable', event.target.value)}>
                  {[
                    'Appointment', 'AppointmentDeleted', 'LabCase', 'LabCaseDeleted',
                    'MedicationPat', 'MedicationPatDeleted', 'Operatory', 'PatField',
                    'PatFieldDeleted', 'Patient', 'Provider', 'Schedule',
                    'ScheduleDeleted', 'ToothInitial', 'ToothInitialDeleted',
                  ].map((table) => <option key={table}>{table}</option>)}
                </select>
              </label>
              <label className="block text-[11px] font-semibold text-slate-700">
                Polling interval (seconds)
                <input className={inputClass} type="number" min="1" required value={values.pollingSeconds} onChange={(event) => set('pollingSeconds', Number(event.target.value))} />
              </label>
            </div>
          ) : (
            <label className="block text-[11px] font-semibold text-slate-700">
              UI event type
              <select className={`${inputClass} disabled:bg-slate-50 disabled:text-slate-500`} disabled={locked} value={values.uiEventType || 'PatientSelected'} onChange={(event) => set('uiEventType', event.target.value)}>
                <option value="PatientSelected">PatientSelected</option>
              </select>
            </label>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-[11px] font-semibold text-slate-700">Start date<input className={inputClass} type="date" value={values.dateTimeStart} onChange={(event) => set('dateTimeStart', event.target.value)} /></label>
            <label className="block text-[11px] font-semibold text-slate-700">Expiration date <span className="font-normal text-slate-400">(optional)</span><input className={inputClass} type="date" value={values.dateTimeStop} onChange={(event) => set('dateTimeStop', event.target.value)} /></label>
          </div>
          <label className="block text-[11px] font-semibold text-slate-700">
            Note <span className="font-normal text-slate-400">(optional)</span>
            <textarea className="mt-1 min-h-[76px] w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-[12px] text-slate-700 outline-none focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70" value={values.note} onChange={(event) => set('note', event.target.value)} placeholder="Add a note for your team" />
          </label>
          {error && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-3.5 py-2.5 text-[10px] font-semibold text-rose-700">{error}</p>}
          <footer className="flex flex-col-reverse justify-end gap-2 border-t border-slate-100 pt-4 sm:flex-row">
            <button type="button" onClick={onClose} className="h-10 rounded-xl border border-slate-200 px-4 text-[11px] font-semibold text-slate-600 hover:bg-slate-50">Cancel</button>
            <button type="submit" disabled={saving} className="flex h-10 items-center justify-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[11px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] hover:bg-[#244fcf] disabled:opacity-60">{saving && <LoaderCircle size={14} className="animate-spin" />}{saving ? 'Saving…' : record ? 'Save changes' : 'Add subscription'}</button>
          </footer>
        </form>
      </section>
    </div>
  );
}

function Metric({ label, value, detail, icon: Icon, tone }: {
  label: string; value: string; detail: string; icon: typeof BellRing; tone: string;
}) {
  return (
    <section className="rounded-2xl border border-slate-200/75 bg-white p-4 shadow-[0_2px_10px_rgba(26,49,91,0.025)] sm:p-5">
      <div className="flex items-start justify-between gap-2"><p className="text-[11px] font-semibold text-slate-500">{label}</p><span className={`flex h-8 w-8 items-center justify-center rounded-xl ${tone}`}><Icon size={16} /></span></div>
      <p className="mt-3 font-[Manrope] text-[25px] font-extrabold leading-none tracking-[-1px] text-slate-900">{value}</p>
      <p className="mt-2 text-[10px] text-slate-400">{detail}</p>
    </section>
  );
}

export default function SubscriptionScreen() {
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<'All' | EventKind>('All');
  const [dialogRecord, setDialogRecord] = useState<Subscription | null | undefined>(undefined);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setSubscriptions(await listSubscriptions());
      setLoadError('');
    } catch (e) {
      setLoadError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const visible = useMemo(() => subscriptions.filter((item) => {
    const matchesType = filter === 'All' || item.eventKind === filter;
    const term = query.trim().toLowerCase();
    return matchesType && (!term || `${item.endpoint} ${item.workstation} ${item.watchTable} ${item.uiEventType} ${item.note}`.toLowerCase().includes(term));
  }), [filter, query, subscriptions]);
  const selected = visible.find((item) => item.id === selectedId) ?? visible[0] ?? null;
  const failures = subscriptions.filter((item) => item.failureReason).length;
  const active = subscriptions.length - failures;

  const save = async (values: SubscriptionValues) => {
    setSaving(true);
    setSaveError('');
    try {
      const saved = dialogRecord ? await updateSubscription(dialogRecord, values) : await createSubscription(values);
      setSelectedId(saved.id);
      setMessage(saved.id > 0
        ? `Subscription #${saved.id} saved and sent to Open Dental.`
        : 'Subscription saved. It is sent to Open Dental automatically when it is reachable.');
      setDialogRecord(undefined);
      await load();
    } catch (e) {
      setSaveError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id: number) => {
    setSaving(true);
    try {
      await deleteSubscription(id);
      if (selectedId === id) setSelectedId(null);
      setMessage(`Subscription #${id} removed. The delete is sent to Open Dental automatically.`);
      await load();
    } catch (e) {
      setMessage(`Could not remove subscription #${id}: ${(e as Error).message}`);
    } finally {
      setSaving(false);
      setDeleteId(null);
    }
  };

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9" data-testid="screen-subscriptions">
      <header className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[10px] font-bold uppercase tracking-[1.3px] text-blue-700"><BellRing size={13} /> Open Dental API <span className="text-slate-300">/</span> Subscriptions</p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]">Subscriptions<span className="text-blue-600">.</span></h1>
          <p className="mt-1.5 max-w-2xl text-[12px] leading-5 text-slate-500">Open Dental webhook subscriptions: which events Open Dental sends, and where.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <a href="https://www.opendental.com/site/apisubscriptions.html" target="_blank" rel="noreferrer" className="flex h-10 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 text-[10px] font-semibold text-slate-600 hover:bg-slate-50">Open Dental spec <ExternalLink size={13} /></a>
          <button type="button" onClick={() => void load()} disabled={loading} aria-label="Refresh subscriptions" data-testid="button-refresh-subscriptions" className="flex h-10 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 text-[10px] font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-60"><RefreshCw size={13} className={loading ? 'animate-spin' : ''} /> Refresh</button>
          <button type="button" onClick={() => { setSaveError(''); setDialogRecord(null); }} data-testid="button-new-subscription" className="flex h-10 items-center gap-2 rounded-xl bg-[#315fe7] px-3.5 text-[11px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf]"><Plus size={15} /> New subscription</button>
        </div>
      </header>

      <div role="note" className="mb-5 flex items-start gap-2.5 rounded-xl border border-blue-100 bg-blue-50/75 px-4 py-3 text-[11px] leading-5 text-blue-900">
        <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-white text-blue-600"><CheckCircle2 size={13} /></span>
        <p><b>Live data · our database.</b> Subscriptions are copied from Open Dental by the sync (every 15 minutes and on Force Sync). Adding, editing and removing saves here first and sends the change to Open Dental.</p>
      </div>

      {loadError && <div role="alert" className="mb-4 flex items-center gap-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-semibold text-rose-700"><AlertTriangle size={14} className="shrink-0" /><span className="flex-1">Could not load subscriptions — {loadError}</span><button type="button" onClick={() => void load()} className="rounded-lg border border-rose-300 px-3 py-1 hover:bg-rose-100">Retry</button></div>}

      <section className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-3" aria-label="Subscription summary">
        <Metric label="Subscriptions" value={String(subscriptions.length).padStart(2, '0')} detail="In our database" icon={BellRing} tone="bg-blue-50 text-blue-600" />
        <Metric label="Delivering normally" value={String(active).padStart(2, '0')} detail="No delivery failure reported by Open Dental" icon={CheckCircle2} tone="bg-emerald-50 text-emerald-600" />
        <Metric label="Needs review" value={String(failures).padStart(2, '0')} detail="Open Dental reported a delivery failure" icon={AlertTriangle} tone="bg-amber-50 text-amber-600" />
      </section>

      {message && <div role="status" className="mb-4 flex items-start justify-between gap-3 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-[11px] font-semibold leading-5 text-emerald-800"><span>{message}</span><button type="button" onClick={() => setMessage('')} aria-label="Dismiss message"><X size={14} /></button></div>}

      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1.5fr)_minmax(290px,.78fr)]">
        <section className="min-w-0 overflow-hidden rounded-2xl border border-slate-200/75 bg-white">
          <header className="flex flex-col gap-3 border-b border-slate-100 px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-5">
            <div><h2 className="font-[Manrope] text-[14px] font-extrabold text-slate-800">Event subscriptions</h2><p className="mt-1 text-[10px] text-slate-400">Database table polling and Open Dental UI events</p></div>
            <div className="relative w-full sm:max-w-[240px]"><Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" /><input aria-label="Search subscriptions" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search endpoint or event" className={`${inputClass} mt-0 h-9 pl-9 text-[11px]`} /></div>
          </header>
          <div className="flex gap-1 border-b border-slate-100 px-4 pt-3 sm:px-5" role="group" aria-label="Filter subscriptions">
            {(['All', 'Database', 'UI'] as const).map((item) => <button key={item} type="button" aria-pressed={filter === item} onClick={() => setFilter(item)} className={`rounded-t-lg border-b-2 px-3 py-2 text-[10px] font-bold transition ${filter === item ? 'border-blue-600 text-blue-700' : 'border-transparent text-slate-400 hover:text-slate-700'}`}>{item === 'All' ? 'All events' : item === 'Database' ? 'Database' : 'UI events'}</button>)}
            <span className="ml-auto flex items-center gap-1 pb-2 text-[9px] text-slate-400"><ArrowDownUp size={11} /> {visible.length} shown</span>
          </div>
          {visible.length ? (
            <div className="divide-y divide-slate-100">
              {visible.map((item) => {
                const isSelected = selected?.id === item.id;
                return (
                  <button key={item.id} type="button" onClick={() => setSelectedId(item.id)} aria-pressed={isSelected} data-testid={`subscription-row-${item.id}`} className={`flex w-full flex-col gap-3 px-4 py-4 text-left transition sm:flex-row sm:items-center sm:px-5 ${isSelected ? 'bg-blue-50/60' : 'hover:bg-slate-50/70'}`}>
                    <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${item.eventKind === 'Database' ? 'bg-blue-50 text-blue-600' : 'bg-violet-50 text-violet-600'}`}>{item.eventKind === 'Database' ? <Database size={17} /> : <Monitor size={17} />}</span>
                    <span className="min-w-0 flex-1">
                      <span className="flex flex-wrap items-center gap-2">
                        <span className="text-[11px] font-bold text-slate-800">{item.eventKind === 'Database' ? item.watchTable : item.uiEventType}</span>
                        <span className={`rounded-full px-2 py-0.5 text-[9px] font-semibold ${item.failureReason ? 'bg-amber-50 text-amber-700' : 'bg-emerald-50 text-emerald-700'}`}>{item.failureReason ? 'Needs review' : 'Active'}</span>{item.id < 0 && <span className="rounded-full bg-sky-50 px-2 py-0.5 text-[9px] font-semibold text-sky-700">Sending to Open Dental</span>}
                      </span>
                      <span className="mt-1 block truncate text-[10px] text-slate-500">{item.endpoint}</span>
                      <span className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-[9px] text-slate-400"><span>{item.workstation}</span>{item.eventKind === 'Database' && <span className="flex items-center gap-1"><Clock3 size={10} /> Every {item.pollingSeconds}s</span>}</span>
                    </span>
                    <span className="flex shrink-0 items-center justify-between gap-3 sm:flex-col sm:items-end"><span className="text-[9px] font-medium text-slate-400">{item.id > 0 ? `ID #${item.id}` : 'Not yet in Open Dental'}</span><ArrowUpRight size={14} className={`transition ${isSelected ? 'text-blue-600' : 'text-slate-300'}`} /></span>
                  </button>
                );
              })}
            </div>
          ) : (
            <div className="px-6 py-12 text-center"><span className="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-slate-50 text-slate-400">{loading ? <LoaderCircle size={17} className="animate-spin" /> : <Search size={17} />}</span><p className="mt-3 text-[11px] font-semibold text-slate-700">{loading ? 'Loading subscriptions…' : subscriptions.length ? 'No matching subscriptions' : 'No subscriptions in the database yet'}</p><p className="mt-1 text-[10px] text-slate-400">{subscriptions.length ? 'Try another search or event filter.' : 'Use Force Sync to copy them from Open Dental, or add one.'}</p></div>
          )}
          <footer className="border-t border-slate-100 px-4 py-3 text-[9px] text-slate-400 sm:px-5">Shown from our database. Delivery status is as last reported by Open Dental.</footer>
        </section>

        <aside className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white">
          {selected ? (
            <>
              <header className="border-b border-slate-100 px-5 py-4">
                <div className="flex items-start justify-between gap-3"><div><p className="text-[9px] font-bold uppercase tracking-[1px] text-slate-400">{selected.id > 0 ? `Subscription #${selected.id}` : 'New subscription · sending to Open Dental'}</p><h2 className="mt-1 font-[Manrope] text-[15px] font-extrabold text-slate-800">{selected.eventKind === 'Database' ? selected.watchTable : selected.uiEventType} event</h2></div><span className={`mt-1 h-2.5 w-2.5 rounded-full ring-4 ${selected.failureReason ? 'bg-amber-500 ring-amber-100' : 'bg-emerald-500 ring-emerald-100'}`} /></div>
              </header>
              <div className="space-y-4 p-5">
                <div><p className="text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">Event type</p><p className="mt-1 text-[11px] font-semibold text-slate-700">{selected.eventKind === 'Database' ? 'Database event · ' + selected.watchTable : 'UI event · ' + selected.uiEventType}</p></div>
                <div><p className="text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">Endpoint URL</p><p className="mt-1 break-all text-[10px] leading-5 text-slate-600">{selected.endpoint}</p></div>
                <div className="grid grid-cols-2 gap-3"><div><p className="text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">Workstation</p><p className="mt-1 break-words text-[10px] font-medium text-slate-700">{selected.workstation}</p></div>{selected.eventKind === 'Database' && <div><p className="text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">Polling</p><p className="mt-1 text-[10px] font-medium text-slate-700">Every {selected.pollingSeconds}s</p></div>}</div>
                <div className="grid grid-cols-2 gap-3"><div><p className="text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">Start date</p><p className="mt-1 text-[10px] font-medium text-slate-700">{selected.dateTimeStart || 'Not set'}</p></div><div><p className="text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">Expires</p><p className="mt-1 text-[10px] font-medium text-slate-700">{selected.dateTimeStop || 'No expiration'}</p></div></div>
                {selected.note && <div><p className="text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">Note</p><p className="mt-1 text-[10px] leading-5 text-slate-600">{selected.note}</p></div>}
                <div className={`rounded-xl border p-3 ${selected.failureReason ? 'border-amber-200 bg-amber-50/75' : 'border-emerald-100 bg-emerald-50/60'}`}>
                  <p className={`flex items-center gap-1.5 text-[10px] font-bold ${selected.failureReason ? 'text-amber-800' : 'text-emerald-800'}`}>{selected.failureReason ? <AlertTriangle size={13} /> : <CheckCircle2 size={13} />}{selected.failureReason ? 'Delivery failure reported' : 'No delivery failures reported'}</p>
                  {selected.failureReason ? <div className="mt-2 space-y-1 text-[9px] leading-4 text-amber-800"><p>{selected.failureReason}</p><p>{selected.subsequentFailures} subsequent failure{selected.subsequentFailures === 1 ? '' : 's'}{selected.lastFailure ? ` · last ${selected.lastFailure}` : ''}{selected.nextRetry ? ` · next retry ${selected.nextRetry}` : ''}</p></div> : <p className="mt-1 text-[9px] leading-4 text-emerald-700">As last reported by Open Dental.</p>}
                </div>
                <div className="flex gap-2 border-t border-slate-100 pt-4">
                  <button type="button" onClick={() => { setSaveError(''); setDialogRecord(selected); }} className="flex h-9 flex-1 items-center justify-center gap-1.5 rounded-lg border border-slate-200 text-[10px] font-semibold text-slate-600 hover:bg-slate-50"><Pencil size={13} /> Edit</button>
                  <button type="button" onClick={() => setDeleteId(selected.id)} className="flex h-9 flex-1 items-center justify-center gap-1.5 rounded-lg border border-rose-100 text-[10px] font-semibold text-rose-600 hover:bg-rose-50"><Trash2 size={13} /> Remove</button>
                </div>
              </div>
            </>
          ) : (
            <div className="px-5 py-12 text-center"><BellRing size={20} className="mx-auto text-slate-300" /><p className="mt-3 text-[11px] font-semibold text-slate-600">Choose a subscription</p><p className="mt-1 text-[10px] leading-5 text-slate-400">Select an item to review its event and delivery details.</p></div>
          )}
        </aside>
      </div>

      {dialogRecord !== undefined && <SubscriptionDialog record={dialogRecord} onClose={() => setDialogRecord(undefined)} onSave={(values) => void save(values)} saving={saving} error={saveError} />}
      {deleteId !== null && (
        <div className="fixed inset-0 z-[90] flex items-center justify-center bg-slate-950/35 p-4 backdrop-blur-[2px]">
          <section role="alertdialog" aria-modal="true" aria-labelledby="remove-subscription-title" className="w-full max-w-[420px] rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl">
            <h2 id="remove-subscription-title" className="font-[Manrope] text-[16px] font-extrabold text-slate-900">Remove subscription #{deleteId}?</h2>
            <p className="mt-2 text-[11px] leading-5 text-slate-500">It is removed from our database now and from Open Dental straight after. Open Dental stops sending these events.</p>
            <div className="mt-5 flex justify-end gap-2">
              <button type="button" onClick={() => setDeleteId(null)} className="h-9 rounded-lg border border-slate-200 px-3 text-[10px] font-semibold text-slate-600 hover:bg-slate-50">Cancel</button>
              <button type="button" disabled={saving} onClick={() => void remove(deleteId)} className="h-9 rounded-lg bg-rose-600 px-3 text-[10px] font-bold text-white hover:bg-rose-700 disabled:opacity-60">{saving ? 'Removing…' : 'Remove subscription'}</button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
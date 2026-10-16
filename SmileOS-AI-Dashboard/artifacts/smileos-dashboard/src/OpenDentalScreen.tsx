import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import {
  Building2, CheckCircle2, CircleAlert, CircleDashed, KeyRound, LoaderCircle, MonitorSmartphone, PlugZap, Plus, RefreshCw, Unplug,
  Webhook as WebhookIcon,
} from 'lucide-react';
import { CLINIC_EVENT, chooseClinic, chosenClinic, clinicsChanged } from './lib/backend';
import { useAuth } from './auth/AuthContext';
import { createUser, getMe, listUsers, temporaryPassword, type AppUser } from './lib/backendAuth';
import {
  disconnectPractice, eventLabel, getConnection, listClinics, registerClinic, removeClinic, repairWebhooks, saveWorkstation, testConnection,
  findDentist, updateClinic, webhookSummary, type ConnectedClinic, type ConnectionState, type ConnectionTest, type DentistLookup,
  type Webhook,
} from './lib/backendConnection';

const card = 'rounded-2xl border border-slate-200/80 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)] sm:p-6';
const inputCls = 'mt-1.5 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-[12px] text-slate-700 outline-none transition focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70';
const btnPrimary = 'flex h-10 items-center justify-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[12px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf] disabled:opacity-50';
const btnGhost = 'flex h-10 items-center justify-center gap-2 rounded-xl border border-slate-200 px-4 text-[12px] font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-50';
const labelCls = 'block text-[11px] font-semibold text-slate-600';

/**
 * Register a clinic and manage its Open Dental connection. Admins (SmileOS administrators) see
 * every clinic and register any number; a Dentist registers one clinic of their own.
 */
export default function OpenDentalScreen({ startRegister = false }: { startRegister?: boolean }) {
  const [state, setState] = useState<ConnectionState | null>(null);
  const [registering, setRegistering] = useState(startRegister);
  const [editing, setEditing] = useState(false);
  // The clinic's section below the clinic list: switching clinic reloads only it, then scrolls to it.
  const sectionRef = useRef<HTMLDivElement>(null);
  const [scrollToSection, setScrollToSection] = useState(startRegister);
  const editAfterSwitch = useRef(false);
  // Bumped after a change, so the clinic list refreshes its rows (names, statuses) in place.
  const [listVersion, setListVersion] = useState(0);
  useEffect(() => {
    if (startRegister) {
      setRegistering(true);
      setScrollToSection(true);
    }
  }, [startRegister]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setState(await getConnection());
      setError('');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  // A clinic was chosen (here or in the clinic bar): reload this clinic's section only.
  useEffect(() => {
    const onClinic = () => {
      setRegistering(false);
      setEditing(editAfterSwitch.current);
      editAfterSwitch.current = false;
      setNotice('');
      setScrollToSection(true);
      setListVersion((v) => v + 1); // a newly registered clinic appears in the list
      void load();
    };
    window.addEventListener(CLINIC_EVENT, onClinic);
    return () => window.removeEventListener(CLINIC_EVENT, onClinic);
  }, [load]);

  useEffect(() => {
    if (!scrollToSection || loading) return;
    const section = sectionRef.current;
    section?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    // Then the cursor goes into the form's main box (without jumping: the page is still scrolling).
    const box = section?.querySelector<HTMLInputElement>('[data-autofocus]');
    box?.focus({ preventScroll: true });
    setScrollToSection(false);
  }, [scrollToSection, loading, editing, registering]);

  /** Work on another clinic (or open the current one's edit form) without leaving the page. */
  const switchClinic = (code: string, edit: boolean) => {
    if (code === (state?.clinic?.clinicCode ?? chosenClinic())) {
      setRegistering(false);
      setEditing(edit);
      setScrollToSection(true);
      return;
    }
    editAfterSwitch.current = edit;
    chooseClinic(code);
  };

  // Webhooks are set up in the background (about a second per subscription): refresh until done.
  const settingUp = Boolean(state?.settingUp);
  useEffect(() => {
    if (!settingUp) return undefined;
    const timer = window.setInterval(() => {
      getConnection().then(setState).catch(() => undefined);
    }, 3000);
    return () => window.clearInterval(timer);
  }, [settingUp]);

  const done = async (message: string) => {
    setNotice(message);
    setError('');
    setListVersion((v) => v + 1);
    clinicsChanged();
    await load();
  };

  // Disconnected: only the status changes; nothing reloads.
  const { setSignedIn } = useAuth();
  const markDisconnected = (message: string) => {
    setState((s) => (s && s.clinic ? { ...s, clinic: { ...s.clinic, connected: false }, webhooks: [], settingUp: false } : s));
    setNotice(message);
    setError('');
    setListVersion((v) => v + 1);
    clinicsChanged();
    if (!state?.admin) getMe().then(setSignedIn).catch(() => undefined); // the top bar and menu follow
  };
  const markRemoved = (label: string) => {
    if (chosenClinic() === state?.clinic?.clinicCode) chooseClinic(null);
    setNotice(`${label} was removed with its data.`);
    setError('');
    setEditing(false);
    setListVersion((v) => v + 1);
    clinicsChanged();
    if (!state?.admin) getMe().then(setSignedIn).catch(() => undefined);
    void load();
  };
  const startEdit = () => {
    setEditing(true);
    setScrollToSection(true);
  };

  return (
    <div className="mx-auto max-w-[920px] px-4 py-6 sm:px-6 lg:px-9" data-testid="screen-open-dental">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-[Manrope] text-[22px] font-extrabold tracking-[-0.6px] text-slate-900">Open Dental</h1>
          <p className="mt-1 text-[12px] text-slate-500">
            {state?.admin
              ? 'Every clinic on SmileOS. Register new clinics, and manage the Open Dental connection of the one you have chosen.'
              : 'Connect your practice\'s Open Dental to SmileOS. You only need the Customer Key.'}
          </p>
        </div>
        <button type="button" onClick={() => void load()} className="flex h-9 items-center gap-2 rounded-xl px-3 text-[11px] font-semibold text-slate-500 hover:bg-white hover:text-slate-800">
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
        </button>
      </div>

      {error && <p role="alert" className="mt-4 flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[12px] text-rose-700"><CircleAlert size={15} className="mt-0.5 shrink-0" />{error}</p>}
      {notice && <p role="status" className="mt-4 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-[12px] text-emerald-700"><CheckCircle2 size={15} className="mt-0.5 shrink-0" />{notice}</p>}

      {(state?.admin || state?.canRegister) && (
        <AllClinicsCard admin={Boolean(state?.admin)} current={state.clinic?.clinicCode ?? ''} version={listVersion} onSwitch={switchClinic}
          onRegister={() => { setEditing(false); setRegistering(true); setScrollToSection(true); }} />
      )}

      <div ref={sectionRef} className={`scroll-mt-36 transition-opacity ${loading && state ? 'pointer-events-none opacity-50' : ''}`} aria-busy={loading}>
      {!state
        ? <div className="mt-10 flex justify-center text-slate-400">{loading ? <LoaderCircle className="animate-spin" /> : null}</div>
        : registering || (!state.clinic && state.canRegister)
          ? <ConnectCard mode="register" state={state} onDone={done} onError={setError}
              onCancel={state.clinic ? () => setRegistering(false) : undefined} />
          : !state.clinic
            ? <p className={`${card} mt-5 text-[12px] text-slate-600`}>Your account isn't linked to a clinic yet. Ask your SmileOS admin.</p>
            : editing
              ? <ConnectCard key={state.clinic.clinicCode} mode="edit" state={state} onDone={done} onError={setError}
                  onCancel={() => setEditing(false)} />
              : (
                <>
                  <ConnectedCard state={state} onDone={done} onError={setError} onEdit={startEdit} onDisconnected={markDisconnected} onRemoved={markRemoved} />
                  {state.clinic.connected && <WebhooksCard state={state} onDone={done} onError={setError} />}
                </>
              )}
      </div>
    </div>
  );
}

type CardProps = { state: ConnectionState; onDone: (message: string) => Promise<void>; onError: (message: string) => void };

function HowToGetKey() {
  return (
    <ol className="mt-3 list-decimal space-y-1.5 pl-5 text-[12px] leading-5 text-slate-600">
      <li>Open <b>Open Dental</b> on any computer in the practice and sign in as an administrator.</li>
      <li>Go to <b>Setup → Advanced Setup → API</b>.</li>
      <li>Click <b>Add</b>, choose <b>SmileOS</b>, and copy the <b>Customer Key</b> it shows.</li>
      <li>Make sure the <b>eConnector</b> service is running on your office server (Open Dental needs it for remote access).</li>
    </ol>
  );
}

/** Every clinic, for Admins: open one to manage it, or register a new one. */
function AllClinicsCard({ admin, current, version, onRegister, onSwitch }: {
  admin: boolean; current: string; version: number; onRegister: () => void; onSwitch: (code: string, edit: boolean) => void;
}) {
  const [clinics, setClinics] = useState<ConnectedClinic[] | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    listClinics().then(setClinics).catch((e: Error) => setError(e.message));
  }, [version]);
  const open = (code: string, thenEdit = false) => onSwitch(code, thenEdit);

  return (
    <section className={`${card} mt-5`} aria-labelledby="all-clinics-title">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600"><Building2 size={19} /></span>
          <div>
            <h2 id="all-clinics-title" className="text-[14px] font-bold text-slate-900">{admin ? 'All clinics' : 'Your clinics'}</h2>
            <p className="text-[11px] text-slate-500">{clinics ? `${clinics.length} clinic${clinics.length === 1 ? '' : 's'}${admin ? ' on SmileOS' : ' you own'}` : 'Loading…'}</p>
          </div>
        </div>
        <button type="button" className={btnPrimary} onClick={onRegister} data-testid="button-register-clinic"><Plus size={15} /> Register a new clinic</button>
      </div>
      {error && <p className="mt-3 text-[11px] text-rose-600">{error}</p>}
      {clinics && clinics.length > 0 && (
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[620px] text-left text-[11px]">
            <thead>
              <tr className="border-b border-slate-100 text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">
                <th className="py-2 pr-3">Clinic</th>{admin && <th className="px-3 py-2">Dentist</th>}<th className="px-3 py-2">Status</th><th className="px-3 py-2 text-right">Patients</th>
                <th className="px-3 py-2 text-right">Users</th><th className="px-3 py-2">Last sync</th><th className="py-2 pl-3 text-right" />
              </tr>
            </thead>
            <tbody>
              {clinics.map((c) => {
                const here = c.clinicCode === (current || chosenClinic());
                return (
                  <tr key={c.clinicCode} className={`border-b border-slate-50 ${here ? 'bg-blue-50/40' : ''}`} data-testid={`row-clinic-${c.clinicCode}`}>
                    <td className="py-2.5 pr-3"><p className="font-bold text-slate-800">{c.clinicName}</p><p className="font-mono text-[10px] text-slate-400">{c.clinicCode}{c.key ? ` · key ${c.key}` : ''}</p></td>
                    {admin && (
                      <td className="px-3 py-2.5">
                        {c.ownerEmail
                          ? <><p className="text-slate-700">{c.ownerName}</p><p className="text-[10px] text-slate-400">{c.ownerEmail}</p></>
                          : <span className="text-[10px] font-semibold text-amber-700">No dentist yet</span>}
                      </td>
                    )}
                    <td className="px-3 py-2.5">
                      <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${c.connected ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                        {c.connected ? 'Connected' : 'Not connected'}
                      </span>
                    </td>
                    <td className="px-3 py-2.5 text-right tabular-nums text-slate-700">{(c.patients ?? 0).toLocaleString()}</td>
                    <td className="px-3 py-2.5 text-right tabular-nums text-slate-700">{c.users ?? 0}</td>
                    <td className="px-3 py-2.5 text-slate-500">{c.lastSyncAt ? new Date(c.lastSyncAt).toLocaleString() : 'Never'}</td>
                    <td className="py-2.5 pl-3 text-right">
                      <div className="flex justify-end gap-1.5">
                        {here
                          ? <span className="self-center text-[10px] font-semibold text-blue-700">Managing</span>
                          : <button type="button" onClick={() => open(c.clinicCode)} className="rounded-lg border border-slate-200 px-2.5 py-1 text-[10px] font-semibold text-slate-600 hover:bg-slate-50">Open</button>}
                        <button type="button" onClick={() => open(c.clinicCode, true)} data-testid={`button-edit-clinic-${c.clinicCode}`}
                          className="rounded-lg border border-blue-200 px-2.5 py-1 text-[10px] font-semibold text-blue-700 hover:bg-blue-50">
                          {c.connected ? 'Edit' : 'Edit & connect'}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function ConnectCard({ state, onDone, onError, mode, onCancel }: CardProps & { mode: 'register' | 'edit'; onCancel?: () => void }) {
  const registering = mode === 'register';
  const { setSignedIn } = useAuth();
  const existing = registering ? null : state.clinic;
  const connected = Boolean(existing?.connected);
  const [name, setName] = useState(existing?.clinicName ?? '');
  const [key, setKey] = useState('');
  const [baseUrl, setBaseUrl] = useState(existing?.baseUrl ?? '');
  const [workstation, setWorkstation] = useState(existing?.workstation ?? '');
  const [test, setTest] = useState<{ result: ConnectionTest; key: string; baseUrl: string } | null>(null);
  const [busy, setBusy] = useState<'test' | 'connect' | null>(null);
  const tested = test?.result.ok && test.key === key.trim() && test.baseUrl === baseUrl.trim();
  // A connected clinic keeps its key when the field is left empty; a new or disconnected one needs it.
  const keyNeeded = !connected;
  // The dentist the clinic belongs to: Admins choose (or create) one; a dentist is always the owner.
  const [dentistId, setDentistId] = useState<string>(existing?.ownerUserId ?? '');
  const needsDentist = state.admin && registering;
  const complete = Boolean(name.trim() && (key.trim() || !keyNeeded) && baseUrl.trim() && workstation.trim()
    && (!needsDentist || dentistId));
  // Only a new key or address is tested (with the key given, or the clinic's own when only the address changed).
  const needsTest = !connected || Boolean(key.trim()) || baseUrl.trim() !== (existing?.baseUrl ?? '').trim();
  const addresses = [
    { label: 'API Service on the office server', value: 'http://localhost:30223/api/v1', hint: 'Open Dental on this network' },
    { label: 'Open Dental Remote API', value: state.remoteUrl, hint: 'through Open Dental\'s cloud and the eConnector' },
  ];

  const runTest = async () => {
    setBusy('test');
    try {
      setTest({ result: await testConnection(key, baseUrl), key: key.trim(), baseUrl: baseUrl.trim() });
      onError('');
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  const connect = async (event: FormEvent) => {
    event.preventDefault();
    setBusy('connect');
    try {
      if (registering) {
        const result = await registerClinic({ clinicName: name, customerKey: key, baseUrl, workstation,
          ...(state.admin ? { dentistUserId: dentistId } : {}) });
        // A dentist's top bar and menu follow their new clinic; then work on it (its section reloads).
        if (!state.admin) setSignedIn(await getMe());
        chooseClinic(result.clinicCode);
        return;
      }
      const result = await updateClinic({ clinicCode: existing?.clinicCode ?? '', clinicName: name, customerKey: key, baseUrl, workstation,
        ...(state.admin && dentistId && dentistId !== existing?.ownerUserId ? { dentistUserId: dentistId } : {}) });
      if (!state.admin) setSignedIn(await getMe()); // the top bar shows the clinic's new name and status
      onCancel?.();
      await onDone(connected ? result.message : `${result.message} Live updates are being set up in Open Dental now; then the data is brought up to date.`);
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <form onSubmit={connect} className={`${card} mt-5`} aria-labelledby="connect-title">
      <div className="flex items-start gap-4">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-blue-600"><PlugZap size={20} /></span>
        <div className="min-w-0 flex-1">
          <h2 id="connect-title" className="text-[14px] font-bold text-slate-900">
            {registering ? (state.admin ? 'Register a new clinic' : 'Register your clinic')
              : connected ? `Edit ${existing?.clinicName ?? ''}` : `Edit and connect ${existing?.clinicName ?? ''}`}
          </h2>
          {!registering && !connected && <p className="mt-1 text-[12px] text-slate-500">It isn't connected, so its data is hidden. Paste its Customer Key to connect it again; its data comes back and is brought up to date.</p>}
          {!registering && connected && <p className="mt-1 text-[12px] text-slate-500">Leave the Customer Key empty to keep the current one ({existing?.key}). A new key or address is tested before it is saved.</p>}
          {registering && !state.admin && <p className="mt-1 text-[12px] text-slate-500">Your account will belong to this clinic. You register one clinic.</p>}
          {(registering || !connected) && (
            <>
              <p className="mt-1 text-[12px] text-slate-500">First, get your Customer Key from Open Dental:</p>
              <HowToGetKey />
            </>
          )}
        </div>
      </div>

      {!state.developerKeySet && (
        <p className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-[11px] text-amber-800">
          SmileOS's Open Dental developer key isn't set on the server (OPENDENTAL_DEVELOPER_KEY), so a Customer Key alone won't work yet.
          Ask your SmileOS administrator, or paste the full key in the form <code>DeveloperKey/CustomerKey</code>.
        </p>
      )}

      {state.admin && (registering
        ? <DentistStep dentistId={dentistId} onChosen={setDentistId} onError={onError} />
        : <OwnerSelect dentistId={dentistId} onChange={setDentistId} />)}

      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        <label className={labelCls}>Practice name
          <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} placeholder="e.g. Smith Family Dental"
            className={inputCls} data-testid="input-practice-name" {...(registering || connected ? { 'data-autofocus': true } : {})} />
          <span className="mt-1 block text-[10px] font-normal text-slate-400">Each clinic has its own name.</span>
        </label>
        <label className={labelCls}>Customer Key
          <input value={key} onChange={(e) => setKey(e.target.value)} required={keyNeeded} autoComplete="off" spellCheck={false}
            placeholder={keyNeeded ? 'Paste it here' : `Keep current (${existing?.key ?? ''})`}
            className={`${inputCls} font-mono`} data-testid="input-customer-key" {...(!registering && !connected ? { 'data-autofocus': true } : {})} />
        </label>
      </div>

      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <div>
          <label className={labelCls}>Open Dental address
            <input value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} required placeholder="http://..." spellCheck={false}
              className={`${inputCls} font-mono`} data-testid="input-open-dental-address" />
          </label>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {addresses.map((a) => (
              <button key={a.value} type="button" onClick={() => setBaseUrl(a.value)} title={`${a.value} (${a.hint})`}
                className={`rounded-lg border px-2.5 py-1 text-[10px] font-semibold transition ${baseUrl.trim() === a.value ? 'border-blue-300 bg-blue-50 text-blue-700' : 'border-slate-200 text-slate-600 hover:bg-slate-50'}`}>
                {a.label}
              </button>
            ))}
          </div>
          <span className="mt-1.5 block text-[10px] text-slate-400">
            Where SmileOS reaches Open Dental's API. The API Service runs on your office server (port 30223); the Remote API works from anywhere while the eConnector runs.
          </span>
        </div>
        <label className={labelCls}>Office computer that sends live updates
          <input value={workstation} onChange={(e) => setWorkstation(e.target.value)} required placeholder="e.g. FRONTDESK-SERVER"
            className={inputCls} data-testid="input-connect-workstation" />
          <span className="mt-1.5 block text-[10px] font-normal text-slate-400">
            The computer running the Open Dental API Service, usually your office server. On it in Windows: Settings → System → About → Device name.
          </span>
        </label>
      </div>

      {test && (
        <p className={`mt-4 flex items-start gap-2 rounded-xl border px-4 py-3 text-[12px] ${test.result.ok ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-rose-200 bg-rose-50 text-rose-700'}`}
          data-testid="text-connection-test">
          {test.result.ok ? <CheckCircle2 size={15} className="mt-0.5 shrink-0" /> : <CircleAlert size={15} className="mt-0.5 shrink-0" />}
          {test.result.message}
        </p>
      )}

      <div className="mt-5 flex flex-wrap items-center gap-3 border-t border-slate-100 pt-4">
        <button type="button" className={btnGhost} disabled={!key.trim() || !baseUrl.trim() || busy !== null || !needsTest} onClick={() => void runTest()} data-testid="button-test-connection">
          {busy === 'test' ? <LoaderCircle size={15} className="animate-spin" /> : <RefreshCw size={15} />} Test connection
        </button>
        <button type="submit" className={btnPrimary} disabled={(needsTest && key.trim() !== '' && !tested) || (keyNeeded && !tested) || !complete || busy !== null} data-testid="button-connect">
          {busy === 'connect' ? <LoaderCircle size={15} className="animate-spin" /> : <PlugZap size={15} />}
          {busy === 'connect' ? 'Saving…' : registering ? 'Register and connect' : connected ? 'Save changes' : 'Save and connect'}
        </button>
        {onCancel && <button type="button" className={btnGhost} onClick={onCancel} disabled={busy !== null}>Cancel</button>}
        {busy === 'connect'
          ? <span className="text-[11px] text-slate-500">Connecting…</span>
          : !complete
            ? <span className="text-[11px] text-slate-400">Fill in all the fields.</span>
            : (keyNeeded || key.trim()) && !tested && <span className="text-[11px] text-slate-400">Test the connection first.</span>}
      </div>
    </form>
  );
}

function ConnectedCard({ state, onError, onEdit, onDisconnected, onRemoved }: CardProps & {
  onEdit: () => void; onDisconnected: (message: string) => void; onRemoved: (label: string) => void;
}) {
  const clinic = state.clinic!;
  const connected = clinic.connected;
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState(false);
  const [typedName, setTypedName] = useState('');
  const [busy, setBusy] = useState(false);
  const nameMatches = typedName.trim().toLowerCase() === clinic.clinicName.trim().toLowerCase();

  const remove = async () => {
    setBusy(true);
    try {
      const { removed } = await removeClinic(clinic.clinicCode, typedName);
      setConfirmRemove(false);
      onRemoved(removed);
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const disconnect = async () => {
    setBusy(true);
    try {
      await disconnectPractice(clinic.clinicCode);
      setConfirmDisconnect(false);
      onDisconnected(`${clinic.clinicName} is disconnected. SmileOS stopped syncing, and is removing its live updates from Open Dental in the background. Its data is kept but hidden until it is connected again.`);
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className={`${card} mt-5`} aria-labelledby="connected-title">
      <div className="flex flex-wrap items-start gap-4">
        <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${connected ? 'bg-emerald-50 text-emerald-600' : 'bg-slate-100 text-slate-500'}`}>
          {connected ? <CheckCircle2 size={20} /> : <Unplug size={20} />}
        </span>
        <div className="min-w-0 flex-1">
          <h2 id="connected-title" className="flex flex-wrap items-center gap-2 text-[14px] font-bold text-slate-900" data-testid="text-connected-practice">
            {clinic.clinicName}
            <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${connected ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`} data-testid="text-clinic-status">
              {connected ? 'Connected' : 'Not connected'}
            </span>
          </h2>
          {!connected && <p className="mt-1 text-[12px] text-slate-500">Not syncing with Open Dental. Its data is kept but hidden until it is connected again.</p>}
          <dl className="mt-2 grid gap-x-6 gap-y-1 text-[12px] sm:grid-cols-[auto_1fr]">
            <dt className="text-slate-400">Code</dt><dd className="font-mono text-slate-700">{clinic.clinicCode}</dd>
            <dt className="text-slate-400">Open Dental</dt><dd className="break-all text-slate-700">{clinic.baseUrl}</dd>
            <dt className="text-slate-400">Customer Key</dt><dd className="font-mono text-slate-700">{clinic.key}</dd>
          </dl>
        </div>
      </div>

      {confirmDisconnect && (
        <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-4 text-[12px] text-rose-800">
          <p>Disconnect {clinic.clinicName}? SmileOS stops syncing and removes its webhooks in Open Dental. Its data is kept but hidden until you connect it again.</p>
          <div className="mt-3 flex gap-2">
            <button type="button" disabled={busy} onClick={() => void disconnect()} className="flex h-10 items-center gap-2 rounded-xl bg-rose-600 px-4 text-[12px] font-bold text-white hover:bg-rose-700 disabled:opacity-50" data-testid="button-confirm-disconnect">
              {busy ? <LoaderCircle size={15} className="animate-spin" /> : <Unplug size={15} />} {busy ? 'Disconnecting…' : 'Disconnect'}
            </button>
            <button type="button" className={btnGhost} onClick={() => setConfirmDisconnect(false)}>Keep connected</button>
          </div>
        </div>
      )}

      {confirmRemove && (
        <div className="mt-4 rounded-xl border border-rose-300 bg-rose-50 p-4 text-[12px] text-rose-900" data-testid="confirm-remove-clinic">
          <p className="font-bold">Remove {clinic.clinicName} for good?</p>
          <p className="mt-1">This deletes the clinic and everything SmileOS copied for it: patients, appointments, records and sync history. It can't be undone.
            Its Open Dental database isn't touched, and staff linked to it are unlinked, not deleted. The audit log keeps its entries.</p>
          <label className="mt-3 block font-semibold">Type <b>{clinic.clinicName}</b> to confirm
            <input value={typedName} onChange={(e) => setTypedName(e.target.value)} autoComplete="off" autoFocus
              className={`${inputCls} border-rose-200`} data-testid="input-confirm-remove" />
          </label>
          <div className="mt-3 flex gap-2">
            <button type="button" disabled={busy || !nameMatches} onClick={() => void remove()}
              className="flex h-10 items-center gap-2 rounded-xl bg-rose-600 px-4 text-[12px] font-bold text-white hover:bg-rose-700 disabled:opacity-50" data-testid="button-confirm-remove">
              {busy ? <LoaderCircle size={15} className="animate-spin" /> : null} {busy ? 'Removing…' : 'Remove clinic'}
            </button>
            <button type="button" className={btnGhost} onClick={() => { setConfirmRemove(false); setTypedName(''); }}>Keep it</button>
          </div>
        </div>
      )}

      {!confirmDisconnect && !confirmRemove && (
        <div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-4">
          <button type="button" className={connected ? btnGhost : btnPrimary} onClick={onEdit} data-testid="button-edit-clinic">
            {connected ? <><KeyRound size={15} /> Edit</> : <><PlugZap size={15} /> Edit &amp; connect</>}
          </button>
          {connected && <button type="button" className={`${btnGhost} text-rose-600 hover:bg-rose-50`} onClick={() => setConfirmDisconnect(true)} data-testid="button-disconnect"><Unplug size={15} /> Disconnect</button>}
          {!connected && (
            <button type="button" className={`${btnGhost} ml-auto text-rose-600 hover:bg-rose-50`} onClick={() => setConfirmRemove(true)} data-testid="button-remove-clinic">
              Remove clinic
            </button>
          )}
        </div>
      )}
    </section>
  );
}

const STATE_STYLE: Record<Webhook['state'], { cls: string; label: string }> = {
  ok: { cls: 'bg-emerald-50 text-emerald-700', label: 'Active' },
  failing: { cls: 'bg-amber-50 text-amber-700', label: 'Failing' },
  missing: { cls: 'bg-slate-100 text-slate-600', label: 'Missing' },
  failed: { cls: 'bg-rose-50 text-rose-700', label: 'Error' },
  off: { cls: 'bg-slate-100 text-slate-500', label: 'Off' },
};

function WebhooksCard({ state, onDone, onError }: CardProps) {
  const clinic = state.clinic!;
  const [workstation, setWorkstation] = useState(clinic.workstation);
  const [busy, setBusy] = useState(false);
  const summary = webhookSummary(state.webhooks);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    try {
      await action();
      await onDone('Setting up live updates in Open Dental. This page updates by itself when it is done.');
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className={`${card} mt-5`} aria-labelledby="webhooks-title">
      <div className="flex flex-wrap items-start gap-4">
        <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${summary.tone === 'ok' ? 'bg-emerald-50 text-emerald-600' : summary.tone === 'warn' ? 'bg-amber-50 text-amber-600' : 'bg-slate-100 text-slate-500'}`}>
          <WebhookIcon size={19} />
        </span>
        <div className="min-w-0 flex-1">
          <h2 id="webhooks-title" className="text-[14px] font-bold text-slate-900">Live updates (webhooks)</h2>
          <p className="mt-1 text-[12px] text-slate-500">
            Open Dental tells SmileOS the moment a patient, appointment or schedule changes. SmileOS sets this up for you and checks it every night.
          </p>
          {state.settingUp
            ? (
              <p className="mt-2 flex items-center gap-2 text-[12px] font-semibold text-blue-700" data-testid="text-webhook-summary">
                <LoaderCircle size={14} className="animate-spin" /> Setting up live updates in Open Dental (about a second per table)…
              </p>
            )
            : (
              <p className={`mt-2 text-[12px] font-semibold ${summary.tone === 'ok' ? 'text-emerald-700' : summary.tone === 'warn' ? 'text-amber-700' : 'text-slate-500'}`} data-testid="text-webhook-summary">
                {summary.text}
                {state.checkedAt && <span className="ml-2 text-[10px] font-normal text-slate-400">checked {new Date(state.checkedAt).toLocaleString()}</span>}
              </p>
            )}
        </div>
      </div>

      {!state.publicUrl && (
        <p className="mt-4 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[11px] text-slate-600">
          SmileOS doesn't have a public internet address yet (PUBLIC_API_URL), so Open Dental can't reach it. Everything else works;
          changes still arrive through the regular sync every minute. Webhooks switch on once the address is set.
        </p>
      )}

      <form className="mt-4 flex flex-wrap items-end gap-3" onSubmit={(e) => { e.preventDefault(); void run(() => saveWorkstation(clinic.clinicCode, workstation)); }}>
        <label className={`${labelCls} min-w-[240px] flex-1`}>
          <span className="flex items-center gap-1.5"><MonitorSmartphone size={13} /> Office computer that sends the updates (optional)</span>
          <input value={workstation} onChange={(e) => setWorkstation(e.target.value)} placeholder="e.g. FRONTDESK-SERVER" className={inputCls} data-testid="input-workstation" />
          <span className="mt-1 block text-[10px] font-normal text-slate-400">
            {clinic.workstation ? `Now: ${clinic.workstation}. ` : 'Now: every computer running Open Dental (the same update may arrive more than once, which is harmless). '}
            To use only your office server (the computer running the Open Dental API Service), enter its name: in Windows, Settings → System → About → Device name.
          </span>
        </label>
        <button type="submit" className={btnPrimary} disabled={busy || state.settingUp || !workstation.trim()} data-testid="button-save-workstation">
          {busy ? <LoaderCircle size={15} className="animate-spin" /> : <WebhookIcon size={15} />} Save and set up
        </button>
        <button type="button" className={btnGhost} disabled={busy || state.settingUp} onClick={() => void run(() => repairWebhooks(clinic.clinicCode))} data-testid="button-repair-webhooks">
          <RefreshCw size={15} /> Check and repair
        </button>
      </form>

      {state.webhooks.length > 0 && state.webhooks.some((w) => w.state !== 'off') && (
        <ul className="mt-4 grid gap-1.5 sm:grid-cols-2" data-testid="list-webhooks">
          {state.webhooks.map((w) => (
            <li key={w.event} title={w.detail} className="flex items-center gap-2 rounded-lg border border-slate-100 px-3 py-2 text-[11px]">
              {w.state === 'ok' ? <CheckCircle2 size={13} className="text-emerald-500" /> : w.state === 'off' ? <CircleDashed size={13} className="text-slate-400" /> : <CircleAlert size={13} className="text-amber-500" />}
              <span className="min-w-0 flex-1 truncate text-slate-700">{eventLabel(w.event)}</span>
              <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${STATE_STYLE[w.state].cls}`}>{STATE_STYLE[w.state].label}</span>
            </li>
          ))}
        </ul>
      )}
      {state.webhooks.some((w) => w.state === 'failing' || w.state === 'failed') && (
        <ul className="mt-3 space-y-1 text-[11px] text-amber-800">
          {state.webhooks.filter((w) => w.state === 'failing' || w.state === 'failed').map((w) => <li key={w.event}><b>{eventLabel(w.event)}:</b> {w.detail}</li>)}
        </ul>
      )}
    </section>
  );
}

/**
 * (Admins, registering a clinic) the dentist it belongs to: looked up by email; when nobody has
 * that email yet, their dentist account is created here, with a temporary password shown once.
 */
function DentistStep({ dentistId, onChosen, onError }: {
  dentistId: string; onChosen: (id: string) => void; onError: (message: string) => void;
}) {
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [found, setFound] = useState<DentistLookup | null>(null);
  const [issued, setIssued] = useState<{ email: string; password: string } | null>(null);
  const [busy, setBusy] = useState<'find' | 'create' | null>(null);

  const find = async () => {
    setBusy('find'); setIssued(null); onChosen('');
    try {
      const result = await findDentist(email.trim());
      setFound(result);
      if (result.exists && result.isDentist && result.active && result.id) onChosen(result.id);
      onError('');
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  const create = async () => {
    setBusy('create');
    const password = temporaryPassword();
    try {
      const user = await createUser({ email: email.trim(), fullName: fullName.trim(), role: 'DENTIST', password });
      setIssued({ email: user.email, password });
      setFound({ exists: true, id: user.id, fullName: user.fullName, email: user.email, isDentist: true, active: true, roleLabel: 'Dentist' });
      onChosen(user.id);
      onError('');
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="mt-5 rounded-xl border border-indigo-100 bg-indigo-50/40 p-4" data-testid="dentist-step">
      <p className="text-[12px] font-bold text-slate-800">1. The dentist this clinic belongs to</p>
      <p className="mt-0.5 text-[11px] text-slate-500">They will see and manage this clinic when they sign in.</p>
      <div className="mt-3 flex flex-wrap items-end gap-2">
        <label className={`${labelCls} min-w-[240px] flex-1`}>Dentist's email
          <input type="email" value={email} onChange={(e) => { setEmail(e.target.value); setFound(null); onChosen(''); }}
            placeholder="dr.mehta@yourclinic.com" className={inputCls} data-testid="input-dentist-email" data-autofocus />
        </label>
        <button type="button" className={btnGhost} disabled={!email.trim() || busy !== null} onClick={() => void find()} data-testid="button-find-dentist">
          {busy === 'find' ? <LoaderCircle size={15} className="animate-spin" /> : null} Find dentist
        </button>
      </div>

      {found?.exists && found.isDentist && found.active && (
        <p className="mt-3 flex items-center gap-2 text-[12px] text-emerald-700" data-testid="text-dentist-found">
          <CheckCircle2 size={15} /> {found.fullName} ({found.email}) {issued ? 'was created and' : ''} will own this clinic.
        </p>
      )}
      {found?.exists && !found.isDentist && (
        <p className="mt-3 text-[12px] text-rose-700">{found.email} is a {found.roleLabel} user, not a dentist. A clinic belongs to a dentist: use another email.</p>
      )}
      {found?.exists && found.isDentist && !found.active && (
        <p className="mt-3 text-[12px] text-rose-700">{found.email} is deactivated. Reactivate it on the Users page first.</p>
      )}
      {found && !found.exists && (
        <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3">
          <p className="text-[12px] text-amber-900">No account uses {email.trim()} yet. Create the dentist's account first:</p>
          <div className="mt-2 flex flex-wrap items-end gap-2">
            <label className={`${labelCls} min-w-[220px] flex-1`}>Dentist's name
              <input value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={120} placeholder="Dr. Priya Mehta" className={inputCls} data-testid="input-new-dentist-name" />
            </label>
            <button type="button" className={btnPrimary} disabled={!fullName.trim() || busy !== null} onClick={() => void create()} data-testid="button-create-dentist">
              {busy === 'create' ? <LoaderCircle size={15} className="animate-spin" /> : null} Create dentist account
            </button>
          </div>
        </div>
      )}
      {issued && (
        <div role="status" className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-[11.5px] text-amber-900" data-testid="temporary-password">
          Give {issued.email} this temporary password; they choose their own at first sign-in. It won't be shown again:
          <code className="ml-1 rounded bg-white px-1.5 py-0.5 font-mono text-[12px] text-slate-900">{issued.password}</code>
        </div>
      )}
      {dentistId && <p className="mt-3 text-[12px] font-bold text-slate-800">2. The clinic and its Open Dental connection</p>}
    </div>
  );
}

/** (Admins, editing a clinic) who owns it: any active dentist. */
function OwnerSelect({ dentistId, onChange }: { dentistId: string; onChange: (id: string) => void }) {
  const [dentists, setDentists] = useState<AppUser[]>([]);
  useEffect(() => {
    listUsers().then((users) => setDentists(users.filter((u) => u.role === 'DENTIST' && u.active))).catch(() => undefined);
  }, []);
  return (
    <label className={`${labelCls} mt-5 block`}>Dentist (owner)
      <select value={dentistId} onChange={(e) => onChange(e.target.value)} className={inputCls} data-testid="select-clinic-owner">
        {!dentistId && <option value="">No dentist yet: choose one</option>}
        {dentists.map((d) => <option key={d.id} value={d.id}>{d.fullName} ({d.email})</option>)}
      </select>
      <span className="mt-1 block text-[10px] font-normal text-slate-400">The owner sees and manages this clinic. Create dentists on the Users page.</span>
    </label>
  );
}

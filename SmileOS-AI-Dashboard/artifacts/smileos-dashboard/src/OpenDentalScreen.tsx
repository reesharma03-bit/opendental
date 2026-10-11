import { useCallback, useEffect, useState, type FormEvent } from 'react';
import {
  CheckCircle2, CircleAlert, CircleDashed, KeyRound, LoaderCircle, MonitorSmartphone, PlugZap, RefreshCw, Unplug, Webhook as WebhookIcon,
} from 'lucide-react';
import {
  changeKey, connectPractice, disconnectPractice, eventLabel, getConnection, repairWebhooks, saveWorkstation, testConnection,
  webhookSummary, type ConnectionState, type ConnectionTest, type Webhook,
} from './lib/backendConnection';

const card = 'rounded-2xl border border-slate-200/80 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)] sm:p-6';
const inputCls = 'mt-1.5 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-[12px] text-slate-700 outline-none transition focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70';
const btnPrimary = 'flex h-10 items-center justify-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[12px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf] disabled:opacity-50';
const btnGhost = 'flex h-10 items-center justify-center gap-2 rounded-xl border border-slate-200 px-4 text-[12px] font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-50';
const labelCls = 'block text-[11px] font-semibold text-slate-600';

/** Connect the practice's Open Dental with its Customer Key; webhooks are set up automatically. */
export default function OpenDentalScreen() {
  const [state, setState] = useState<ConnectionState | null>(null);
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

  const done = async (message: string) => { setNotice(message); setError(''); await load(); };

  return (
    <div className="mx-auto max-w-[920px] px-4 py-6 sm:px-6 lg:px-9" data-testid="screen-open-dental">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-[Manrope] text-[22px] font-extrabold tracking-[-0.6px] text-slate-900">Open Dental</h1>
          <p className="mt-1 text-[12px] text-slate-500">Connect your practice's Open Dental to SmileOS. You only need the Customer Key.</p>
        </div>
        <button type="button" onClick={() => void load()} className="flex h-9 items-center gap-2 rounded-xl px-3 text-[11px] font-semibold text-slate-500 hover:bg-white hover:text-slate-800">
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
        </button>
      </div>

      {error && <p role="alert" className="mt-4 flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[12px] text-rose-700"><CircleAlert size={15} className="mt-0.5 shrink-0" />{error}</p>}
      {notice && <p role="status" className="mt-4 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-[12px] text-emerald-700"><CheckCircle2 size={15} className="mt-0.5 shrink-0" />{notice}</p>}

      {!state
        ? <div className="mt-10 flex justify-center text-slate-400">{loading ? <LoaderCircle className="animate-spin" /> : null}</div>
        : state.clinic
          ? (
            <>
              <ConnectedCard state={state} onDone={done} onError={setError} />
              <WebhooksCard state={state} onDone={done} onError={setError} />
            </>
          )
          : <ConnectCard state={state} onDone={done} onError={setError} />}
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

function ConnectCard({ state, onDone, onError }: CardProps) {
  const [name, setName] = useState('');
  const [key, setKey] = useState('');
  const [baseUrl, setBaseUrl] = useState('');
  const [workstation, setWorkstation] = useState('');
  const [test, setTest] = useState<{ result: ConnectionTest; key: string; baseUrl: string } | null>(null);
  const [busy, setBusy] = useState<'test' | 'connect' | null>(null);
  const tested = test?.result.ok && test.key === key.trim() && test.baseUrl === baseUrl.trim();
  const complete = Boolean(name.trim() && key.trim() && baseUrl.trim() && workstation.trim());
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
      const result = await connectPractice({ clinicName: name, customerKey: key, baseUrl, workstation });
      await onDone(`${result.message} Your data is now being copied from Open Dental; you can follow it in Sync settings.`);
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
          <h2 id="connect-title" className="text-[14px] font-bold text-slate-900">Connect your practice</h2>
          <p className="mt-1 text-[12px] text-slate-500">First, get your Customer Key from Open Dental:</p>
          <HowToGetKey />
        </div>
      </div>

      {!state.developerKeySet && (
        <p className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-[11px] text-amber-800">
          SmileOS's Open Dental developer key isn't set on the server (OPENDENTAL_DEVELOPER_KEY), so a Customer Key alone won't work yet.
          Ask your SmileOS administrator, or paste the full key in the form <code>DeveloperKey/CustomerKey</code>.
        </p>
      )}

      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        <label className={labelCls}>Practice name
          <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} placeholder="e.g. Smith Family Dental"
            className={inputCls} data-testid="input-practice-name" />
        </label>
        <label className={labelCls}>Customer Key
          <input value={key} onChange={(e) => setKey(e.target.value)} required autoComplete="off" spellCheck={false} placeholder="Paste it here"
            className={`${inputCls} font-mono`} data-testid="input-customer-key" />
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
        <button type="button" className={btnGhost} disabled={!key.trim() || !baseUrl.trim() || busy !== null} onClick={() => void runTest()} data-testid="button-test-connection">
          {busy === 'test' ? <LoaderCircle size={15} className="animate-spin" /> : <RefreshCw size={15} />} Test connection
        </button>
        <button type="submit" className={btnPrimary} disabled={!tested || !complete || busy !== null} data-testid="button-connect">
          {busy === 'connect' ? <LoaderCircle size={15} className="animate-spin" /> : <PlugZap size={15} />} {busy === 'connect' ? 'Connecting…' : 'Connect'}
        </button>
        {busy === 'connect'
          ? <span className="text-[11px] text-slate-500">Saving and setting up live updates in Open Dental (about 20 seconds)…</span>
          : !complete
            ? <span className="text-[11px] text-slate-400">Fill in all four fields.</span>
            : !tested && <span className="text-[11px] text-slate-400">Test the connection first.</span>}
      </div>
    </form>
  );
}

function ConnectedCard({ state, onDone, onError }: CardProps) {
  const clinic = state.clinic!;
  const [changing, setChanging] = useState(false);
  const [key, setKey] = useState('');
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);
  const [busy, setBusy] = useState(false);

  const saveKey = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    try {
      const result = await changeKey(key, '');
      setKey(''); setChanging(false);
      await onDone(`Key changed. ${result.message}`);
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const disconnect = async () => {
    setBusy(true);
    try {
      await disconnectPractice();
      setConfirmDisconnect(false);
      await onDone('Disconnected. SmileOS stopped syncing right away, and is removing its live updates from Open Dental in the background (about 20 seconds). Data already copied is kept.');
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className={`${card} mt-5`} aria-labelledby="connected-title">
      <div className="flex flex-wrap items-start gap-4">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600"><CheckCircle2 size={20} /></span>
        <div className="min-w-0 flex-1">
          <h2 id="connected-title" className="text-[14px] font-bold text-slate-900" data-testid="text-connected-practice">{clinic.clinicName} is connected</h2>
          <dl className="mt-2 grid gap-x-6 gap-y-1 text-[12px] sm:grid-cols-[auto_1fr]">
            <dt className="text-slate-400">Code</dt><dd className="font-mono text-slate-700">{clinic.clinicCode}</dd>
            <dt className="text-slate-400">Open Dental</dt><dd className="break-all text-slate-700">{clinic.baseUrl}</dd>
            <dt className="text-slate-400">Customer Key</dt><dd className="font-mono text-slate-700">{clinic.key}</dd>
          </dl>
        </div>
      </div>

      {changing && (
        <form onSubmit={saveKey} className="mt-4 rounded-xl border border-slate-100 bg-slate-50/60 p-4">
          <label className={labelCls}>New Customer Key
            <input value={key} onChange={(e) => setKey(e.target.value)} required autoComplete="off" spellCheck={false} className={`${inputCls} font-mono`} data-testid="input-new-customer-key" />
          </label>
          <p className="mt-1.5 text-[10px] text-slate-400">It is tested before it replaces the current key.</p>
          <div className="mt-3 flex gap-2">
            <button type="submit" className={btnPrimary} disabled={busy || !key.trim()}>{busy ? <LoaderCircle size={15} className="animate-spin" /> : <KeyRound size={15} />} Test and save</button>
            <button type="button" className={btnGhost} onClick={() => { setChanging(false); setKey(''); }}>Cancel</button>
          </div>
        </form>
      )}

      {confirmDisconnect && (
        <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-4 text-[12px] text-rose-800">
          <p>Disconnect {clinic.clinicName}? SmileOS stops syncing and removes its webhooks in Open Dental. Data already copied stays, and you can connect again later.</p>
          <div className="mt-3 flex gap-2">
            <button type="button" disabled={busy} onClick={() => void disconnect()} className="flex h-10 items-center gap-2 rounded-xl bg-rose-600 px-4 text-[12px] font-bold text-white hover:bg-rose-700 disabled:opacity-50" data-testid="button-confirm-disconnect">
              {busy ? <LoaderCircle size={15} className="animate-spin" /> : <Unplug size={15} />} {busy ? 'Disconnecting…' : 'Disconnect'}
            </button>
            <button type="button" className={btnGhost} onClick={() => setConfirmDisconnect(false)}>Keep connected</button>
          </div>
        </div>
      )}

      {!changing && !confirmDisconnect && (
        <div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-4">
          <button type="button" className={btnGhost} onClick={() => setChanging(true)} data-testid="button-change-key"><KeyRound size={15} /> Change key</button>
          <button type="button" className={`${btnGhost} text-rose-600 hover:bg-rose-50`} onClick={() => setConfirmDisconnect(true)} data-testid="button-disconnect"><Unplug size={15} /> Disconnect</button>
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

  const run = async (action: () => Promise<{ webhooks: Webhook[] }>) => {
    setBusy(true);
    try {
      const { webhooks } = await action();
      const s = webhookSummary(webhooks);
      await onDone(s.tone === 'ok' ? `${s.text}. Open Dental now sends changes to SmileOS as they happen.` : s.text);
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
          <p className={`mt-2 text-[12px] font-semibold ${summary.tone === 'ok' ? 'text-emerald-700' : summary.tone === 'warn' ? 'text-amber-700' : 'text-slate-500'}`} data-testid="text-webhook-summary">{summary.text}</p>
        </div>
      </div>

      {!state.publicUrl && (
        <p className="mt-4 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[11px] text-slate-600">
          SmileOS doesn't have a public internet address yet (PUBLIC_API_URL), so Open Dental can't reach it. Everything else works;
          changes still arrive through the regular sync every minute. Webhooks switch on once the address is set.
        </p>
      )}

      <form className="mt-4 flex flex-wrap items-end gap-3" onSubmit={(e) => { e.preventDefault(); void run(() => saveWorkstation(workstation)); }}>
        <label className={`${labelCls} min-w-[240px] flex-1`}>
          <span className="flex items-center gap-1.5"><MonitorSmartphone size={13} /> Office computer that sends the updates (optional)</span>
          <input value={workstation} onChange={(e) => setWorkstation(e.target.value)} placeholder="e.g. FRONTDESK-SERVER" className={inputCls} data-testid="input-workstation" />
          <span className="mt-1 block text-[10px] font-normal text-slate-400">
            {clinic.workstation ? `Now: ${clinic.workstation}. ` : 'Now: every computer running Open Dental (the same update may arrive more than once, which is harmless). '}
            To use only your office server (the computer running the Open Dental API Service), enter its name: in Windows, Settings → System → About → Device name.
          </span>
        </label>
        <button type="submit" className={btnPrimary} disabled={busy || !workstation.trim()} data-testid="button-save-workstation">
          {busy ? <LoaderCircle size={15} className="animate-spin" /> : <WebhookIcon size={15} />} Save and set up
        </button>
        <button type="button" className={btnGhost} disabled={busy} onClick={() => void run(repairWebhooks)} data-testid="button-repair-webhooks">
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

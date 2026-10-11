import { useCallback, useEffect, useMemo, useState } from 'react';
import { CircleAlert, CirclePause, CirclePlay, LoaderCircle, RefreshCw, Save } from 'lucide-react';
import {
  getSyncControl, getSyncSelections, getSyncStatus, groupSyncable, pauseSync, resumeSync, saveSyncSelection,
  startForceSync, syncLabel, type ClinicSyncSelection, type SyncControlState, type SyncSelections, type SyncStatus,
} from './lib/backendSync';

const card = 'rounded-2xl border border-slate-200/80 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)] sm:p-6';
const btnPrimary = 'flex h-10 items-center justify-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[12px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf] disabled:opacity-50';
const inputCls = 'h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-[12px] text-slate-700 outline-none transition focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70';

const when = (iso: string | null) => (iso
  ? new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }).format(new Date(iso))
  : '');

/** Pause / resume the Open Dental sync, run a Force Sync, and choose which screens' data each clinic syncs. */
export default function SyncSettingsScreen() {
  const [control, setControl] = useState<SyncControlState | null>(null);
  const [selections, setSelections] = useState<SyncSelections | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [c, s] = await Promise.all([getSyncControl(), getSyncSelections()]);
      setControl(c); setSelections(s); setError('');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  return (
    <div className="mx-auto max-w-[1100px] px-4 py-6 sm:px-6 lg:px-9" data-testid="screen-sync-settings">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-[Manrope] text-[22px] font-extrabold tracking-[-0.6px] text-slate-900">Sync settings</h1>
          <p className="mt-1 text-[12px] text-slate-500">Pause the Open Dental sync, copy everything now with Force Sync, or choose which screens each clinic keeps in step.</p>
        </div>
        <button type="button" onClick={() => void load()} className="flex h-9 items-center gap-2 rounded-xl px-3 text-[11px] font-semibold text-slate-500 hover:bg-white hover:text-slate-800" data-testid="button-refresh-sync-settings">
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
        </button>
      </div>

      {error && <p role="alert" className="mt-4 flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[12px] text-rose-700"><CircleAlert size={15} />{error}</p>}
      {notice && <p role="status" className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-[12px] text-emerald-700">{notice}</p>}

      {loading && !control
        ? <div className="mt-10 flex justify-center text-slate-400"><LoaderCircle className="animate-spin" /></div>
        : (
          <>
            {control && <PauseCard control={control} onChange={(c, message) => { setControl(c); setNotice(message); setError(''); }} onError={setError} />}
            {control && <ForceSyncCard paused={control.paused} />}
            {selections && <SelectionCard selections={selections} onSaved={(s, message) => { setSelections(s); setNotice(message); setError(''); }} onError={setError} />}
          </>
        )}
    </div>
  );
}

function PauseCard({ control, onChange, onError }: {
  control: SyncControlState;
  onChange: (state: SyncControlState, message: string) => void;
  onError: (message: string) => void;
}) {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  const act = async (pause: boolean) => {
    setBusy(true);
    try {
      const state = pause ? await pauseSync(reason) : await resumeSync();
      setReason('');
      onChange(state, pause
        ? 'Sync paused. Nothing is read from or sent to Open Dental until you resume.'
        : 'Sync resumed. It picks up where it left off, and waiting edits are sent to Open Dental.');
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className={`${card} mt-5`} aria-labelledby="sync-pause-title">
      <div className="flex flex-wrap items-start gap-4">
        <span className={`mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${control.paused ? 'bg-amber-50 text-amber-600' : 'bg-emerald-50 text-emerald-600'}`}>
          {control.paused ? <CirclePause size={20} /> : <CirclePlay size={20} />}
        </span>
        <div className="min-w-0 flex-1">
          <h2 id="sync-pause-title" className="text-[14px] font-bold text-slate-900" data-testid="text-sync-state">
            {control.paused ? 'Sync is paused' : 'Sync is running'}
          </h2>
          {control.paused ? (
            <p className="mt-1 text-[12px] text-slate-600">
              Paused{control.pausedBy ? ` by ${control.pausedBy}` : ''}{control.pausedAt ? ` · ${when(control.pausedAt)}` : ''}
              {control.reason ? <> · <span className="italic">“{control.reason}”</span></> : null}
            </p>
          ) : (
            <p className="mt-1 text-[12px] text-slate-600">
              Changes are pulled from Open Dental every minute, and dashboard edits are sent to it right away.
              {control.fullSyncRunning ? ' A full sync is running now.' : ''}
            </p>
          )}
          <ul className="mt-3 list-disc space-y-1 pl-4 text-[11px] leading-5 text-slate-500">
            <li>While paused, nothing is read from Open Dental: no change pulls, no nightly sync and no Force Sync. A running Force Sync stops after the screen it is on.</li>
            <li>Edits made in the dashboard are saved and wait in the queue; they go to Open Dental when you resume.</li>
            <li>Webhooks from Open Dental are still saved, and screens that read Open Dental live still work.</li>
          </ul>
        </div>
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-3 border-t border-slate-100 pt-4">
        {!control.paused && (
          <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} placeholder="Reason (optional), e.g. Open Dental upgrade tonight"
            aria-label="Reason for pausing" className={`${inputCls} max-w-[420px] flex-1`} data-testid="input-pause-reason" />
        )}
        <button type="button" disabled={busy} onClick={() => void act(!control.paused)} data-testid={control.paused ? 'button-resume-sync' : 'button-pause-sync'}
          className={control.paused ? btnPrimary : 'flex h-10 items-center justify-center gap-2 rounded-xl bg-amber-500 px-4 text-[12px] font-bold text-white transition hover:bg-amber-600 disabled:opacity-50'}>
          {busy ? <LoaderCircle size={15} className="animate-spin" /> : control.paused ? <CirclePlay size={15} /> : <CirclePause size={15} />}
          {control.paused ? 'Resume sync' : 'Pause sync'}
        </button>
      </div>
    </section>
  );
}

function syncSummary(status: SyncStatus | null): string {
  if (!status || status.state === 'idle') return 'No Force Sync since the backend started.';
  if (status.state === 'running') return status.currentStep ? `Syncing · ${status.currentStep}` : 'Syncing…';
  if (status.state === 'failed') return `Failed: ${status.error ?? 'unknown error'}`;
  if (status.state === 'paused') return `Stopped part-way: ${status.error ?? 'sync paused'}. What it finished is kept.`;
  const records = (status.records ?? 0).toLocaleString();
  const failures = status.failures ?? 0;
  return failures
    ? `Done · ${records} records · ${failures} screen${failures === 1 ? '' : 's'} failed`
    : `Done · ${records} records`;
}

function ForceSyncCard({ paused }: { paused: boolean }) {
  const [status, setStatus] = useState<SyncStatus | null>(null);
  const [error, setError] = useState('');
  const [starting, setStarting] = useState(false);
  const running = status?.state === 'running';

  // Picks up a sync that is already running (nightly run, another tab), and re-reads it
  // when sync is paused or resumed (a running sync stops when paused).
  useEffect(() => {
    getSyncStatus().then(setStatus).catch((e: Error) => setError(e.message));
  }, [paused]);

  useEffect(() => {
    if (!running) return undefined;
    const timer = window.setInterval(() => {
      getSyncStatus().then(setStatus).catch((e: Error) => setError(e.message));
    }, 3000);
    return () => window.clearInterval(timer);
  }, [running]);

  const start = async () => {
    setError(''); setStarting(true);
    try {
      setStatus(await startForceSync());
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setStarting(false);
    }
  };

  const failed = status?.results?.filter((r) => r.status === 'failed') ?? [];
  const percent = running && status?.total ? Math.min(100, Math.round(((status.done ?? 0) / status.total) * 100)) : 0;
  const finishedAt = status?.finishedAt ? when(status.finishedAt) : '';

  return (
    <section className={`${card} mt-5`} aria-labelledby="force-sync-title">
      <div className="flex flex-wrap items-start gap-4">
        <span className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
          <RefreshCw size={19} className={running ? 'animate-spin' : ''} />
        </span>
        <div className="min-w-0 flex-1">
          <h2 id="force-sync-title" className="text-[14px] font-bold text-slate-900">Force Sync</h2>
          <p className="mt-1 text-[12px] text-slate-600">
            Copies everything each clinic syncs from Open Dental now, including the data Open Dental lists per patient,
            and notices records deleted there. The same runs every night at 2 AM. On a large practice it can take a long time.
          </p>
        </div>
        <button type="button" onClick={() => void start()} disabled={paused || running || starting} data-testid="button-force-refresh"
          aria-busy={running} title={paused ? 'Resume sync first' : undefined} className={btnPrimary}>
          {running || starting ? <LoaderCircle size={15} className="animate-spin" /> : <RefreshCw size={15} />}
          {running ? 'Syncing…' : 'Force Sync'}
        </button>
      </div>

      {running && (
        <div className="mt-4" aria-hidden="true">
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
            <div className="h-full rounded-full bg-gradient-to-r from-blue-500 to-indigo-500 transition-[width] duration-500" style={{ width: `${percent}%` }} />
          </div>
          <p className="mt-1.5 text-[10px] text-slate-400">{status?.done ?? 0} of {status?.total ?? 0} done · {percent}%</p>
        </div>
      )}

      <p data-testid="text-force-sync-status" aria-live="polite"
        className={`mt-3 text-[11px] ${error || status?.state === 'failed' ? 'text-rose-600' : status?.state === 'paused' || failed.length ? 'text-amber-600' : 'text-slate-500'}`}>
        {error || syncSummary(status)}{!running && finishedAt ? ` · ${finishedAt}` : ''}
      </p>
      {paused && !running && <p className="mt-1 text-[11px] text-amber-600">Sync is paused. Resume it above to run Force Sync.</p>}

      {failed.length > 0 && (
        <details className="mt-3 rounded-xl border border-amber-200 bg-amber-50/60 px-4 py-2 text-[11px] text-amber-800">
          <summary className="cursor-pointer font-semibold">What failed ({failed.length})</summary>
          <ul className="mt-2 space-y-1 pb-1">
            {failed.map((r) => (
              <li key={`${r.clinic}-${r.resource}`}><b>{r.clinic} · {r.resource}</b>: {r.error ?? 'failed'}</li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}

function SelectionCard({ selections, onSaved, onError }: {
  selections: SyncSelections;
  onSaved: (s: SyncSelections, message: string) => void;
  onError: (message: string) => void;
}) {
  const groups = useMemo(() => groupSyncable(selections.syncable), [selections.syncable]);
  const [code, setCode] = useState(selections.clinics.find((c) => c.active)?.clinicCode ?? selections.clinics[0]?.clinicCode ?? '');
  const clinic = selections.clinics.find((c) => c.clinicCode === code);
  const [everything, setEverything] = useState(true);
  const [chosen, setChosen] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);

  // Start from what is saved whenever the clinic (or the saved choice) changes.
  useEffect(() => {
    setEverything(clinic?.everything ?? true);
    setChosen(new Set(clinic?.resources ?? []));
  }, [clinic]);

  if (!clinic) {
    return <section className={`${card} mt-5 text-[12px] text-slate-500`}>No clinics yet. Add a row to the clinics table first.</section>;
  }

  const saved = (c: ClinicSyncSelection) => c.everything === everything
    && (everything || (c.resources.length === chosen.size && c.resources.every((r) => chosen.has(r))));
  const unchanged = saved(clinic);

  const toggle = (names: string[], on: boolean) => {
    setChosen((current) => {
      const next = new Set(current);
      names.forEach((n) => (on ? next.add(n) : next.delete(n)));
      return next;
    });
  };

  const save = async () => {
    setBusy(true);
    try {
      const result = await saveSyncSelection(clinic.clinicCode, everything, [...chosen]);
      onSaved(result, everything
        ? `${clinic.clinicCode} syncs everything again.`
        : `${clinic.clinicCode} now syncs ${chosen.size} screen${chosen.size === 1 ? '' : 's'}.`);
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className={`${card} mt-5`} aria-labelledby="sync-screens-title">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="sync-screens-title" className="text-[14px] font-bold text-slate-900">What each clinic syncs</h2>
          <p className="mt-1 max-w-[640px] text-[12px] text-slate-500">
            Syncing fewer screens leaves more of Open Dental's request allowance (about one call a second) for the ones you use.
            Screens you leave out keep the data they already have, and catch up from where they stopped when you add them back.
          </p>
        </div>
        {selections.clinics.length > 1 && (
          <label className="text-[11px] font-semibold text-slate-500">
            Clinic
            <select value={code} onChange={(e) => setCode(e.target.value)} className={`${inputCls} mt-1 min-w-[200px]`} data-testid="select-sync-clinic">
              {selections.clinics.map((c) => (
                <option key={c.clinicCode} value={c.clinicCode}>{c.clinicName || c.clinicCode}{c.active ? '' : ' (inactive)'}</option>
              ))}
            </select>
          </label>
        )}
      </div>

      <fieldset className="mt-4 flex flex-wrap gap-2" aria-label="What to sync">
        {[{ value: true, label: 'Sync everything' }, { value: false, label: 'Only the screens I choose' }].map((option) => (
          <label key={String(option.value)} className={`flex h-10 cursor-pointer items-center gap-2 rounded-xl border px-4 text-[12px] font-semibold transition ${everything === option.value ? 'border-blue-300 bg-blue-50 text-blue-700' : 'border-slate-200 text-slate-600 hover:bg-slate-50'}`}>
            <input type="radio" name="sync-scope" className="accent-[#315fe7]" checked={everything === option.value} onChange={() => setEverything(option.value)}
              data-testid={option.value ? 'radio-sync-everything' : 'radio-sync-chosen'} />
            {option.label}
          </label>
        ))}
      </fieldset>

      {!everything && (
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          {groups.map((group) => {
            const names = group.resources.map((r) => r.name);
            const count = names.filter((n) => chosen.has(n)).length;
            return (
              <div key={group.label} className="rounded-xl border border-slate-200 p-3">
                <label className="flex cursor-pointer items-center gap-2 text-[12px] font-bold text-slate-800">
                  <input type="checkbox" className="accent-[#315fe7]" checked={count === names.length}
                    ref={(el) => { if (el) el.indeterminate = count > 0 && count < names.length; }}
                    onChange={(e) => toggle(names, e.target.checked)} data-testid={`checkbox-sync-group-${group.label.toLowerCase().replaceAll(/[^a-z0-9]+/g, '-')}`} />
                  {group.label}
                  <span className="ml-auto text-[10px] font-medium text-slate-400">{count}/{names.length}</span>
                </label>
                <div className="mt-2 grid grid-cols-1 gap-x-3 gap-y-1 pl-5 sm:grid-cols-2">
                  {group.resources.map((r) => (
                    <label key={r.name} className="flex cursor-pointer items-center gap-2 text-[11px] text-slate-600">
                      <input type="checkbox" className="accent-[#315fe7]" checked={chosen.has(r.name)} onChange={(e) => toggle([r.name], e.target.checked)}
                        data-testid={`checkbox-sync-${r.name}`} />
                      {r.label}
                    </label>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {!everything && unchanged && clinic.alsoSynced.length > 0 && (
        <p className="mt-3 text-[11px] text-slate-500" data-testid="text-also-synced">
          Also synced, because chosen screens are read through them: {clinic.alsoSynced.map((n) => syncLabel(groups, n)).join(', ')}.
        </p>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-3 border-t border-slate-100 pt-4">
        <button type="button" className={btnPrimary} disabled={busy || unchanged || (!everything && chosen.size === 0)} onClick={() => void save()} data-testid="button-save-sync-selection">
          {busy ? <LoaderCircle size={15} className="animate-spin" /> : <Save size={15} />} Save for {clinic.clinicName || clinic.clinicCode}
        </button>
        {!everything && chosen.size === 0 && <span className="text-[11px] text-amber-600">Choose at least one screen. To stop syncing altogether, pause sync instead.</span>}
        {!unchanged && (everything || chosen.size > 0) && <span className="text-[11px] text-slate-400">Unsaved changes</span>}
      </div>
    </section>
  );
}

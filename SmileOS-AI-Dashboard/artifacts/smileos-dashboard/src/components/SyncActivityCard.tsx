import { useEffect, useMemo, useState } from 'react';
import { Activity, CircleAlert, LoaderCircle } from 'lucide-react';
import { getSyncActivity, groupSyncable, syncLabel, type SyncActivity, type SyncTableActivity } from '../lib/backendSync';

const card = 'rounded-2xl border border-slate-200/80 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)] sm:p-6';

/** "12 s ago", "4 min ago", "3 h ago", or a date. */
export function ago(iso: string | null | undefined): string {
  if (!iso) return '';
  const seconds = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 0) return inFuture(iso);
  if (seconds < 60) return `${seconds} s ago`;
  if (seconds < 3600) return `${Math.round(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.round(seconds / 3600)} h ago`;
  return new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }).format(new Date(iso));
}

function inFuture(iso: string): string {
  const seconds = Math.round((new Date(iso).getTime() - Date.now()) / 1000);
  if (seconds <= 0) return 'now';
  if (seconds < 60) return `in ${seconds} s`;
  if (seconds < 3600) return `in ${Math.round(seconds / 60)} min`;
  return `in ${Math.round(seconds / 3600)} h`;
}

function elapsed(iso?: string): string {
  if (!iso) return '';
  const minutes = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
  return minutes < 1 ? 'under a minute' : minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
}

const CORE_LABELS: Record<string, string> = {
  patients: 'Patients', appointments: 'Appointments', procedurelogs: 'Procedures', documents: 'Documents (images)',
};

/**
 * What is syncing right now for the clinic being viewed: the full sync in progress (step, progress,
 * records so far), the background work (change check every minute, live updates from Open Dental,
 * edits waiting to be sent) and, per screen, how many records SmileOS holds and when they were synced.
 */
export default function SyncActivityCard() {
  const [activity, setActivity] = useState<SyncActivity | null>(null);
  const [error, setError] = useState('');
  const [, tick] = useState(0);
  const running = activity?.fullSync.state === 'running' || activity?.fullSync.state === 'queued';

  useEffect(() => {
    let stopped = false;
    const load = () => getSyncActivity()
      .then((a) => { if (!stopped) { setActivity(a); setError(''); } })
      .catch((e: Error) => { if (!stopped) setError(e.message); });
    void load();
    const timer = window.setInterval(load, running ? 4000 : 20000);
    return () => { stopped = true; window.clearInterval(timer); };
  }, [running]);

  // "x s ago" keeps counting between loads.
  useEffect(() => {
    const timer = window.setInterval(() => tick((n) => n + 1), 5000);
    return () => window.clearInterval(timer);
  }, []);

  const groups = useMemo(() => groupSyncable((activity?.tables ?? []).filter((t) => t.kind !== 'core').map((t) => t.name)), [activity]);
  const label = (t: SyncTableActivity) => CORE_LABELS[t.name] ?? syncLabel(groups, t.name);

  if (!activity) {
    return (
      <section className={`${card} mt-5`}>
        {error
          ? <p className="flex items-center gap-2 text-[12px] text-rose-600"><CircleAlert size={15} />{error}</p>
          : <div className="flex justify-center text-slate-400"><LoaderCircle className="animate-spin" /></div>}
      </section>
    );
  }

  const full = activity.fullSync;
  const bg = activity.background;
  const percent = full.total ? Math.min(100, Math.round(((full.done ?? 0) / full.total) * 100)) : 0;
  // The step reads "CLINIC_CODE: resource"; the first step copies the core tables together.
  const stepName = full.currentStep ? full.currentStep.slice(full.currentStep.indexOf(':') + 1).trim() : '';
  const coreStep = stepName.includes(',');
  const step = !stepName ? '' : coreStep
    ? (full.coreTable ? `${CORE_LABELS[full.coreTable] ?? syncLabel(groups, full.coreTable)} (core tables)` : 'patients, appointments, procedures and documents')
    : (CORE_LABELS[stepName] ?? syncLabel(groups, stepName));
  const unit = stepName && (activity.tables.find((t) => t.name === stepName)?.kind === 'per-patient') ? 'patients / records' : '';
  const inStep = full.state === 'running' && !coreStep
    ? [full.parentsTotal ? `${(full.parentsDone ?? 0).toLocaleString()} of ${full.parentsTotal.toLocaleString()} ${unit.startsWith('patients') ? 'patients' : 'records'} read` : '',
      full.stepRecords ? `${full.stepRecords.toLocaleString()} rows so far` : ''].filter(Boolean).join(' · ')
    : '';
  const tables = activity.tables;
  const isActive = (t: SyncTableActivity) => full.state === 'running'
    && (coreStep ? (full.coreTable ? t.name === full.coreTable : t.kind === 'core') : stepName === t.name);
  const active = tables.filter(isActive);
  const failed = tables.filter((t) => t.status === 'failed');
  // The screen being copied comes first, even before it has its first record.
  const withData = [...active, ...tables.filter((t) => !isActive(t) && (t.records > 0 || t.lastSyncedAt))];
  const notYet = tables.filter((t) => !isActive(t) && t.records === 0 && !t.lastSyncedAt && t.status !== 'failed');
  const continued = (full.resumed ?? 0) > 0;

  return (
    <section className={`${card} mt-5`} aria-labelledby="sync-activity-title" data-testid="card-sync-activity">
      <div className="flex flex-wrap items-start gap-4">
        <span className={`mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${running ? 'bg-blue-50 text-blue-600' : 'bg-emerald-50 text-emerald-600'}`}>
          <Activity size={19} className={running ? 'animate-pulse' : ''} />
        </span>
        <div className="min-w-0 flex-1">
          <h2 id="sync-activity-title" className="text-[14px] font-bold text-slate-900">Sync activity · {activity.clinicName}</h2>
          <p className="mt-1 text-[12px] text-slate-600" aria-live="polite" data-testid="text-sync-now">
            {!activity.connected
              ? 'Not connected: nothing syncs for this clinic until it is connected again.'
              : full.state === 'running'
                ? <><b>Right now:</b> {full.trigger === 'resume' || (continued && full.trigger === 'connect') ? 'continuing the stopped sync' : full.trigger === 'connect' ? 'first sync' : full.trigger === 'nightly' ? 'nightly full sync' : 'Force Sync'} · step {full.done ?? 0} of {full.total ?? '?'}{step ? <> · copying <b>{step}</b></> : null}{inStep ? ` (${inStep})` : ''} · running {elapsed(full.startedAt)}</>
                : full.state === 'queued'
                  ? <><b>Right now:</b> waiting for another clinic's sync to end; this clinic follows right after.</>
                  : full.state === 'paused'
                    ? <><b>Stopped part-way</b> ({full.error ?? 'stopped'}) {ago(full.finishedAt)}. What was copied is kept: reconnecting or resuming continues from where it stopped, it doesn't start over.</>
                    : activity.otherClinicSyncing
                      ? <><b>Right now:</b> clinic {activity.otherClinicSyncing} is syncing; this clinic's changes keep arriving in the background (below).</>
                      : <><b>Right now:</b> no full sync is running. Changes in Open Dental keep arriving in the background (below).</>}
          </p>
          {continued && (full.state === 'running' || full.state === 'paused') && (
            <p className="mt-1 text-[11px] text-emerald-700" data-testid="text-sync-continued">
              Continued where it stopped: {full.resumed} step{full.resumed === 1 ? ' was' : 's were'} already done and weren't copied again.
            </p>
          )}
        </div>
      </div>

      {full.state === 'running' && full.total ? (
        <div className="mt-4" aria-hidden="true">
          <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100">
            <div className="h-full rounded-full bg-gradient-to-r from-blue-500 to-indigo-500 transition-[width] duration-500" style={{ width: `${percent}%` }} />
          </div>
          <p className="mt-1.5 text-[10px] text-slate-400">{percent}% · {full.failures ? `${full.failures} step${full.failures === 1 ? '' : 's'} failed so far · ` : ''}refreshes every few seconds</p>
        </div>
      ) : null}

      <div className="mt-4 grid gap-3 sm:grid-cols-3">
        <Fact title="Change check (every minute)" value={bg.lastChangePullAt ? ago(bg.lastChangePullAt) : 'Not yet since the backend started'}
          hint="Patients, appointments, procedures and documents changed in Open Dental" />
        <Fact title="Live updates from Open Dental" value={bg.lastWebhookAt ? `${ago(bg.lastWebhookAt)}${bg.lastWebhookTable ? ` · ${bg.lastWebhookTable}` : ''}` : 'None since the backend started'}
          hint="Sent by Open Dental the moment something changes" />
        <Fact title="Edits waiting to go to Open Dental" value={`${bg.editsWaiting.toLocaleString()} waiting${bg.editsFailed ? ` · ${bg.editsFailed} failed` : ''}`}
          hint="Changes made in SmileOS" warn={bg.editsFailed > 0} />
      </div>

      {active.length > 0 && (
        <p className="mt-4 text-[11.5px] text-blue-700"><b>Syncing now:</b> {active.map(label).join(', ')}</p>
      )}
      {failed.length > 0 && (
        <details className="mt-3 rounded-xl border border-amber-200 bg-amber-50/60 px-4 py-2 text-[11px] text-amber-800" open>
          <summary className="cursor-pointer font-semibold">{failed.length} screen{failed.length === 1 ? '' : 's'} failed their last sync</summary>
          <ul className="mt-2 space-y-1 pb-1">
            {failed.map((t) => <li key={t.name}><b>{label(t)}</b>: {t.error ?? 'failed'}{t.nextDueAt ? ` · retries ${inFuture(t.nextDueAt)}` : ''}</li>)}
          </ul>
        </details>
      )}

      <div className="mt-4 overflow-x-auto rounded-xl border border-slate-200">
        <table className="w-full min-w-[520px] text-left text-[11.5px]" data-testid="table-sync-activity">
          <thead className="bg-slate-50 text-[10.5px] uppercase tracking-wide text-slate-500">
            <tr><th className="px-3 py-2">Screen</th><th className="px-3 py-2 text-right">Records in SmileOS</th><th className="px-3 py-2">Last synced</th><th className="px-3 py-2">Next</th></tr>
          </thead>
          <tbody>
            {withData.map((t) => {
              const now = active.includes(t);
              return (
                <tr key={t.name} className={`border-t border-slate-100 ${now ? 'bg-blue-50/60' : ''}`}>
                  <td className="px-3 py-1.5 font-semibold text-slate-800">
                    {now && <LoaderCircle size={12} className="mr-1 inline animate-spin text-blue-600" />}
                    {label(t)}
                    {t.status === 'failed' && <span className="ml-1.5 rounded-full bg-amber-100 px-1.5 py-0.5 text-[9.5px] font-bold text-amber-800">failed</span>}
                  </td>
                  <td className="px-3 py-1.5 text-right tabular-nums text-slate-700">{t.records.toLocaleString()}</td>
                  <td className="px-3 py-1.5 text-slate-500">{now ? (inStep ? `syncing now · ${inStep}` : 'syncing now') : ago(t.lastSyncedAt) || '—'}</td>
                  <td className="px-3 py-1.5 text-slate-500">{t.kind === 'core' ? 'live + every minute' : t.nextDueAt ? inFuture(t.nextDueAt) : t.kind === 'per-patient' ? 'nightly full sync' : '—'}</td>
                </tr>
              );
            })}
            {withData.length === 0 && (
              <tr><td colSpan={4} className="px-3 py-3 text-center text-slate-400">Nothing copied yet for this clinic.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {notYet.length > 0 && (
        <details className="mt-2 text-[11px] text-slate-500">
          <summary className="cursor-pointer">{notYet.length} screen{notYet.length === 1 ? '' : 's'} with no records yet</summary>
          <p className="mt-1">{notYet.map(label).join(', ')}. Either Open Dental has nothing for them, or the sync hasn't reached them yet.</p>
        </details>
      )}
    </section>
  );
}

function Fact({ title, value, hint, warn }: { title: string; value: string; hint: string; warn?: boolean }) {
  return (
    <div className="rounded-xl border border-slate-200 p-3">
      <p className="text-[10.5px] font-semibold uppercase tracking-wide text-slate-400">{title}</p>
      <p className={`mt-1 text-[12.5px] font-bold ${warn ? 'text-amber-700' : 'text-slate-800'}`}>{value}</p>
      <p className="mt-0.5 text-[10.5px] text-slate-400">{hint}</p>
    </div>
  );
}

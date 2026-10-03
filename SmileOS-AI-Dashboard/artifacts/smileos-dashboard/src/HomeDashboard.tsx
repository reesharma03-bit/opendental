import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity, ArrowDownRight, ArrowRight, ArrowUpRight, BellRing, CalendarDays, Check, CheckCircle2, ChevronDown,
  ChevronLeft, ChevronRight, CircleAlert, CircleDollarSign, ClipboardList, Clock3, FileText, LoaderCircle, Minus,
  RefreshCw, ShieldCheck, Stethoscope, UserRoundPlus, Users, Wallet,
} from 'lucide-react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { checkInAppointment, getDashboardSummary, type DashboardAppointment, type DashboardSummary } from './lib/backendDashboard';

// Amounts are shown in the practice currency; Open Dental itself stores plain numbers.
const LOCALE = 'en-IN';
const CURRENCY = 'INR';
const REFRESH_MS = 60_000;
const PAGE_SIZE = 8;

const money = (value: number | null | undefined) => value == null ? '—'
  : new Intl.NumberFormat(LOCALE, { style: 'currency', currency: CURRENCY, maximumFractionDigits: 0 }).format(value);
const moneyShort = (value: number) => new Intl.NumberFormat(LOCALE, {
  style: 'currency', currency: CURRENCY, notation: 'compact', maximumFractionDigits: 1,
}).format(value);
const count = (value: number | null | undefined) => value == null ? '—' : value.toLocaleString(LOCALE);
const timeOf = (iso: string | null) => iso
  ? new Intl.DateTimeFormat(LOCALE, { hour: 'numeric', minute: '2-digit' }).format(new Date(iso)) : '—';
const dayTimeOf = (iso: string) => new Intl.DateTimeFormat(LOCALE, { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }).format(new Date(iso));
const dateOf = (iso: string) => new Intl.DateTimeFormat(LOCALE, { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(iso));

/** Percentage change, or null when there is nothing to compare with. */
function change(current: number | null | undefined, previous: number | null | undefined): number | null {
  if (current == null || previous == null || previous === 0) return null;
  return ((current - previous) / previous) * 100;
}

const TONES = ['bg-sky-100 text-sky-700', 'bg-violet-100 text-violet-700', 'bg-amber-100 text-amber-700', 'bg-rose-100 text-rose-700', 'bg-emerald-100 text-emerald-700'];
const toneFor = (patNum: number) => TONES[Math.abs(patNum) % TONES.length];
const initialsOf = (name: string) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]!.toUpperCase()).join('') || '?';

const STATUS_STYLE: Record<string, { pill: string; color: string }> = {
  Scheduled: { pill: 'bg-blue-50 text-blue-700', color: '#315fe7' },
  'Checked in': { pill: 'bg-violet-50 text-violet-700', color: '#a99bec' },
  Completed: { pill: 'bg-emerald-50 text-emerald-700', color: '#56b6c1' },
  Broken: { pill: 'bg-rose-50 text-rose-600', color: '#f08c8c' },
};

function rowStatus(row: DashboardAppointment): string {
  if (row.status === 'Complete' || row.dismissed) return 'Completed';
  if (row.status === 'Broken') return 'Broken';
  if (row.arrived) return 'Checked in';
  return 'Scheduled';
}

function MetricCard({ label, value, delta, deltaText, icon: Icon, iconTone, note, testId }: {
  label: string; value: string; delta?: number | null; deltaText?: string; icon: typeof Users; iconTone: string; note: string; testId: string;
}) {
  const text = deltaText ?? (delta == null ? null : `${delta > 0 ? '+' : ''}${delta.toFixed(1)}%`);
  const tone = delta == null || delta === 0 ? 'bg-slate-100 text-slate-500' : delta > 0 ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-600';
  return (
    <section className="rounded-xl border border-slate-200/75 bg-white p-6 shadow-[0_2px_10px_rgba(26,49,91,0.025)] transition hover:-translate-y-0.5 hover:shadow-[0_8px_24px_rgba(26,49,91,0.07)]" data-testid={testId}>
      <div className="flex items-start justify-between"><p className="text-[12px] font-semibold text-slate-500">{label}</p><span className={`flex h-9 w-9 items-center justify-center rounded-xl ${iconTone}`}><Icon size={18} strokeWidth={1.9} /></span></div>
      <div className="mt-3 flex items-end justify-between gap-2">
        <p className="font-[Manrope] text-[27px] font-extrabold leading-none tracking-[-1.2px] text-slate-900">{value}</p>
        {text && <span className={`mb-0.5 inline-flex items-center gap-0.5 rounded-md px-1.5 py-1 text-[10px] font-bold ${tone}`}>{delta == null || delta === 0 ? <Minus size={12} /> : delta > 0 ? <ArrowUpRight size={12} /> : <ArrowDownRight size={12} />}{text}</span>}
      </div>
      <p className="mt-2.5 text-[10px] text-slate-400">{note}</p>
    </section>
  );
}

function Avatar({ name, patNum }: { name: string; patNum: number }) {
  return <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[10px] font-bold ${toneFor(patNum)}`}>{initialsOf(name)}</span>;
}

export default function HomeDashboard({ search, announce, onNavigate, onNewAppointment, onAddPatient }: {
  search: string;
  announce: (message: string) => void;
  onNavigate: (name: string) => void;
  onNewAppointment: () => void;
  onAddPatient: () => void;
}) {
  const [data, setData] = useState<DashboardSummary | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [lastLoaded, setLastLoaded] = useState<Date | null>(null);
  const [period, setPeriod] = useState<'This week' | 'Last week'>('This week');
  const [page, setPage] = useState(0);
  const [checkingIn, setCheckingIn] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await getDashboardSummary());
      setError('');
      setLastLoaded(new Date());
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => { if (!document.hidden) void load(); }, REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [load]);

  const appointments = data?.appointments ?? null;
  const visible = useMemo(() => (appointments?.list ?? []).filter((row) =>
    `${row.patientName} ${row.treatment} ${row.provider ?? ''} ${rowStatus(row)}`.toLowerCase().includes(search.toLowerCase())), [appointments, search]);
  const pages = Math.max(1, Math.ceil(visible.length / PAGE_SIZE));
  const shown = visible.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);
  useEffect(() => { setPage(0); }, [search]);

  const statusCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    (appointments?.list ?? []).forEach((row) => { const s = rowStatus(row); counts[s] = (counts[s] ?? 0) + 1; });
    return counts;
  }, [appointments]);
  const statusTotal = Object.values(statusCounts).reduce((a, b) => a + b, 0);
  const donut = useMemo(() => {
    if (!statusTotal) return '#eef1f6';
    let angle = 0;
    return `conic-gradient(${Object.entries(statusCounts).map(([status, n]) => {
      const start = angle; angle += (n / statusTotal) * 360;
      return `${STATUS_STYLE[status]?.color ?? '#cbd5e1'} ${start}deg ${angle}deg`;
    }).join(', ')})`;
  }, [statusCounts, statusTotal]);

  const minutesToNext = appointments?.nextAt ? Math.round((new Date(appointments.nextAt).getTime() - Date.now()) / 60000) : null;
  const daily = period === 'This week' ? data?.collections?.dailyThisWeek : data?.collections?.dailyLastWeek;
  const periodTotal = period === 'This week' ? data?.collections?.thisWeek : data?.collections?.lastWeek;
  const recent = (data?.recentPatients ?? []).filter((p) => p.name.toLowerCase().includes(search.toLowerCase()));
  const attention = data?.attention;
  const billing = data?.billingVisible !== false;
  const sync = data?.sync;

  const checkIn = async (row: DashboardAppointment) => {
    setCheckingIn(row.aptNum);
    try {
      await checkInAppointment(row.aptNum);
      announce(`${row.patientName} checked in. Saved here and sent to Open Dental.`);
      await load();
    } catch (e) {
      announce(`Could not check in ${row.patientName}: ${(e as Error).message}`);
    } finally {
      setCheckingIn(null);
    }
  };

  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const dateLabel = new Intl.DateTimeFormat(LOCALE, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date());
  const weekdayLabel = new Intl.DateTimeFormat(LOCALE, { weekday: 'long' }).format(new Date()).toUpperCase();
  const weekdayName = new Intl.DateTimeFormat(LOCALE, { weekday: 'long' }).format(new Date());

  const attentionItems: { text: string; tone: string; target?: string }[] = [];
  if (attention?.recallsDueNext7Days) attentionItems.push({ text: `${attention.recallsDueNext7Days} patient${attention.recallsDueNext7Days === 1 ? ' is' : 's are'} due for recall in the next 7 days.`, tone: 'text-cyan-200' });
  if (attention?.openClaims?.count) attentionItems.push({ text: `${attention.openClaims.count} insurance claim${attention.openClaims.count === 1 ? '' : 's'} still open (${money(attention.openClaims.amount)}).`, tone: 'text-amber-200' });
  if (attention?.plannedAppointments) attentionItems.push({ text: `${attention.plannedAppointments} planned appointment${attention.plannedAppointments === 1 ? '' : 's'} not yet scheduled.`, tone: 'text-cyan-200', target: 'Appointments' });
  if (attention?.brokenThisWeek) attentionItems.push({ text: `${attention.brokenThisWeek} broken appointment${attention.brokenThisWeek === 1 ? '' : 's'} this week.`, tone: 'text-rose-200', target: 'Appointments' });
  if (sync?.failedChanges) attentionItems.push({ text: `${sync.failedChanges} change${sync.failedChanges === 1 ? '' : 's'} could not be sent to Open Dental.`, tone: 'text-rose-200' });
  if (sync?.pendingChanges) attentionItems.push({ text: `${sync.pendingChanges} change${sync.pendingChanges === 1 ? '' : 's'} waiting to reach Open Dental.`, tone: 'text-amber-200' });

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9" data-testid="screen-home-dashboard">
      <section className="mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[11px] font-semibold text-slate-500"><span className={`h-1.5 w-1.5 rounded-full ${error ? 'bg-rose-500' : 'bg-emerald-500'}`} /> LIVE DATA <span className="text-slate-300">·</span> {weekdayLabel}</p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]" data-testid="text-greeting">{greeting}<span className="text-blue-600">.</span></h1>
          <p className="mt-1.5 text-[12px] text-slate-500" data-testid="text-today-date">{dateLabel} <span className="mx-1.5 text-slate-300">·</span> Here’s your practice at a glance.</p>
        </div>
        <div className="flex items-center gap-2">
          <button type="button" onClick={() => void load()} disabled={loading} data-testid="button-refresh-dashboard" className="flex h-9 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 text-[11px] font-semibold text-slate-600 shadow-sm hover:border-slate-300 disabled:opacity-60">
            <RefreshCw size={14} className={`text-slate-400 ${loading ? 'animate-spin' : ''}`} /> {lastLoaded ? `Updated ${timeOf(lastLoaded.toISOString())}` : 'Loading…'}
          </button>
          <button type="button" onClick={onNewAppointment} data-testid="button-new-appointment" className="flex h-9 items-center gap-2 rounded-xl bg-[#315fe7] px-3.5 text-[11px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf]"><span className="text-[16px] font-normal leading-none">+</span> New appointment</button>
        </div>
      </section>

      {error && (
        <div role="alert" className="mb-5 flex items-start gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] text-rose-700" data-testid="dashboard-error">
          <CircleAlert size={15} className="mt-0.5 shrink-0" />
          <span className="flex-1"><b>Could not load the dashboard.</b> {error}</span>
          <button type="button" onClick={() => void load()} className="rounded-lg border border-rose-300 px-3 py-1 font-bold hover:bg-rose-100">Retry</button>
        </div>
      )}
      {!data && !error && <div className="flex items-center gap-2 py-10 text-[12px] text-slate-500"><LoaderCircle size={16} className="animate-spin text-blue-600" /> Loading practice data…</div>}

      {data && (
        <>
          <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Practice key metrics">
            <MetricCard label="Today's appointments" value={count(appointments?.today)} delta={change(appointments?.today, appointments?.sameDayLastWeek)}
              icon={CalendarDays} iconTone="bg-blue-50 text-blue-600" testId="card-kpi-appointments"
              note={appointments ? `${appointments.sameDayLastWeek} last ${weekdayName}` : 'Appointments not available'} />
            <MetricCard label="Doctors on duty" value={count(data.providers?.onDuty)} deltaText={data.providers?.total ? `of ${data.providers.total}` : undefined}
              icon={Stethoscope} iconTone="bg-blue-50 text-blue-600" testId="card-kpi-doctors"
              note={data.providers?.total ? 'Providers with appointments today' : 'Providers with appointments today · provider list not synced yet'} />
            <MetricCard label="Active patients" value={count(data.patients?.active)}
              delta={change(data.patients?.newLast30Days, data.patients?.newPrevious30Days)}
              deltaText={data.patients ? `+${data.patients.newLast30Days} new` : undefined}
              icon={Users} iconTone="bg-cyan-50 text-cyan-700" testId="card-kpi-patients"
              note={data.patients ? `${count(data.patients.total)} on file · new = first visit in the last 30 days` : 'Patients not available'} />
            {billing && <MetricCard label="Production this month" value={money(data.production?.monthToDate)}
              delta={change(data.production?.monthToDate, data.production?.lastMonthToDate)}
              icon={CircleDollarSign} iconTone="bg-emerald-50 text-emerald-700" testId="card-kpi-production"
              note={data.production ? `Completed procedures · ${money(data.production.lastMonthToDate)} same point last month` : 'Procedure logs not available'} />}
            {billing && <MetricCard label="Collections this week" value={money(data.collections?.thisWeek)}
              delta={change(data.collections?.thisWeek, data.collections?.lastWeek)}
              icon={Wallet} iconTone="bg-emerald-50 text-emerald-700" testId="card-kpi-collections"
              note={data.collections ? `Payments received · ${money(data.collections.lastWeek)} last week` : 'Payments not synced yet'} />}
            {billing && <MetricCard label="Open insurance claims" value={count(attention?.openClaims?.count)}
              deltaText={attention?.openClaims ? money(attention.openClaims.amount) : undefined}
              icon={FileText} iconTone="bg-amber-50 text-amber-700" testId="card-kpi-claims"
              note={attention?.openClaims ? 'Unsent, on hold, waiting or sent' : 'Claims not synced yet'} />}
            <MetricCard label="Recalls due (7 days)" value={count(attention?.recallsDueNext7Days)}
              icon={BellRing} iconTone="bg-cyan-50 text-cyan-700" testId="card-kpi-recalls"
              note={attention?.recallsDueNext7Days == null ? 'Recalls not synced yet' : 'Due and not yet scheduled'} />
            <MetricCard label="Planned, not scheduled" value={count(attention?.plannedAppointments)}
              deltaText={attention?.brokenThisWeek ? `${attention.brokenThisWeek} broken` : undefined}
              delta={attention?.brokenThisWeek ? -1 : null}
              icon={ClipboardList} iconTone="bg-amber-50 text-amber-700" testId="card-kpi-planned"
              note="Treatment waiting to be booked · broken this week" />
          </section>

          <section className="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1.72fr)_minmax(320px,.88fr)]">
            <div className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]" data-testid="section-todays-appointments">
              <div className="flex flex-wrap items-center justify-between gap-3 px-5 pb-4 pt-5 sm:px-6">
                <div><div className="flex items-center gap-2"><h2 className="font-[Manrope] text-[15px] font-extrabold tracking-[-.3px] text-slate-800">Today's appointments</h2><span className="rounded-md bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700">{appointments?.list.length ?? 0}</span></div><p className="mt-1 text-[10px] text-slate-400">Your schedule for today</p></div>
                <button type="button" onClick={() => onNavigate('Appointments')} data-testid="button-view-all-appointments" className="flex items-center gap-1 text-[10px] font-bold text-blue-600 hover:text-blue-800">View schedule <ArrowRight size={13} /></button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] border-collapse text-left">
                  <thead><tr className="border-y border-slate-100 bg-slate-50/65 text-[9px] font-bold uppercase tracking-[.8px] text-slate-400"><th className="px-5 py-2.5 sm:px-6">Time</th><th className="px-3 py-2.5">Patient</th><th className="px-3 py-2.5">Treatment</th><th className="px-3 py-2.5">Dentist</th><th className="px-3 py-2.5">Status</th><th className="px-5 py-2.5 sm:px-6">Action</th></tr></thead>
                  <tbody>
                    {shown.map((row, index) => {
                      const status = rowStatus(row);
                      return (
                        <tr key={row.aptNum} className="border-b border-slate-100/80 last:border-0 hover:bg-slate-50/50" data-testid={`row-appointment-${index}`}>
                          <td className="whitespace-nowrap px-5 py-3.5 text-[10px] font-semibold text-slate-500 sm:px-6"><span className="flex items-center gap-1.5"><Clock3 size={12} className="text-slate-400" />{timeOf(row.time)}</span></td>
                          <td className="whitespace-nowrap px-3 py-3"><div className="flex items-center gap-2.5"><Avatar name={row.patientName} patNum={row.patNum} /><div><p className="text-[11px] font-bold text-slate-700">{row.patientName}</p><p className="mt-0.5 text-[9px] text-slate-400">{row.minutes ? `${row.minutes} min` : `#${row.patNum}`}</p></div></div></td>
                          <td className="max-w-[220px] truncate px-3 py-3 text-[10px] text-slate-500" title={row.treatment}>{row.treatment}</td>
                          <td className="whitespace-nowrap px-3 py-3 text-[10px] text-slate-500">{row.provider ?? '—'}</td>
                          <td className="whitespace-nowrap px-3 py-3"><span className={`rounded-full px-2 py-1 text-[9px] font-semibold ${STATUS_STYLE[status]?.pill ?? 'bg-slate-100 text-slate-600'}`}>{status}</span></td>
                          <td className="whitespace-nowrap px-5 py-3 sm:px-6">
                            {status === 'Scheduled'
                              ? <button type="button" onClick={() => void checkIn(row)} disabled={checkingIn === row.aptNum} data-testid={`button-check-in-${index}`} className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-[9px] font-semibold text-slate-600 transition hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700 disabled:opacity-50">{checkingIn === row.aptNum ? 'Saving…' : 'Check in'}</button>
                              : status === 'Checked in'
                                ? <span className="inline-flex items-center gap-1 text-[9px] font-semibold text-emerald-600"><Check size={12} /> Checked in</span>
                                : <span className="text-[9px] text-slate-400">—</span>}
                          </td>
                        </tr>
                      );
                    })}
                    {shown.length === 0 && <tr><td colSpan={6} className="px-6 py-10 text-center text-xs text-slate-400" data-testid="empty-appointments">{search ? `No appointments match “${search}”.` : appointments ? 'No appointments today.' : 'Appointments are not available.'}</td></tr>}
                  </tbody>
                </table>
              </div>
              <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3 sm:px-6">
                <span className="text-[10px] text-slate-400">Showing {shown.length} of {visible.length} appointments</span>
                <div className="flex items-center gap-1">
                  <button type="button" aria-label="Previous appointments" data-testid="button-previous-appointments" disabled={page === 0} onClick={() => setPage((p) => p - 1)} className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-400 hover:bg-slate-50 disabled:opacity-40"><ChevronLeft size={14} /></button>
                  <span className="px-2 text-[10px] font-semibold text-slate-500">{page + 1} / {pages}</span>
                  <button type="button" aria-label="Next appointments" data-testid="button-next-appointments" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)} className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-400 hover:bg-slate-50 disabled:opacity-40"><ChevronRight size={14} /></button>
                </div>
              </div>
            </div>

            <div className="flex flex-col gap-5">
              <section className="rounded-2xl border border-slate-200/75 bg-white p-5 shadow-[0_2px_10px_rgba(26,49,91,0.025)]" data-testid="section-appointment-status">
                <div><h2 className="font-[Manrope] text-[14px] font-extrabold text-slate-800">Appointment status</h2><p className="mt-1 text-[10px] text-slate-400">Today's schedule at a glance</p></div>
                <div className="mt-5 flex items-center gap-5">
                  <div className="relative flex h-[112px] w-[112px] shrink-0 items-center justify-center rounded-full" style={{ background: donut }}>
                    <div className="flex h-[82px] w-[82px] flex-col items-center justify-center rounded-full bg-white"><span className="font-[Manrope] text-[22px] font-extrabold text-slate-800">{statusTotal}</span><span className="text-[9px] text-slate-400">total today</span></div>
                  </div>
                  <div className="min-w-0 flex-1 space-y-2.5">
                    {Object.keys(STATUS_STYLE).map((status) => <div key={status} className="flex items-center gap-2"><span className="h-2 w-2 rounded-full" style={{ background: STATUS_STYLE[status]!.color }} /><span className="flex-1 text-[10px] text-slate-500">{status}</span><span className="text-[10px] font-bold text-slate-700">{statusCounts[status] ?? 0}</span></div>)}
                  </div>
                </div>
                <div className="mt-4 flex items-center gap-2 rounded-xl bg-emerald-50/75 px-3 py-2.5 text-[10px] text-emerald-800">
                  <CheckCircle2 size={14} className="shrink-0 text-emerald-600" />
                  <span>{minutesToNext == null ? 'No more appointments waiting today.' : minutesToNext <= 0 ? <><b>Next patient is due now</b> ({timeOf(appointments!.nextAt)})</> : <><b>Next appointment</b> in {minutesToNext} min ({timeOf(appointments!.nextAt)})</>}</span>
                </div>
              </section>

              <section className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-[#253f9e] via-[#3154c5] to-[#4867d5] p-5 text-white shadow-[0_8px_24px_rgba(42,74,173,.16)]" data-testid="card-needs-attention">
                <div className="absolute -right-9 -top-11 h-36 w-36 rounded-full border border-white/10" /><div className="absolute -right-1 -top-4 h-24 w-24 rounded-full border border-white/10" />
                <div className="relative flex items-start justify-between">
                  <div className="flex items-center gap-2"><span className="flex h-7 w-7 items-center justify-center rounded-lg bg-white/15"><Activity size={15} /></span><span className="text-[12px] font-bold">Needs attention</span></div>
                  <span className="rounded-md border border-white/15 bg-white/10 px-2 py-1 text-[8px] font-bold uppercase tracking-[1px] text-blue-100">Today</span>
                </div>
                <div className="relative mt-4 space-y-3 border-t border-white/15 pt-3">
                  {attentionItems.length === 0
                    ? <p className="text-[10px] leading-[1.55] text-blue-50">Nothing needs attention right now.</p>
                    : attentionItems.map((item) => (
                      <p key={item.text} className="flex gap-2.5 text-[10px] leading-[1.55] text-blue-50">
                        <span className={`mt-0.5 ${item.tone}`}><CircleAlert size={13} /></span>
                        <span className="flex-1">{item.text}</span>
                        {item.target && <button type="button" onClick={() => onNavigate(item.target!)} className="shrink-0 font-bold text-white underline-offset-2 hover:underline">Open</button>}
                      </p>
                    ))}
                </div>
                <p className="relative mt-4 border-t border-white/15 pt-3 text-[9px] text-blue-100">
                  {sync?.running ? 'Open Dental sync is running…'
                    : sync?.lastFullSync ? `Last full sync with Open Dental: ${dateOf(sync.lastFullSync.at)} ${timeOf(sync.lastFullSync.at)} (${sync.lastFullSync.status.replaceAll('_', ' ')})`
                      : 'No full sync with Open Dental yet — use Force Sync in the sidebar.'}
                </p>
              </section>
            </div>
          </section>

          <section className={`mt-5 grid grid-cols-1 gap-5 ${billing ? 'xl:grid-cols-[minmax(0,1.22fr)_minmax(330px,.78fr)]' : ''}`}>
            {billing && <section className="rounded-2xl border border-slate-200/75 bg-white p-5 shadow-[0_2px_10px_rgba(26,49,91,0.025)] sm:p-6" data-testid="section-revenue-chart">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <h2 className="font-[Manrope] text-[14px] font-extrabold text-slate-800">Collections</h2>
                    {(() => {
                      const d = change(data.collections?.thisWeek, data.collections?.lastWeek);
                      return d == null ? null : <span className={`flex items-center gap-1 rounded-md px-1.5 py-1 text-[9px] font-bold ${d >= 0 ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-600'}`}>{d >= 0 ? <ArrowUpRight size={11} /> : <ArrowDownRight size={11} />} {Math.abs(d).toFixed(1)}%</span>;
                    })()}
                  </div>
                  <p className="mt-1 text-[10px] text-slate-400">Payments received per day</p>
                </div>
                <button type="button" onClick={() => setPeriod((value) => value === 'This week' ? 'Last week' : 'This week')} data-testid="button-revenue-period" className="flex h-8 items-center gap-2 rounded-lg border border-slate-200 px-2.5 text-[10px] font-semibold text-slate-600 hover:bg-slate-50">{period}<ChevronDown size={13} /></button>
              </div>
              <div className="mt-3 flex items-end gap-2"><span className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900" data-testid="text-revenue-total">{money(periodTotal)}</span><span className="mb-1 text-[9px] text-slate-400">{period.toLowerCase()}</span></div>
              <div className="mt-3 h-[186px] w-full" data-testid="chart-weekly-revenue">
                {daily
                  ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={daily} margin={{ top: 12, right: 4, left: -8, bottom: 0 }}>
                        <defs><linearGradient id="revenueFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#2563eb" stopOpacity={0.19} /><stop offset="95%" stopColor="#2563eb" stopOpacity={0.005} /></linearGradient></defs>
                        <CartesianGrid vertical={false} stroke="#eef1f6" strokeDasharray="3 4" />
                        <XAxis dataKey="day" axisLine={false} tickLine={false} tick={{ fill: '#9aa6b7', fontSize: 10 }} dy={8} />
                        <YAxis axisLine={false} tickLine={false} tick={{ fill: '#9aa6b7', fontSize: 9 }} tickFormatter={(value: number) => moneyShort(value)} />
                        <Tooltip cursor={{ stroke: '#bfdbfe', strokeDasharray: '4 4' }} contentStyle={{ border: '1px solid #e5e7eb', borderRadius: 10, fontSize: 12, boxShadow: '0 5px 16px rgba(31,53,88,.08)' }} formatter={(value: number) => [money(value), 'Collected']} labelFormatter={(_, items) => items?.[0]?.payload?.date ? dateOf(items[0].payload.date) : ''} />
                        <Area type="monotone" dataKey="value" stroke="#2563eb" strokeWidth={2.5} fill="url(#revenueFill)" activeDot={{ r: 4, strokeWidth: 3, stroke: '#fff', fill: '#2563eb' }} dot={{ r: 3, fill: '#fff', stroke: '#2563eb', strokeWidth: 2 }} />
                      </AreaChart>
                    </ResponsiveContainer>
                  )
                  : <p className="flex h-full items-center justify-center text-[11px] text-slate-400">Payments have not been synced from Open Dental yet.</p>}
              </div>
            </section>}

            <section className="rounded-2xl border border-slate-200/75 bg-white p-5 shadow-[0_2px_10px_rgba(26,49,91,0.025)] sm:p-6" data-testid="section-recent-patients">
              <div className="flex items-start justify-between"><div><h2 className="font-[Manrope] text-[14px] font-extrabold text-slate-800">Recent patients</h2><p className="mt-1 text-[10px] text-slate-400">Newest patients at your practice</p></div><button type="button" onClick={() => onNavigate('Patients')} data-testid="button-view-patients" className="mt-0.5 flex items-center gap-1 text-[10px] font-bold text-blue-600 hover:text-blue-800">View all <ArrowRight size={12} /></button></div>
              <div className="mt-4 divide-y divide-slate-100">
                {recent.map((patient) => (
                  <div key={patient.patNum} className="flex items-center gap-2.5 py-2.5 first:pt-0 last:pb-0" data-testid={`row-patient-${patient.patNum}`}>
                    <Avatar name={patient.name} patNum={patient.patNum} />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[11px] font-bold text-slate-700">{patient.name}</p>
                      <p className="mt-0.5 truncate text-[9px] text-slate-400">{patient.nextAppointment ? `Next: ${dayTimeOf(patient.nextAppointment)}` : patient.firstVisit ? `First visit ${dateOf(patient.firstVisit)}` : 'No upcoming appointment'}</p>
                    </div>
                    <span className="rounded-md bg-slate-50 px-1.5 py-1 text-[8px] font-semibold text-slate-400">#{patient.patNum}</span>
                    <button type="button" onClick={() => onNavigate('Patients')} aria-label={`Open ${patient.name}`} data-testid={`button-patient-${patient.patNum}`} className="ml-0.5 flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 hover:bg-blue-50 hover:text-blue-600"><ArrowRight size={13} /></button>
                  </div>
                ))}
                {recent.length === 0 && <p className="py-9 text-center text-xs text-slate-400" data-testid="empty-patients">{search ? `No patients match “${search}”.` : 'No patients yet.'}</p>}
              </div>
              <button type="button" onClick={onAddPatient} data-testid="button-add-patient" className="mt-4 flex h-9 w-full items-center justify-center gap-2 rounded-xl border border-dashed border-slate-200 text-[10px] font-semibold text-slate-500 transition hover:border-blue-200 hover:bg-blue-50/50 hover:text-blue-700"><UserRoundPlus size={14} /> Add a patient</button>
            </section>
          </section>

          <footer className="mt-6 flex flex-col items-center justify-between gap-2 border-t border-slate-200/75 pt-4 text-[9px] text-slate-500 sm:flex-row">
            <span className="flex items-center gap-1.5"><ShieldCheck size={12} className="text-emerald-600" /> SmileOS practice workspace</span>
            <span>Live data from our database{data.unavailable.length ? ` · not yet available: ${data.unavailable.join(', ')}` : ''} · refreshes every minute</span>
          </footer>
        </>
      )}
    </div>
  );
}

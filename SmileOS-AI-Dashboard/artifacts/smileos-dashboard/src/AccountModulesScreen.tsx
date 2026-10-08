import { useCallback, useEffect, useState } from 'react';
import { CircleAlert, LoaderCircle, RefreshCw, Users, Wallet } from 'lucide-react';
import PatientPicker from './components/PatientPicker';
import {
  formatMoney, getAging, getPatientBalances, getServiceDateView,
  type Aging, type LedgerLine, type PatientBalance,
} from './lib/backendAccountModules';

type Tab = 'aging' | 'balances' | 'ledger';

const TABS: { id: Tab; label: string }[] = [
  { id: 'aging', label: 'Aging' },
  { id: 'balances', label: 'Family balances' },
  { id: 'ledger', label: 'Ledger by service date' },
];

/** Open Dental's Account Module for a patient's family, read live from Open Dental. */
export default function AccountModulesScreen() {
  const [patNum, setPatNum] = useState<number | null>(null);
  const [tab, setTab] = useState<Tab>('aging');
  const [family, setFamily] = useState(true);
  const [aging, setAging] = useState<Aging | null>(null);
  const [balances, setBalances] = useState<PatientBalance[]>([]);
  const [ledger, setLedger] = useState<LedgerLine[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  const onPickerError = useCallback((message: string) => setError(message), []);

  useEffect(() => {
    if (!patNum) return;
    let live = true;
    setLoading(true);
    setError('');
    const load = tab === 'aging' ? getAging(patNum).then((v) => { if (live) setAging(v); })
      : tab === 'balances' ? getPatientBalances(patNum).then((v) => { if (live) setBalances(v); })
        : getServiceDateView(patNum, family).then((v) => { if (live) setLedger(v); });
    load.catch((e: Error) => { if (live) setError(e.message); }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [patNum, tab, family, reload]);

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9" data-testid="screen-account-modules">
      <div className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[11px] font-semibold text-slate-400"><Wallet size={13} className="text-blue-600" /> INSURANCE &amp; BILLING</p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]">Account<span className="text-blue-600">.</span></h1>
          <p className="mt-1.5 text-[12px] text-slate-500">A family’s balances, aging and ledger, as Open Dental’s Account Module shows them.</p>
        </div>
        <button type="button" onClick={() => setReload((n) => n + 1)} disabled={!patNum || loading}
          className="flex h-10 items-center gap-2 self-start rounded-xl border border-slate-200 bg-white px-3 text-[11px] font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 sm:self-auto">
          <RefreshCw size={13} className={loading ? 'animate-spin' : ''} /> Refresh
        </button>
      </div>

      <p role="note" className="mb-5 rounded-2xl border border-blue-200/80 bg-blue-50/60 px-4 py-3 text-[11px] leading-5 text-blue-950">
        <b>Live from Open Dental.</b> Open Dental works these figures out from the whole ledger each time, so they aren’t stored in our database and need Open Dental to be reachable.
      </p>

      <PatientPicker id="account-patient" value={patNum} onError={onPickerError}
        onChange={(value) => { setPatNum(value); setAging(null); setBalances([]); setLedger([]); }} />

      {error && <div role="alert" className="mb-4 flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-medium text-rose-700"><CircleAlert size={15} className="mt-0.5 shrink-0" />{error}</div>}

      {patNum && (
        <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]">
          <div className="flex flex-col gap-3 border-b border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
            <div role="tablist" aria-label="Account views" className="flex flex-wrap gap-1 rounded-xl bg-slate-100 p-1">
              {TABS.map((t) => (
                <button key={t.id} role="tab" type="button" aria-selected={tab === t.id} onClick={() => setTab(t.id)} data-testid={`tab-account-${t.id}`}
                  className={`h-8 rounded-lg px-3 text-[11px] font-semibold transition ${tab === t.id ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-800'}`}>{t.label}</button>
              ))}
            </div>
            {tab === 'ledger' && (
              <label className="flex items-center gap-2 text-[11px] font-semibold text-slate-600">
                <input type="checkbox" checked={family} onChange={(e) => setFamily(e.target.checked)} className="h-4 w-4 rounded border-slate-300 text-blue-600" />
                <Users size={13} /> Whole family
              </label>
            )}
          </div>

          {loading ? (
            <div className="flex items-center justify-center gap-2 px-5 py-14 text-[12px] text-slate-500"><LoaderCircle size={17} className="animate-spin text-blue-600" /> Asking Open Dental…</div>
          ) : error ? null : tab === 'aging' ? (
            aging && <AgingView aging={aging} />
          ) : tab === 'balances' ? (
            <BalancesView balances={balances} />
          ) : (
            <LedgerView lines={ledger} />
          )}
        </section>
      )}
    </div>
  );
}

function Stat({ label, value, tone = 'slate', hint }: { label: string; value: number; tone?: 'slate' | 'amber' | 'rose' | 'emerald' | 'blue'; hint?: string }) {
  const tones = { slate: 'text-slate-900', amber: 'text-amber-700', rose: 'text-rose-700', emerald: 'text-emerald-700', blue: 'text-blue-700' };
  return (
    <div className="rounded-xl border border-slate-200/80 bg-white px-4 py-3">
      <p className="text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">{label}</p>
      <p className={`mt-1 font-[Manrope] text-[19px] font-extrabold tabular-nums ${tones[tone]}`}>{formatMoney(value)}</p>
      {hint && <p className="mt-0.5 text-[9px] text-slate-400">{hint}</p>}
    </div>
  );
}

function AgingView({ aging }: { aging: Aging }) {
  const buckets = [
    { label: '0–30 days', value: aging.bal0to30 }, { label: '31–60 days', value: aging.bal31to60 },
    { label: '61–90 days', value: aging.bal61to90 }, { label: 'Over 90 days', value: aging.balOver90 },
  ];
  const max = Math.max(1, ...buckets.map((b) => Math.abs(b.value)));
  return (
    <div className="space-y-5 p-5 sm:p-6" data-testid="view-aging">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Family total" value={aging.total} hint="Before insurance" />
        <Stat label="Insurance estimate" value={aging.insEst} tone="blue" />
        <Stat label="Balance after insurance" value={aging.estBal} tone="amber" />
        <Stat label="Patient portion" value={aging.patEstBal} tone="rose" hint="After insurance and write-offs" />
      </div>
      <div>
        <p className="mb-2 text-[11px] font-bold text-slate-700">How old the balance is</p>
        <div className="space-y-2">
          {buckets.map((b, i) => (
            <div key={b.label} className="grid grid-cols-[100px_minmax(0,1fr)_90px] items-center gap-3 text-[11px]">
              <span className="text-slate-500">{b.label}</span>
              <span className="h-2.5 overflow-hidden rounded-full bg-slate-100">
                <span className={`block h-full rounded-full ${['bg-emerald-500', 'bg-amber-400', 'bg-orange-500', 'bg-rose-600'][i]}`} style={{ width: `${(Math.abs(b.value) / max) * 100}%` }} />
              </span>
              <span className="text-right font-semibold tabular-nums text-slate-800">{formatMoney(b.value)}</span>
            </div>
          ))}
        </div>
      </div>
      <p className="text-[10px] text-slate-400">Unearned / prepaid: <b className="text-slate-600">{formatMoney(aging.unearned)}</b></p>
    </div>
  );
}

function BalancesView({ balances }: { balances: PatientBalance[] }) {
  if (balances.length === 0) return <p className="px-5 py-12 text-center text-[11px] text-slate-500">No balances returned.</p>;
  return (
    <table className="w-full border-collapse text-left" data-testid="view-balances">
      <thead className="bg-[#fbfcfe] text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">
        <tr><th className="px-5 py-3 sm:px-6">Family member</th><th className="px-4 py-3">Patient #</th><th className="px-5 py-3 text-right sm:px-6">Balance</th></tr>
      </thead>
      <tbody>
        {balances.map((b, i) => (
          <tr key={`${b.patNum}-${i}`} className={`border-t border-slate-100 ${b.isFamilyTotal ? 'bg-slate-50 font-bold' : ''}`}>
            <td className="px-5 py-3 text-[12px] text-slate-800 sm:px-6">{b.name}</td>
            <td className="px-4 py-3 text-[11px] text-slate-500">{b.isFamilyTotal ? 'Guarantor' : `#${b.patNum}`}</td>
            <td className={`px-5 py-3 text-right text-[12px] tabular-nums sm:px-6 ${b.balance > 0 ? 'text-amber-700' : 'text-slate-700'}`}>{formatMoney(b.balance)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function LedgerView({ lines }: { lines: LedgerLine[] }) {
  if (lines.length === 0) return <p className="px-5 py-12 text-center text-[11px] text-slate-500">No charges or credits.</p>;
  return (
    <div className="overflow-x-auto" data-testid="view-ledger">
      <table className="w-full min-w-[900px] border-collapse text-left">
        <thead className="bg-[#fbfcfe] text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">
          <tr>
            <th className="px-5 py-3 sm:px-6">Service date</th><th className="px-4 py-3">Posted</th><th className="px-4 py-3">Patient</th>
            <th className="px-4 py-3">Type</th><th className="px-4 py-3">Description</th><th className="px-4 py-3">Provider</th>
            <th className="px-4 py-3 text-right">Charge</th><th className="px-4 py-3 text-right">Credit</th>
            <th className="px-4 py-3 text-right">Ins. balance</th><th className="px-5 py-3 text-right sm:px-6">Account balance</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((l, i) => (
            <tr key={`${l.objectType}-${l.primaryKey}-${i}`} className={`border-t border-slate-100 text-[11px] ${l.isDayTotal ? 'bg-slate-50 font-bold text-slate-800' : 'text-slate-600'}`}>
              <td className="px-5 py-2.5 sm:px-6">{l.serviceDate || ''}</td>
              <td className="px-4 py-2.5">{l.transDate || ''}</td>
              <td className="px-4 py-2.5">{l.patient}</td>
              <td className="px-4 py-2.5">{l.type}</td>
              <td className="px-4 py-2.5">{l.reference}</td>
              <td className="px-4 py-2.5">{l.provider}</td>
              <td className="px-4 py-2.5 text-right tabular-nums">{l.charge ? formatMoney(l.charge) : ''}</td>
              <td className="px-4 py-2.5 text-right tabular-nums text-emerald-700">{l.credit ? formatMoney(l.credit) : ''}</td>
              <td className="px-4 py-2.5 text-right tabular-nums">{l.insBal}</td>
              <td className="px-5 py-2.5 text-right tabular-nums sm:px-6">{l.acctBal}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

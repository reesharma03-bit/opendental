import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Check, CircleAlert, Edit2, Info, LoaderCircle, Plus, Receipt, Search, X } from 'lucide-react';
import PatientPicker, { fieldClass } from './components/PatientPicker';
import {
  createAdjustment, draftProblem, listAdjustments, listAdjustmentTypes, toDraft, updateAdjustment,
  type Adjustment, type AdjustmentDraft, type AdjustmentType,
} from './lib/backendAdjustments';
import { listProviders, type ProviderOption } from './lib/backendLookups';
import { formatMoney } from './lib/backendAccountModules';

const localToday = () => {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
};

const emptyDraft = (): AdjustmentDraft => ({
  adjDate: localToday(), adjAmt: '', adjType: '', provNum: '', procNum: '', procDate: '', note: '',
});

/** Account adjustments per patient: discounts, write-offs and extra charges. */
export default function AdjustmentsScreen() {
  const [patNum, setPatNum] = useState<number | null>(null);
  const [adjustments, setAdjustments] = useState<Adjustment[]>([]);
  const [types, setTypes] = useState<AdjustmentType[]>([]);
  const [providers, setProviders] = useState<ProviderOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [screenError, setScreenError] = useState('');
  const [formError, setFormError] = useState('');
  const [notice, setNotice] = useState('');
  const [filter, setFilter] = useState('');
  const [editing, setEditing] = useState<Adjustment | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [draft, setDraft] = useState<AdjustmentDraft>(emptyDraft());
  const onPickerError = useCallback((message: string) => setScreenError(message), []);

  useEffect(() => {
    listAdjustmentTypes().then(setTypes).catch(() => setTypes([]));
    listProviders().then(setProviders).catch(() => setProviders([]));
  }, []);

  useEffect(() => {
    if (!patNum) { setAdjustments([]); return; }
    let live = true;
    setLoading(true);
    listAdjustments(patNum)
      .then((rows) => { if (live) { setAdjustments(rows); setScreenError(''); } })
      .catch((error: Error) => { if (live) { setAdjustments([]); setScreenError(`Unable to load adjustments: ${error.message}`); } })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [patNum]);

  const typeOf = (defNum: number | string) => types.find((t) => t.defNum === Number(defNum));
  const providerName = (provNum: number) => providers.find((p) => p.ProvNum === provNum)?.name ?? (provNum ? `Provider #${provNum}` : '—');

  const shown = useMemo(() => {
    const term = filter.trim().toLowerCase();
    if (!term) return adjustments;
    return adjustments.filter((a) => `${a.adjTypeName} ${typeOf(a.adjType)?.name ?? ''} ${a.note} ${a.adjDate}`.toLowerCase().includes(term));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [adjustments, filter, types]);
  const totals = useMemo(() => ({
    added: adjustments.filter((a) => a.adjAmt > 0).reduce((sum, a) => sum + a.adjAmt, 0),
    reduced: adjustments.filter((a) => a.adjAmt < 0).reduce((sum, a) => sum + a.adjAmt, 0),
  }), [adjustments]);

  const openCreate = () => { setEditing(null); setDraft(emptyDraft()); setFormError(''); setFormOpen(true); };
  const openEdit = (adjustment: Adjustment) => { setEditing(adjustment); setDraft(toDraft(adjustment)); setFormError(''); setFormOpen(true); };
  const set = <K extends keyof AdjustmentDraft>(key: K, value: AdjustmentDraft[K]) => setDraft((current) => ({ ...current, [key]: value }));

  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!patNum) return;
    const problem = draftProblem(draft, typeOf(draft.adjType), localToday());
    if (problem) { setFormError(problem); return; }
    setSaving(true);
    setFormError('');
    try {
      if (editing) await updateAdjustment(editing, draft);
      else await createAdjustment(patNum, draft);
      setNotice(editing ? 'Adjustment updated. Saved here and sent to Open Dental.' : 'Adjustment added. Saved here and sent to Open Dental.');
      setFormOpen(false);
      setAdjustments(await listAdjustments(patNum));
    } catch (error) {
      setFormError((error as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const selectedType = typeOf(draft.adjType);

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9" data-testid="screen-adjustments">
      <div className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[11px] font-semibold text-slate-400"><Receipt size={13} className="text-blue-600" /> INSURANCE &amp; BILLING</p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]">Adjustments<span className="text-blue-600">.</span></h1>
          <p className="mt-1.5 text-[12px] text-slate-500">Discounts, write-offs and extra charges on a patient’s account.</p>
        </div>
        <button type="button" onClick={openCreate} disabled={!patNum || loading || saving} data-testid="button-create-adjustment"
          className="flex h-10 items-center justify-center gap-2 self-start rounded-xl bg-[#315fe7] px-4 text-[12px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf] disabled:cursor-not-allowed disabled:opacity-50 sm:self-auto">
          <Plus size={16} /> Add adjustment
        </button>
      </div>

      <div role="note" className="mb-5 flex items-start gap-3 rounded-2xl border border-blue-200/80 bg-blue-50/60 px-4 py-3.5 text-[11px] leading-5 text-blue-950">
        <Info size={16} className="mt-0.5 shrink-0 text-blue-600" />
        <p><b>Saved here first, then in Open Dental.</b> Open Dental doesn’t allow deleting adjustments through its API. To undo one, add an opposite adjustment or change it in Open Dental.</p>
      </div>

      {screenError && <div role="alert" className="mb-4 flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-medium text-rose-700"><CircleAlert size={15} className="mt-0.5 shrink-0" />{screenError}</div>}
      {notice && !screenError && <div role="status" className="mb-4 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-[11px] font-semibold text-emerald-700"><Check size={15} />{notice}</div>}

      <PatientPicker id="adjustments-patient" value={patNum} onError={onPickerError}
        onChange={(value) => { setPatNum(value); setNotice(''); setScreenError(''); }} />

      {patNum && (
        <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]" data-testid="section-adjustments">
          <div className="flex flex-col gap-4 border-b border-slate-100 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
            <div>
              <div className="flex items-center gap-2">
                <h2 className="font-[Manrope] text-[15px] font-extrabold tracking-[-.3px] text-slate-800">Account adjustments</h2>
                <span className="rounded-md bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700">{adjustments.length}</span>
              </div>
              <p className="mt-1 text-[10px] text-slate-400">
                <span className="text-amber-700">+{formatMoney(totals.added)} added</span> · <span className="text-emerald-700">{formatMoney(totals.reduced)} reduced</span>
              </p>
            </div>
            <label className="relative block w-full sm:max-w-[310px]">
              <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Filter by type, note or date" aria-label="Filter adjustments" className={`${fieldClass} mt-0 pl-9`} />
            </label>
          </div>

          {loading ? (
            <div className="flex items-center justify-center gap-2 px-5 py-14 text-[12px] text-slate-500"><LoaderCircle size={17} className="animate-spin text-blue-600" /> Loading adjustments…</div>
          ) : adjustments.length === 0 ? (
            <div className="px-5 py-14 text-center">
              <span className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-slate-100 text-slate-400"><Receipt size={19} /></span>
              <p className="mt-3 text-[12px] font-semibold text-slate-700">No adjustments for this patient</p>
              <p className="mt-1 text-[10px] text-slate-400">Adjustments are copied from Open Dental by the sync; new ones you add appear here straight away.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[820px] border-collapse text-left">
                <thead className="bg-[#fbfcfe] text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">
                  <tr>
                    <th className="px-5 py-3 sm:px-6">Date</th><th className="px-4 py-3">Type</th><th className="px-4 py-3 text-right">Amount</th>
                    <th className="px-4 py-3">Provider</th><th className="px-4 py-3">Procedure</th><th className="px-4 py-3">Note</th>
                    <th className="px-5 py-3 text-right sm:px-6">Edit</th>
                  </tr>
                </thead>
                <tbody>
                  {shown.map((a) => (
                    <tr key={a.adjNum} data-testid={`row-adjustment-${a.adjNum}`} className="border-t border-slate-100 hover:bg-slate-50/60">
                      <td className="px-5 py-3.5 text-[11px] font-semibold text-slate-700 sm:px-6">{a.adjDate || '—'}
                        {a.adjNum < 0 && <span className="ml-2 rounded bg-amber-50 px-1.5 py-0.5 text-[9px] font-bold text-amber-700">Sending…</span>}
                      </td>
                      <td className="px-4 py-3.5 text-[11px] text-slate-700">{a.adjTypeName || typeOf(a.adjType)?.name || `Type #${a.adjType}`}</td>
                      <td className={`px-4 py-3.5 text-right text-[12px] font-bold tabular-nums ${a.adjAmt < 0 ? 'text-emerald-700' : 'text-amber-700'}`}>{a.adjAmt > 0 ? '+' : ''}{formatMoney(a.adjAmt)}</td>
                      <td className="px-4 py-3.5 text-[11px] text-slate-500">{providerName(a.provNum)}</td>
                      <td className="px-4 py-3.5 text-[10px] text-slate-500">{a.procNum ? `#${a.procNum}${a.procDate ? ` · ${a.procDate}` : ''}` : 'Not attached'}</td>
                      <td className="max-w-[260px] truncate px-4 py-3.5 text-[11px] text-slate-500" title={a.note}>{a.note || '—'}</td>
                      <td className="px-5 py-3.5 text-right sm:px-6">
                        <button type="button" onClick={() => openEdit(a)} disabled={saving || a.adjNum < 0} aria-label={`Edit adjustment ${a.adjNum}`} data-testid={`button-edit-adjustment-${a.adjNum}`}
                          className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-blue-50 hover:text-blue-700 disabled:opacity-40"><Edit2 size={14} /></button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {formOpen && (
        <div className="fixed inset-0 z-[90] flex items-center justify-center overflow-y-auto bg-slate-950/35 p-4 backdrop-blur-[2px]">
          <section role="dialog" aria-modal="true" aria-labelledby="adjustment-form-title" className="my-auto w-full max-w-[560px] rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="mb-1 text-[10px] font-bold uppercase tracking-[1px] text-blue-600">Patient #{patNum}</p>
                <h2 id="adjustment-form-title" className="font-[Manrope] text-[18px] font-extrabold text-slate-900">{editing ? `Edit adjustment #${editing.adjNum}` : 'Add adjustment'}</h2>
                <p className="mt-1 text-[11px] text-slate-500">Negative types (discounts, write-offs) take a negative amount; positive types a positive one.</p>
              </div>
              <button type="button" onClick={() => setFormOpen(false)} aria-label="Close" className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100"><X size={17} /></button>
            </div>
            <form onSubmit={save} className="mt-5 space-y-4" noValidate>
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block text-[11px] font-bold text-slate-700">Adjustment type <span className="text-rose-500">*</span>
                  <select value={draft.adjType} onChange={(e) => set('adjType', e.target.value)} data-testid="select-adjustment-type" className={fieldClass}>
                    <option value="">{types.length ? 'Choose a type' : 'No types synced yet'}</option>
                    {types.map((t) => <option key={t.defNum} value={t.defNum}>{t.name} ({t.sign === '-' ? 'reduces' : 'adds'})</option>)}
                  </select>
                  {!types.length && <span className="mt-1 block text-[10px] font-normal text-slate-400">Types come from Open Dental’s Definitions. Run Force Sync to load them.</span>}
                </label>
                <label className="block text-[11px] font-bold text-slate-700">Amount <span className="text-rose-500">*</span>
                  <input inputMode="decimal" value={draft.adjAmt} onChange={(e) => set('adjAmt', e.target.value)} placeholder={selectedType?.sign === '-' ? '-25.00' : '25.00'} data-testid="input-adjustment-amount" className={fieldClass} />
                </label>
                <label className="block text-[11px] font-bold text-slate-700">Date <span className="text-rose-500">*</span>
                  <input type="date" max={localToday()} value={draft.adjDate} onChange={(e) => set('adjDate', e.target.value)} data-testid="input-adjustment-date" className={fieldClass} />
                </label>
                <label className="block text-[11px] font-bold text-slate-700">Provider
                  <select value={draft.provNum} onChange={(e) => set('provNum', e.target.value)} className={fieldClass}>
                    <option value="">{editing ? 'Keep current provider' : 'Patient’s primary provider'}</option>
                    {providers.map((p) => <option key={p.ProvNum} value={p.ProvNum}>{p.name}</option>)}
                  </select>
                </label>
                <label className="block text-[11px] font-bold text-slate-700">Attach to procedure <span className="font-normal text-slate-400">(ProcNum, optional)</span>
                  <input inputMode="numeric" value={draft.procNum} onChange={(e) => set('procNum', e.target.value.replace(/\D/g, ''))} placeholder="e.g. 18" className={fieldClass} />
                </label>
                {!editing && (
                  <label className="block text-[11px] font-bold text-slate-700">Procedure date <span className="font-normal text-slate-400">(optional)</span>
                    <input type="date" value={draft.procDate} onChange={(e) => set('procDate', e.target.value)} className={fieldClass} />
                  </label>
                )}
              </div>
              <label className="block text-[11px] font-bold text-slate-700">Note
                <textarea rows={3} maxLength={2000} value={draft.note} onChange={(e) => set('note', e.target.value)} placeholder="e.g. Cash discount"
                  className="mt-1.5 w-full resize-y rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-[12px] text-slate-700 outline-none focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70" />
              </label>
              {formError && <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-[10px] font-medium text-rose-700">{formError}</p>}
              <div className="flex flex-col-reverse gap-2 border-t border-slate-100 pt-4 sm:flex-row sm:justify-end">
                <button type="button" onClick={() => setFormOpen(false)} disabled={saving} className="h-10 rounded-xl border border-slate-200 px-4 text-[11px] font-semibold text-slate-600 hover:bg-slate-50">Cancel</button>
                <button type="submit" disabled={saving} data-testid="button-save-adjustment" className="flex h-10 items-center justify-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[11px] font-bold text-white hover:bg-[#244fcf] disabled:opacity-60">
                  {saving && <LoaderCircle size={14} className="animate-spin" />}{saving ? 'Saving…' : editing ? 'Save changes' : 'Add adjustment'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}

import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import {
  Check, ChevronLeft, ChevronRight, CircleAlert, HeartPulse, LoaderCircle,
  Plus, RefreshCw, Search, ShieldCheck, X,
} from 'lucide-react';
import {
  DISEASE_DEFINITION_PAGE_SIZE,
  createBackendDiseaseDefinition,
  getBackendDiseaseDefinition,
  listBackendDiseaseDefinitions,
  type BackendDiseaseDefinition,
} from './lib/backendDiseaseDefinitions';

const inputClass =
  'h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-[12px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70';

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'An unexpected upstream error occurred.';
}

function formatTimestamp(value: string) {
  if (!value) return 'Not recorded';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

const show = (value: string) => value || 'Not recorded';

export default function DiseaseDefinitionsScreen() {
  const [rows, setRows] = useState<BackendDiseaseDefinition[]>([]);
  const [offset, setOffset] = useState(0);
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState('');
  const [filter, setFilter] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<BackendDiseaseDefinition | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [detailReload, setDetailReload] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [name, setName] = useState('');
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [notice, setNotice] = useState('');
  const [refreshWarning, setRefreshWarning] = useState('');

  const mounted = useRef(true);
  const requestSeq = useRef(0);
  const offsetRef = useRef(0);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  const loadRows = useCallback(async (nextOffset: number, afterCreate = false) => {
    const seq = ++requestSeq.current;
    setLoading(true);
    if (!afterCreate) setListError('');
    try {
      const result = await listBackendDiseaseDefinitions(nextOffset);
      if (!mounted.current || seq !== requestSeq.current) return;
      setRows(result);
      setOffset(nextOffset);
      offsetRef.current = nextOffset;
      setListError('');
      setRefreshWarning('');
      setSelectedId((cur) => (cur !== null && result.some((r) => r.diseaseDefNum === cur) ? cur : null));
    } catch (error) {
      if (!mounted.current || seq !== requestSeq.current) return;
      setRows([]);
      setSelectedId(null);
      setDetail(null);
      setDetailError('');
      const msg = errorMessage(error);
      setListError(`Disease definitions are unavailable. ${msg}`);
      if (afterCreate) {
        setRefreshWarning(`The list could not be refreshed, so it may not show the new definition yet. ${msg}`);
      }
    } finally {
      if (mounted.current && seq === requestSeq.current) setLoading(false);
    }
  }, []);

  useEffect(() => { void loadRows(0); }, [loadRows]);

  useEffect(() => {
    if (selectedId === null) {
      setDetail(null);
      setDetailError('');
      setDetailLoading(false);
      return;
    }
    let active = true;
    setDetail(null);
    setDetailLoading(true);
    setDetailError('');
    getBackendDiseaseDefinition(selectedId)
      .then((record) => { if (active) setDetail(record); })
      .catch((error: unknown) => {
        if (active) setDetailError(`Unable to load definition #${selectedId}. ${errorMessage(error)}`);
      })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [selectedId, detailReload]);

  const visibleRows = useMemo(() => {
    const term = filter.trim().toLowerCase();
    const list = term
      ? rows.filter((r) =>
          `${r.diseaseDefNum} ${r.diseaseName} ${r.icd9Code} ${r.icd10Code} ${r.snomedCode}`.toLowerCase().includes(term))
      : rows;
    return [...list].sort((a, b) => a.diseaseName.localeCompare(b.diseaseName) || a.diseaseDefNum - b.diseaseDefNum);
  }, [filter, rows]);

  const openCreate = () => {
    setName('');
    setFormError('');
    setFormOpen(true);
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) { setFormError('Enter a disease name before saving.'); return; }
    if (rows.some((r) => r.diseaseName.trim().toLowerCase() === trimmed.toLowerCase())) {
      setFormError('A disease definition with this name is already listed on this page. Names must be unique.');
      return;
    }
    setSaving(true);
    setFormError('');
    try {
      await createBackendDiseaseDefinition(trimmed);
    } catch (error) {
      if (!mounted.current) return;
      setFormError(`Open Dental did not confirm the creation. ${errorMessage(error)}`);
      setSaving(false);
      return;
    }
    if (!mounted.current) return;
    setSaving(false);
    setFormOpen(false);
    setRefreshWarning('');
    setNotice(`Disease definition "${trimmed}" was created in Open Dental.`);
    void loadRows(offsetRef.current, true);
  };

  const hiddenCount = rows.filter((r) => r.isHidden).length;

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9" data-testid="screen-disease-definitions">
      <header className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[10px] font-bold uppercase tracking-[1.3px] text-amber-700">
            <HeartPulse size={13} /> Patient safety <span className="text-slate-300">/</span> API Catalog
          </p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]" data-testid="text-disease-definitions-title">
            Disease definitions<span className="text-blue-600">.</span>
          </h1>
          <p className="mt-1.5 max-w-2xl text-[12px] leading-5 text-slate-500">
            Reference conditions staff select when documenting a patient's medical history.
          </p>
        </div>
        <div className="flex items-center gap-2 self-start sm:self-auto">
          <button type="button" onClick={() => void loadRows(offset)} disabled={loading} data-testid="button-refresh-disease-definitions" className="flex h-10 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 text-[11px] font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-50">
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
          </button>
          <button type="button" onClick={openCreate} disabled={loading || Boolean(listError)} data-testid="button-create-disease-definition" className="flex h-10 items-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[11px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf] disabled:cursor-not-allowed disabled:opacity-50">
            <Plus size={15} /> New disease
          </button>
        </div>
      </header>

      <aside role="note" className="mb-5 flex items-start gap-3 rounded-2xl border border-amber-200/80 bg-[#fff8e9] px-4 py-3.5 text-amber-950 sm:items-center">
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-amber-100 text-amber-700"><ShieldCheck size={17} /></span>
        <div className="min-w-0">
          <p className="text-[12px] font-bold">Live Open Dental reference data</p>
          <p className="mt-0.5 text-[10px] leading-5 text-amber-900/75">New names are sent directly to Open Dental and must be unique. The official API does not offer editing or deletion of disease definitions.</p>
        </div>
      </aside>

      {listError && (
        <div role="alert" data-testid="disease-definitions-error" className="mb-4 flex flex-col gap-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-medium text-rose-800 sm:flex-row sm:items-center">
          <span className="flex flex-1 items-start gap-2"><CircleAlert size={15} className="mt-0.5 shrink-0" />{listError}</span>
          <button type="button" onClick={() => void loadRows(offset)} data-testid="button-retry-disease-definitions" className="ml-6 flex items-center gap-1.5 self-start font-bold hover:text-rose-950 sm:ml-0"><RefreshCw size={12} /> Retry</button>
        </div>
      )}
      {notice && (
        <div role="status" data-testid="disease-definitions-success" className="mb-4 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-[11px] font-semibold text-emerald-800">
          <Check size={15} className="mt-0.5 shrink-0" /><span className="flex-1">{notice}</span>
          <button type="button" onClick={() => setNotice('')} aria-label="Dismiss confirmation" className="text-emerald-700 hover:text-emerald-900"><X size={14} /></button>
        </div>
      )}
      {refreshWarning && (
        <div role="alert" data-testid="disease-definitions-refresh-warning" className="mb-4 flex flex-col gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-[11px] font-medium text-amber-900 sm:flex-row sm:items-center">
          <span className="flex flex-1 items-start gap-2"><CircleAlert size={15} className="mt-0.5 shrink-0" />{refreshWarning}</span>
          <button type="button" onClick={() => void loadRows(offsetRef.current, true)} className="ml-6 flex items-center gap-1.5 self-start font-bold sm:ml-0"><RefreshCw size={12} /> Refresh list</button>
        </div>
      )}

      <section className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-[1fr_auto_auto]">
        <label className="relative block">
          <span className="sr-only">Search disease definitions</span>
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Search name, ID, ICD-9, ICD-10, or SNOMED" data-testid="input-disease-definition-search" className={`${inputClass} pl-9`} />
        </label>
        <div className="flex items-center gap-2 rounded-xl border border-slate-200/80 bg-white px-3.5 py-2.5 text-[10px] font-semibold text-slate-600">
          <span className="h-2 w-2 rounded-full bg-emerald-500" />{loading || listError ? '—' : `${rows.length - hiddenCount} visible`}
        </div>
        <div className="flex items-center gap-2 rounded-xl border border-slate-200/80 bg-white px-3.5 py-2.5 text-[10px] font-semibold text-slate-600">
          <span className="h-2 w-2 rounded-full bg-slate-400" />{loading || listError ? '—' : `${hiddenCount} hidden`}
        </div>
      </section>

      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
        <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]" data-testid="section-disease-definitions">
          <div className="border-b border-slate-100 px-5 py-4 sm:px-6">
            <div className="flex items-center gap-2">
              <h2 className="font-[Manrope] text-[14px] font-extrabold text-slate-800">Disease catalog</h2>
              {!listError && !loading && <span className="rounded-md bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700" data-testid="text-disease-definition-count">{rows.length}</span>}
            </div>
            <p className="mt-1 text-[10px] text-slate-400">Page starting at record {offset + 1}. Search filters this page only.</p>
          </div>

          {loading ? (
            <div aria-label="Loading disease definitions" data-testid="disease-definitions-loading">
              {[0, 1, 2, 3, 4].map((i) => <div key={i} className="flex animate-pulse items-center gap-4 border-b border-slate-100 px-5 py-4 sm:px-6"><span className="h-8 w-8 rounded-lg bg-slate-100" /><span className="h-3 w-2/5 rounded bg-slate-100" /><span className="ml-auto h-3 w-1/6 rounded bg-slate-100" /></div>)}
            </div>
          ) : listError ? (
            <div className="px-6 py-14 text-center" data-testid="disease-definitions-unavailable">
              <CircleAlert size={23} className="mx-auto text-rose-500" />
              <p className="mt-3 text-[12px] font-bold text-slate-700">Catalog unavailable</p>
              <p className="mt-1 text-[10px] text-slate-500">This is not an empty result. No cached or sample records are shown.</p>
              <button type="button" onClick={() => void loadRows(offset)} className="mt-4 inline-flex h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-[10px] font-bold text-slate-700 hover:bg-slate-50"><RefreshCw size={13} /> Try again</button>
            </div>
          ) : rows.length === 0 ? (
            <div className="px-6 py-14 text-center" data-testid="empty-disease-definitions">
              <span className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-blue-50 text-blue-600"><HeartPulse size={19} /></span>
              <p className="mt-3 text-[12px] font-semibold text-slate-700">{offset > 0 ? 'No definitions on this page' : 'No definitions returned'}</p>
              <p className="mt-1 text-[10px] text-slate-400">Open Dental responded successfully with an empty list.</p>
              {offset > 0 && <button type="button" onClick={() => void loadRows(Math.max(0, offset - DISEASE_DEFINITION_PAGE_SIZE))} className="mt-4 inline-flex h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-[10px] font-bold text-slate-700 hover:bg-slate-50"><ChevronLeft size={13} /> Previous page</button>}
            </div>
          ) : visibleRows.length === 0 ? (
            <div className="px-5 py-14 text-center text-[11px] text-slate-500" data-testid="empty-disease-definition-filter">No definitions on this page match "{filter}".</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[680px] border-collapse text-left">
                <thead className="bg-[#fbfcfe] text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">
                  <tr>
                    <th className="px-5 py-3 sm:px-6">Disease</th>
                    <th className="px-4 py-3">ICD-9</th>
                    <th className="px-4 py-3">ICD-10</th>
                    <th className="px-4 py-3">SNOMED</th>
                    <th className="px-4 py-3">Visibility</th>
                    <th className="px-5 py-3 sm:px-6"><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  {visibleRows.map((row) => (
                    <tr key={row.diseaseDefNum} data-testid={`row-disease-definition-${row.diseaseDefNum}`} onClick={() => setSelectedId(row.diseaseDefNum)} className={`cursor-pointer border-t border-slate-100 transition hover:bg-slate-50/70 ${selectedId === row.diseaseDefNum ? 'bg-blue-50/55' : ''}`}>
                      <td className="px-5 py-3.5 sm:px-6">
                        <p className="text-[11px] font-bold text-slate-800">{row.diseaseName || 'Unnamed disease'}</p>
                        <p className="mt-1 font-mono text-[9px] text-slate-400">#{row.diseaseDefNum}</p>
                      </td>
                      <td className="px-4 py-3.5 font-mono text-[10px] text-slate-600">{row.icd9Code || '-'}</td>
                      <td className="px-4 py-3.5 font-mono text-[10px] text-slate-600">{row.icd10Code || '-'}</td>
                      <td className="px-4 py-3.5 font-mono text-[10px] text-slate-600">{row.snomedCode || '-'}</td>
                      <td className="px-4 py-3.5"><span className={`rounded-md px-2 py-1 text-[9px] font-bold ${row.isHidden ? 'bg-slate-100 text-slate-500' : 'bg-emerald-50 text-emerald-700'}`}>{row.isHidden ? 'Hidden' : 'Visible'}</span></td>
                      <td className="px-5 py-3.5 text-right sm:px-6">
                        <button type="button" onClick={(e) => { e.stopPropagation(); setSelectedId(row.diseaseDefNum); }} aria-label={`View ${row.diseaseName || 'disease'} details`} data-testid={`button-view-disease-definition-${row.diseaseDefNum}`} className="rounded-lg px-2.5 py-1.5 text-[9px] font-bold text-blue-700 hover:bg-blue-50">Details</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {!listError && !loading && (
            <footer className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 px-5 py-3 sm:px-6">
              <span className="text-[10px] text-slate-400">Showing {visibleRows.length} of {rows.length} on this page</span>
              <div className="flex items-center gap-2">
                <button type="button" onClick={() => void loadRows(Math.max(0, offset - DISEASE_DEFINITION_PAGE_SIZE))} disabled={offset === 0} data-testid="button-previous-disease-definitions" className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2.5 text-[10px] font-semibold text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"><ChevronLeft size={13} /> Previous</button>
                <span className="text-[9px] font-semibold text-slate-400">Offset {offset}</span>
                <button type="button" onClick={() => void loadRows(offset + DISEASE_DEFINITION_PAGE_SIZE)} disabled={rows.length < DISEASE_DEFINITION_PAGE_SIZE} data-testid="button-next-disease-definitions" className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2.5 text-[10px] font-semibold text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40">Next <ChevronRight size={13} /></button>
              </div>
            </footer>
          )}
        </section>

        <aside className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]" aria-label="Disease details" data-testid="panel-disease-definition-detail">
          <div className="border-b border-slate-100 px-5 py-4">
            <p className="text-[9px] font-bold uppercase tracking-[1px] text-blue-600">Selected reference</p>
            <h2 className="mt-1 font-[Manrope] text-[14px] font-extrabold text-slate-800">Disease details</h2>
          </div>
          {detailLoading ? (
            <div className="space-y-4 p-5" data-testid="disease-definition-detail-loading">
              <div className="h-5 w-3/4 animate-pulse rounded bg-slate-100" />
              {[0, 1, 2, 3].map((i) => <div key={i} className="h-10 animate-pulse rounded-lg bg-slate-50" />)}
            </div>
          ) : detailError ? (
            <div role="alert" className="p-5 text-[10px] leading-5 text-rose-700">
              <span className="mb-2 flex items-center gap-1.5 font-bold"><CircleAlert size={14} /> Detail could not be loaded</span>{detailError}
              <div className="mt-3 flex gap-4">
                <button type="button" onClick={() => setDetailReload((current) => current + 1)} className="font-bold text-blue-700 hover:text-blue-900">Retry</button>
                <button type="button" onClick={() => setSelectedId(null)} className="font-bold text-slate-600 hover:text-slate-900">Clear selection</button>
              </div>
            </div>
          ) : detail ? (
            <div className="p-5">
              <p className="break-words text-[14px] font-bold leading-5 text-slate-900">{detail.diseaseName || 'Unnamed disease'}</p>
              <p className="mt-1 font-mono text-[9px] text-slate-400">DiseaseDefNum {detail.diseaseDefNum}</p>
              <span className={`mt-4 inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-[9px] font-bold ${detail.isHidden ? 'bg-slate-100 text-slate-600' : 'bg-emerald-50 text-emerald-700'}`}>
                <span className={`h-1.5 w-1.5 rounded-full ${detail.isHidden ? 'bg-slate-400' : 'bg-emerald-500'}`} />{detail.isHidden ? 'Hidden from selection' : 'Available for selection'}
              </span>
              <dl className="mt-5 divide-y divide-slate-100 border-y border-slate-100">
                <Field label="ICD-9 code" value={show(detail.icd9Code)} mono />
                <Field label="ICD-10 code" value={show(detail.icd10Code)} mono />
                <Field label="SNOMED code" value={show(detail.snomedCode)} mono />
                <Field label="Last updated" value={formatTimestamp(detail.dateTStamp)} />
              </dl>
              <p className="mt-4 text-[9px] leading-4 text-slate-400">Read-only. The Open Dental API documents no update or delete for disease definitions.</p>
            </div>
          ) : (
            <div className="px-5 py-10 text-center" data-testid="empty-disease-definition-detail">
              <span className="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-slate-50 text-slate-400"><Search size={17} /></span>
              <p className="mt-3 text-[11px] font-semibold text-slate-700">Choose a disease</p>
              <p className="mt-1 text-[10px] leading-4 text-slate-400">Select a row to read its record from Open Dental.</p>
            </div>
          )}
        </aside>
      </div>

      {formOpen && (
        <div className="fixed inset-0 z-[90] flex items-center justify-center overflow-y-auto bg-slate-950/35 p-4 backdrop-blur-[2px]">
          <section role="dialog" aria-modal="true" aria-labelledby="disease-form-title" data-testid="dialog-disease-definition-form" className="my-auto w-full max-w-[480px] rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="mb-1 text-[9px] font-bold uppercase tracking-[1px] text-amber-700">Open Dental reference data</p>
                <h2 id="disease-form-title" className="font-[Manrope] text-[18px] font-extrabold text-slate-900">New disease definition</h2>
                <p className="mt-1 text-[10px] leading-5 text-slate-500">Disease names must be unique. This cannot be edited or deleted afterward through the API.</p>
              </div>
              <button type="button" onClick={() => setFormOpen(false)} disabled={saving} aria-label="Close form" className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 disabled:opacity-50"><X size={17} /></button>
            </div>
            <form onSubmit={submit} className="mt-5 space-y-4">
              <label className="block text-[11px] font-bold text-slate-700" htmlFor="disease-definition-name">
                Disease name <span className="text-rose-500">*</span>
                <input id="disease-definition-name" autoFocus required maxLength={255} value={name} onChange={(e) => setName(e.target.value)} placeholder="Enter disease name" data-testid="input-disease-definition-name" className={`${inputClass} mt-1.5`} />
              </label>
              {formError && <p role="alert" data-testid="disease-definition-form-error" className="rounded-lg bg-rose-50 px-3 py-2 text-[10px] font-medium text-rose-700">{formError}</p>}
              <div className="flex flex-col-reverse gap-2 border-t border-slate-100 pt-4 sm:flex-row sm:justify-end">
                <button type="button" onClick={() => setFormOpen(false)} disabled={saving} className="h-10 rounded-xl border border-slate-200 px-4 text-[11px] font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50">Cancel</button>
                <button type="submit" disabled={saving} data-testid="button-save-disease-definition" className="flex h-10 items-center justify-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[11px] font-bold text-white hover:bg-[#244fcf] disabled:cursor-wait disabled:opacity-60">
                  {saving && <LoaderCircle size={14} className="animate-spin" />}{saving ? 'Creating...' : 'Create disease'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}

function Field({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-start justify-between gap-4 py-3">
      <dt className="text-[9px] font-semibold uppercase tracking-[.5px] text-slate-400">{label}</dt>
      <dd className={`max-w-[58%] break-words text-right text-[10px] font-semibold text-slate-700 ${mono ? 'font-mono' : ''}`}>{value}</dd>
    </div>
  );
}

import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  AlertTriangle, Check, ChevronLeft, ChevronRight,
  CircleAlert, Clock3, Edit2, FilePlus2, LoaderCircle, Plus, RefreshCw,
  Search, ShieldCheck, X,
} from 'lucide-react';
import {
  createBackendAllergyDefinition,
  getBackendAllergyDefinition,
  listBackendAllergyDefinitions,
  updateBackendAllergyDefinition,
  type AllergyDefinitionDraft,
  type BackendAllergyDefinition,
} from './lib/backendAllergyDefinitions';

const PAGE_SIZE = 100;
const inputClass =
  'mt-1.5 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-[12px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70';
const emptyDraft = (): AllergyDefinitionDraft => ({ description: '', isHidden: false });

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'An unexpected upstream error occurred.';
}

function formatTimestamp(value: string) {
  if (!value) return 'Not recorded';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function metadataValue(value: string | number) {
  return value === '' || value === 0 ? 'Not recorded' : String(value);
}

export default function AllergyDefinitionsScreen() {
  const [rows, setRows] = useState<BackendAllergyDefinition[]>([]);
  const [offset, setOffset] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [listError, setListError] = useState('');
  const [filter, setFilter] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<BackendAllergyDefinition | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<BackendAllergyDefinition | null>(null);
  const [draft, setDraft] = useState<AllergyDefinitionDraft>(emptyDraft());
  const [saving, setSaving] = useState(false);
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');
  const [sortAscending, setSortAscending] = useState(true);

  const loadRows = useCallback(async (nextOffset: number, quiet = false) => {
    if (quiet) setRefreshing(true);
    else setLoading(true);
    setListError('');
    try {
      const result = await listBackendAllergyDefinitions(nextOffset);
      setRows(result);
      setOffset(nextOffset);
      setSelectedId((current) => current !== null && result.some((row) => row.allergyDefNum === current) ? current : null);
      setDetail((current) => current && result.some((row) => row.allergyDefNum === current.allergyDefNum) ? current : null);
    } catch (error) {
      setRows([]);
      setListError(`Unable to load allergy definitions from Open Dental. ${errorMessage(error)}`);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadRows(0);
  }, [loadRows]);

  useEffect(() => {
    if (selectedId === null) {
      setDetail(null);
      setDetailError('');
      setDetailLoading(false);
      return;
    }
    let active = true;
    setDetailLoading(true);
    setDetailError('');
    getBackendAllergyDefinition(selectedId)
      .then((record) => {
        if (active) setDetail(record);
      })
      .catch((error: unknown) => {
        if (active) {
          setDetail(null);
          setDetailError(`Unable to load definition #${selectedId}. ${errorMessage(error)}`);
        }
      })
      .finally(() => {
        if (active) setDetailLoading(false);
      });
    return () => { active = false; };
  }, [selectedId]);

  const visibleRows = useMemo(() => {
    const term = filter.trim().toLowerCase();
    const matching = term
      ? rows.filter((row) => `${row.allergyDefNum} ${row.description} ${row.snomedType} ${row.uniiCode}`.toLowerCase().includes(term))
      : rows;
    return [...matching].sort((a, b) => {
      const compare = a.description.localeCompare(b.description) || a.allergyDefNum - b.allergyDefNum;
      return sortAscending ? compare : -compare;
    });
  }, [filter, rows, sortAscending]);

  const openCreate = () => {
    setEditing(null);
    setDraft(emptyDraft());
    setActionError('');
    setNotice('');
    setFormOpen(true);
  };

  const openEdit = (record: BackendAllergyDefinition) => {
    setEditing(record);
    setDraft({ description: record.description, isHidden: record.isHidden });
    setActionError('');
    setNotice('');
    setFormOpen(true);
  };

  const saveDefinition = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const description = draft.description.trim();
    if (!description) {
      setActionError('Enter a definition name before saving.');
      return;
    }
    setSaving(true);
    setActionError('');
    setNotice('');
    try {
      const saved = editing
        ? await updateBackendAllergyDefinition(editing.allergyDefNum, { ...draft, description })
        : await createBackendAllergyDefinition({ ...draft, description });
      setFormOpen(false);
      setNotice(editing
        ? `Definition #${saved.allergyDefNum} updated in Open Dental.`
        : `Definition “${saved.description || description}” created in Open Dental.`);
      if (editing) setDetail(saved);
      setSelectedId(saved.allergyDefNum);
      await loadRows(offset, true);
      setSelectedId(saved.allergyDefNum);
      setDetail(saved);
    } catch (error) {
      setActionError(`Open Dental did not confirm the change. ${errorMessage(error)}`);
    } finally {
      setSaving(false);
    }
  };

  const hiddenCount = rows.filter((row) => row.isHidden).length;
  const shownCount = rows.length - hiddenCount;

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9" data-testid="screen-allergy-definitions">
      <header className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[10px] font-bold uppercase tracking-[1.3px] text-amber-700">
            <AlertTriangle size={13} /> Patient safety <span className="text-slate-300">/</span> API Catalog
          </p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]" data-testid="text-allergy-definitions-title">
            Allergy definitions<span className="text-blue-600">.</span>
          </h1>
          <p className="mt-1.5 max-w-2xl text-[12px] leading-5 text-slate-500">
            Maintain the reference terms staff use when documenting patient allergies.
          </p>
        </div>
        <div className="flex items-center gap-2 self-start sm:self-auto">
          <button type="button" onClick={() => void loadRows(offset, true)} disabled={loading || refreshing} data-testid="button-refresh-allergy-definitions" className="flex h-10 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 text-[11px] font-semibold text-slate-600 transition hover:border-slate-300 hover:bg-slate-50 disabled:opacity-50">
            <RefreshCw size={14} className={refreshing ? 'animate-spin' : ''} /> Refresh
          </button>
          <button type="button" onClick={openCreate} disabled={loading || Boolean(listError)} data-testid="button-create-allergy-definition" className="flex h-10 items-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[11px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf] disabled:cursor-not-allowed disabled:opacity-50">
            <Plus size={15} /> New definition
          </button>
        </div>
      </header>

      <aside role="note" data-testid="notice-allergy-definitions-live-data" className="mb-5 flex items-start gap-3 rounded-2xl border border-amber-200/80 bg-[#fff8e9] px-4 py-3.5 text-amber-950 sm:items-center">
        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-amber-100 text-amber-700 sm:mt-0"><ShieldCheck size={17} /></span>
        <div className="min-w-0 flex-1">
          <p className="text-[12px] font-bold">Live Open Dental reference data</p>
          <p className="mt-0.5 text-[10px] leading-5 text-amber-900/75">Changes are sent directly to Open Dental. Definitions cannot be deleted through the official API; use hidden status to retire a term.</p>
        </div>
        <span className="hidden rounded-full border border-amber-300/80 px-2.5 py-1 text-[9px] font-bold uppercase tracking-[.8px] text-amber-800 sm:inline-flex">Source of truth</span>
      </aside>

      {listError && (
        <div role="alert" data-testid="allergy-definitions-error" className="mb-4 flex flex-col gap-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-medium text-rose-800 sm:flex-row sm:items-center">
          <span className="flex flex-1 items-start gap-2"><CircleAlert size={15} className="mt-0.5 shrink-0" />{listError}</span>
          <button type="button" onClick={() => void loadRows(offset)} className="ml-6 flex items-center gap-1.5 self-start font-bold text-rose-800 hover:text-rose-950 sm:ml-0"><RefreshCw size={12} /> Retry</button>
        </div>
      )}
      {notice && (
        <div role="status" data-testid="allergy-definitions-success" className="mb-4 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-[11px] font-semibold text-emerald-800">
          <Check size={15} />{notice}
        </div>
      )}
      {actionError && !formOpen && <div role="alert" className="mb-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-medium text-rose-700">{actionError}</div>}

      <section className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-[1fr_auto_auto]">
        <label className="relative block">
          <span className="sr-only">Search definitions</span>
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="Search name, ID, SNOMED, or UNII" aria-label="Search allergy definitions" data-testid="input-allergy-definition-search" className={`${inputClass} mt-0 pl-9`} />
        </label>
        <div className="flex items-center gap-2 rounded-xl border border-slate-200/80 bg-white px-3.5 py-2.5">
          <span className="h-2 w-2 rounded-full bg-emerald-500" /><span className="text-[10px] font-semibold text-slate-600">{loading ? 'Syncing' : `${shownCount} visible`}</span>
        </div>
        <div className="flex items-center gap-2 rounded-xl border border-slate-200/80 bg-white px-3.5 py-2.5">
          <span className="h-2 w-2 rounded-full bg-slate-400" /><span className="text-[10px] font-semibold text-slate-600">{loading ? '—' : `${hiddenCount} hidden`}</span>
        </div>
      </section>

      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
        <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]" data-testid="section-allergy-definitions">
          <div className="flex flex-col gap-2 border-b border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
            <div>
              <div className="flex items-center gap-2">
                <h2 className="font-[Manrope] text-[14px] font-extrabold tracking-[-.3px] text-slate-800">Definition catalog</h2>
                <span className="rounded-md bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700" data-testid="text-allergy-definition-count">{rows.length}</span>
              </div>
              <p className="mt-1 text-[10px] text-slate-400">Open Dental response · page starting at record {offset + 1}</p>
            </div>
            <span className="inline-flex items-center gap-1.5 text-[9px] font-semibold text-slate-400"><Clock3 size={12} /> Metadata supplied by source</span>
          </div>

          {loading ? (
            <div className="space-y-0" aria-label="Loading allergy definitions" data-testid="allergy-definitions-loading">
              {[0, 1, 2, 3, 4].map((item) => <div key={item} className="flex animate-pulse items-center gap-4 border-b border-slate-100 px-5 py-4 sm:px-6"><span className="h-8 w-8 rounded-lg bg-slate-100" /><span className="h-3 w-2/5 rounded bg-slate-100" /><span className="ml-auto h-3 w-1/6 rounded bg-slate-100" /></div>)}
            </div>
          ) : listError ? (
            <div className="px-6 py-14 text-center">
              <CircleAlert size={23} className="mx-auto text-rose-500" />
              <p className="mt-3 text-[12px] font-bold text-slate-700">Catalog unavailable</p>
              <p className="mt-1 text-[10px] text-slate-500">No cached or sample records are shown.</p>
            </div>
          ) : rows.length === 0 ? (
            <div className="px-6 py-14 text-center" data-testid="empty-allergy-definitions">
              <span className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-blue-50 text-blue-600"><FilePlus2 size={19} /></span>
              <p className="mt-3 text-[12px] font-semibold text-slate-700">No definitions returned</p>
              <p className="mt-1 text-[10px] text-slate-400">Open Dental returned an empty list. Add a definition to begin the catalog.</p>
              <button type="button" onClick={openCreate} className="mt-4 inline-flex h-9 items-center gap-2 rounded-lg bg-[#315fe7] px-3 text-[10px] font-bold text-white hover:bg-[#244fcf]"><Plus size={13} /> Create first definition</button>
            </div>
          ) : visibleRows.length === 0 ? (
            <div className="px-5 py-14 text-center text-[11px] text-slate-500" data-testid="empty-allergy-definition-filter">No definitions match “{filter}”.</div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[680px] border-collapse text-left">
                  <thead className="bg-[#fbfcfe] text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">
                    <tr>
                      <th className="px-5 py-3 sm:px-6">Definition</th>
                      <th className="px-4 py-3">SNOMED type</th>
                      <th className="px-4 py-3">Medication</th>
                      <th className="px-4 py-3">Visibility</th>
                      <th className="px-5 py-3 text-right sm:px-6"> </th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibleRows.map((row) => (
                      <tr key={row.allergyDefNum} data-testid={`row-allergy-definition-${row.allergyDefNum}`} onClick={() => { setSelectedId(row.allergyDefNum); setNotice(''); }} className={`cursor-pointer border-t border-slate-100 transition hover:bg-slate-50/70 ${selectedId === row.allergyDefNum ? 'bg-blue-50/55' : ''}`}>
                        <td className="px-5 py-3.5 sm:px-6">
                          <p className="text-[11px] font-bold text-slate-800">{row.description || 'Unnamed definition'}</p>
                          <p className="mt-1 font-mono text-[9px] text-slate-400">#{row.allergyDefNum}{row.uniiCode ? ` · ${row.uniiCode}` : ''}</p>
                        </td>
                        <td className="px-4 py-3.5 text-[10px] text-slate-600">{row.snomedType || '—'}</td>
                        <td className="px-4 py-3.5 font-mono text-[10px] text-slate-600">{row.medicationNum || '—'}</td>
                        <td className="px-4 py-3.5"><span className={`rounded-md px-2 py-1 text-[9px] font-bold ${row.isHidden ? 'bg-slate-100 text-slate-500' : 'bg-emerald-50 text-emerald-700'}`}>{row.isHidden ? 'Hidden' : 'Visible'}</span></td>
                        <td className="px-5 py-3.5 text-right sm:px-6">
                          <button type="button" onClick={(event) => { event.stopPropagation(); setSelectedId(row.allergyDefNum); setNotice(''); }} aria-label={`View ${row.description || 'allergy definition'} details`} data-testid={`button-view-allergy-definition-${row.allergyDefNum}`} className="rounded-lg px-2.5 py-1.5 text-[9px] font-bold text-blue-700 transition hover:bg-blue-50">Details</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <footer className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 px-5 py-3 sm:px-6">
                <span className="text-[10px] text-slate-400">Showing {visibleRows.length} of {rows.length} definitions</span>
                <div className="flex items-center gap-2">
                  <button type="button" onClick={() => void loadRows(Math.max(0, offset - PAGE_SIZE))} disabled={offset === 0 || loading || refreshing} aria-label="Previous definitions" data-testid="button-previous-allergy-definitions" className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2.5 text-[10px] font-semibold text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"><ChevronLeft size={13} /> Previous</button>
                  <span className="text-[9px] font-semibold text-slate-400">Offset {offset}</span>
                  <button type="button" onClick={() => void loadRows(offset + PAGE_SIZE)} disabled={rows.length < PAGE_SIZE || loading || refreshing} aria-label="Next definitions" data-testid="button-next-allergy-definitions" className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2.5 text-[10px] font-semibold text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40">Next <ChevronRight size={13} /></button>
                </div>
              </footer>
            </>
          )}
        </section>

        <aside className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]" aria-label="Definition details" data-testid="panel-allergy-definition-detail">
          <div className="border-b border-slate-100 px-5 py-4">
            <p className="text-[9px] font-bold uppercase tracking-[1px] text-blue-600">Selected reference</p>
            <h2 className="mt-1 font-[Manrope] text-[14px] font-extrabold text-slate-800">Definition details</h2>
          </div>
          {detailLoading ? (
            <div className="space-y-4 p-5" data-testid="allergy-definition-detail-loading">
              <div className="h-5 w-3/4 animate-pulse rounded bg-slate-100" />
              {[0, 1, 2, 3].map((item) => <div key={item} className="h-10 animate-pulse rounded-lg bg-slate-50" />)}
            </div>
          ) : detailError ? (
            <div role="alert" className="p-5 text-[10px] leading-5 text-rose-700"><span className="mb-2 flex items-center gap-1.5 font-bold"><CircleAlert size={14} /> Detail could not be loaded</span>{detailError}<button type="button" onClick={() => selectedId !== null && setSelectedId(null)} className="mt-3 block font-bold text-blue-700 hover:text-blue-900">Clear selection</button></div>
          ) : detail ? (
            <div className="p-5">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="break-words text-[14px] font-bold leading-5 text-slate-900">{detail.description || 'Unnamed definition'}</p>
                  <p className="mt-1 font-mono text-[9px] text-slate-400">AllergyDefNum {detail.allergyDefNum}</p>
                </div>
                <button type="button" onClick={() => openEdit(detail)} aria-label="Edit allergy definition" data-testid="button-edit-allergy-definition" className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-blue-50 hover:text-blue-700"><Edit2 size={14} /></button>
              </div>
              <div className="mt-4">
                <span className={`inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-[9px] font-bold ${detail.isHidden ? 'bg-slate-100 text-slate-600' : 'bg-emerald-50 text-emerald-700'}`}>
                  <span className={`h-1.5 w-1.5 rounded-full ${detail.isHidden ? 'bg-slate-400' : 'bg-emerald-500'}`} />{detail.isHidden ? 'Hidden from selection' : 'Available for selection'}
                </span>
              </div>
              <dl className="mt-5 divide-y divide-slate-100 border-y border-slate-100">
                <DetailField label="SNOMED type" value={metadataValue(detail.snomedType)} />
                <DetailField label="Medication number" value={metadataValue(detail.medicationNum)} />
                <DetailField label="UNII code" value={metadataValue(detail.uniiCode)} mono />
                <DetailField label="Last updated" value={formatTimestamp(detail.dateTStamp)} />
              </dl>
              <p className="mt-4 text-[9px] leading-4 text-slate-400">Metadata is read-only in the available Open Dental definition endpoint.</p>
            </div>
          ) : (
            <div className="px-5 py-10 text-center" data-testid="empty-allergy-definition-detail">
              <span className="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-slate-50 text-slate-400"><Search size={17} /></span>
              <p className="mt-3 text-[11px] font-semibold text-slate-700">Choose a definition</p>
              <p className="mt-1 text-[10px] leading-4 text-slate-400">Select a row to inspect its full reference metadata.</p>
            </div>
          )}
        </aside>
      </div>

      {formOpen && (
        <div className="fixed inset-0 z-[90] flex items-center justify-center overflow-y-auto bg-slate-950/35 p-4 backdrop-blur-[2px]" data-testid="dialog-allergy-definition-backdrop">
          <section role="dialog" aria-modal="true" aria-labelledby="allergy-definition-form-title" data-testid="dialog-allergy-definition-form" className="my-auto w-full max-w-[480px] rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="mb-1 text-[9px] font-bold uppercase tracking-[1px] text-amber-700">Open Dental reference data</p>
                <h2 id="allergy-definition-form-title" className="font-[Manrope] text-[18px] font-extrabold text-slate-900">{editing ? 'Edit definition' : 'New definition'}</h2>
                <p className="mt-1 text-[10px] leading-5 text-slate-500">{editing ? `Update definition #${editing.allergyDefNum}.` : 'Add a new allergy term to the practice reference catalog.'}</p>
              </div>
              <button type="button" onClick={() => setFormOpen(false)} disabled={saving} aria-label="Close definition form" data-testid="button-close-allergy-definition-form" className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-50"><X size={17} /></button>
            </div>
            <form onSubmit={saveDefinition} className="mt-5 space-y-4">
              <label className="block text-[11px] font-bold text-slate-700" htmlFor="allergy-definition-description">
                Description <span className="text-rose-500">*</span>
                <input id="allergy-definition-description" autoFocus required maxLength={255} value={draft.description} onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))} placeholder="Enter allergy definition" data-testid="input-allergy-definition-description" className={inputClass} />
              </label>
              {editing ? (
                <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 px-3 py-3">
                  <input type="checkbox" checked={draft.isHidden} onChange={(event) => setDraft((current) => ({ ...current, isHidden: event.target.checked }))} data-testid="checkbox-allergy-definition-hidden" className="mt-0.5 h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />
                  <span><span className="block text-[11px] font-bold text-slate-700">Hide definition from selection</span><span className="mt-0.5 block text-[9px] leading-4 text-slate-400">Retire this term without deleting historical records.</span></span>
                </label>
              ) : (
                <div className="rounded-xl border border-blue-100 bg-blue-50/60 px-3 py-3 text-[10px] leading-5 text-blue-900">
                  New definitions are created visible in Open Dental. Visibility can be changed after creation.
                </div>
              )}
              {editing && <div className="rounded-xl bg-slate-50 px-3 py-3 text-[9px] leading-4 text-slate-500">SNOMED type, medication number, UNII code, and timestamp are maintained by Open Dental and are read-only here.</div>}
              {actionError && <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-[10px] font-medium text-rose-700">{actionError}</p>}
              <div className="flex flex-col-reverse gap-2 border-t border-slate-100 pt-4 sm:flex-row sm:justify-end">
                <button type="button" onClick={() => setFormOpen(false)} disabled={saving} className="h-10 rounded-xl border border-slate-200 px-4 text-[11px] font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-50">Cancel</button>
                <button type="submit" disabled={saving} data-testid="button-save-allergy-definition" className="flex h-10 items-center justify-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[11px] font-bold text-white transition hover:bg-[#244fcf] disabled:cursor-wait disabled:opacity-60">
                  {saving && <LoaderCircle size={14} className="animate-spin" />}{saving ? 'Saving…' : editing ? 'Save changes' : 'Create definition'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}

function DetailField({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-start justify-between gap-4 py-3">
      <dt className="text-[9px] font-semibold uppercase tracking-[.5px] text-slate-400">{label}</dt>
      <dd className={`max-w-[58%] break-words text-right text-[10px] font-semibold text-slate-700 ${mono ? 'font-mono' : ''}`}>{value}</dd>
    </div>
  );
}
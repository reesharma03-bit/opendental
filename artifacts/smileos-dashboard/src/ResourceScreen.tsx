import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import {
  CircleAlert, Check, ChevronLeft, ChevronRight, Edit2, ExternalLink, Eye, Inbox, LoaderCircle,
  Lock, Plus, RefreshCw, Search, ShieldAlert, Trash2, X, Zap,
} from 'lucide-react';
import { request } from './lib/backend';
import { listBackendPatients, type BackendPatient } from './lib/backendPatients';
import {
  buildBody, buildUpdateBody, displayValue, formValue, getField, missingRequired, qs, toRows, updatePath,
  type ActionMeta, type ColumnMeta, type FieldMeta, type ParamMeta, type ResourceMeta,
} from './lib/resourceMeta';

type Row = Record<string, unknown>;
const inputCls = 'mt-1.5 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-[12px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70 disabled:bg-slate-50 disabled:text-slate-500';
const btnPrimary = 'flex h-10 items-center justify-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[12px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf] disabled:cursor-not-allowed disabled:opacity-50';
const btnGhost = 'flex h-10 items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-4 text-[11px] font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-50';

function patientLabel(p: BackendPatient) {
  return `${p.lastName}, ${p.firstName}`.replace(/^, |, $/g, '').trim() || `Patient ${p.patNum}`;
}

function humanize(key: string) {
  return key.trim().replace(/([a-z])([A-Z])/g, '$1 $2').replace(/^./, (c) => c.toUpperCase());
}

/* ---------- patient list shared by pickers ---------- */
function usePatients() {
  const [patients, setPatients] = useState<BackendPatient[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const load = useCallback(() => {
    setLoading(true);
    listBackendPatients('')
      .then((rows) => { setPatients(rows.filter((p) => p.patNum > 0)); setError(''); })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);
  useEffect(() => { load(); }, [load]);
  return { patients, loading, error, reload: load };
}
type PatientsState = ReturnType<typeof usePatients>;

function PatientPicker({ id, value, onChange, state, disabled, optional }: {
  id: string; value: string; onChange: (v: string) => void; state: PatientsState; disabled?: boolean; optional?: boolean;
}) {
  const [term, setTerm] = useState('');
  const shown = useMemo(() => {
    const q = term.trim().toLowerCase();
    const base = q ? state.patients.filter((p) => `${patientLabel(p)} ${p.patNum} ${p.chartNumber}`.toLowerCase().includes(q)) : state.patients;
    const sel = state.patients.find((p) => String(p.patNum) === value);
    return sel && !base.includes(sel) ? [sel, ...base] : base;
  }, [state.patients, term, value]);
  if (state.error) {
    return (
      <div>
        <input id={id} inputMode="numeric" value={value} onChange={(e) => onChange(e.target.value.replace(/\D/g, ''))} disabled={disabled} placeholder="Enter PatNum" className={inputCls} />
        <p className="mt-1 text-[10px] text-rose-600">Patient list unavailable ({state.error}). Enter the patient number manually or <button type="button" onClick={state.reload} className="font-bold underline">retry</button>.</p>
      </div>
    );
  }
  return (
    <div>
      <input aria-label="Search patients" value={term} onChange={(e) => setTerm(e.target.value)} disabled={disabled} placeholder="Type to narrow patients by name or number" className={`${inputCls} h-9`} />
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)} disabled={disabled || state.loading} className={inputCls}>
        <option value="">{state.loading ? 'Loading patients...' : optional ? 'Any patient' : 'Choose a patient'}</option>
         {value && !state.patients.some((p) => String(p.patNum) === value) && <option value={value}>Patient #{value} (entered manually)</option>}
        {shown.map((p) => <option key={p.patNum} value={p.patNum}>{patientLabel(p)} - #{p.patNum}{p.birthdate ? ` - ${p.birthdate.slice(0, 10)}` : ''}</option>)}
      </select>
       <input aria-label={`Patient number for ${id}`} inputMode="numeric" value={value} onChange={(e) => onChange(e.target.value.replace(/\D/g, ''))} disabled={disabled} placeholder="Or enter PatNum directly" className={`${inputCls} h-9`} />
       <p className="mt-1 text-[10px] text-slate-400">Enter a patient number if the patient is not in the loaded list.</p>
    </div>
  );
}

/* ---------- foreign key lookup ---------- */
function LookupField({ id, field, value, onChange, disabled }: { id: string; field: FieldMeta; value: string; onChange: (v: string) => void; disabled?: boolean }) {
  const lookup = field.lookup!;
  const [options, setOptions] = useState<{ value: string; label: string }[]>([]);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [err, setErr] = useState('');
  useEffect(() => {
    let live = true;
    setState('loading');
    request<unknown>(lookup.path)
      .then((data) => {
        if (!live) return;
        setOptions(toRows(data).map((row) => ({
          value: displayValue(getField(row, lookup.valueKey)) || String(getField(row, lookup.valueKey) ?? ''),
          label: `${lookup.labelKeys.map((k) => displayValue(getField(row, k))).filter(Boolean).join(' ') || 'Unnamed'} (#${String(getField(row, lookup.valueKey) ?? '')})`,
        })).filter((o) => o.value !== ''));
        setState('ready');
      })
      .catch((e: Error) => { if (live) { setErr(e.message); setState('error'); } });
    return () => { live = false; };
  }, [lookup.path, lookup.valueKey]); // eslint-disable-line react-hooks/exhaustive-deps
  if (state === 'error') {
    return (
      <div>
        <input id={id} value={value} onChange={(e) => onChange(e.target.value)} disabled={disabled} placeholder={`${lookup.valueKey} value`} className={inputCls} />
        <p className="mt-1 text-[10px] text-amber-700">Could not load {lookup.resource} ({err}). Enter the {lookup.valueKey} manually.</p>
      </div>
    );
  }
  const isNumeric = lookup.valueKey !== 'FieldName';
  return (
    <div>
    <select id={id} value={value} onChange={(e) => onChange(e.target.value)} disabled={disabled || state === 'loading'} className={inputCls}>
      <option value="">{state === 'loading' ? `Loading ${lookup.resource}...` : isNumeric ? 'None' : 'Choose'}</option>
      {value && !options.some((o) => o.value === value) && <option value={value}>{lookup.valueKey} {value}</option>}
      {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
    <input aria-label={`Manual ${lookup.valueKey}`} inputMode={isNumeric ? 'numeric' : undefined} value={value} onChange={(e) => onChange(e.target.value)} disabled={disabled} placeholder={`Or enter ${lookup.valueKey} directly`} className={`${inputCls} h-9`} />
    </div>
  );
}

/* ---------- generic field ---------- */
function FieldInput({ field, value, onChange, patients, disabled, idPrefix }: {
  field: FieldMeta; value: string; onChange: (v: string) => void; patients: PatientsState; disabled?: boolean; idPrefix: string;
}) {
  const id = `${idPrefix}-${field.name}`;
  let control;
  if (field.kind === 'patient') control = <PatientPicker id={id} value={value} onChange={onChange} state={patients} disabled={disabled} />;
  else if (field.kind === 'lookup') control = <LookupField id={id} field={field} value={value} onChange={onChange} disabled={disabled} />;
  else if (field.kind === 'select') {
    control = (
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)} disabled={disabled} className={inputCls}>
        <option value="">Not set</option>
        {field.options!.map((o) => <option key={o} value={o}>{o}</option>)}
      </select>
    );
  } else if (field.kind === 'bool') {
    control = (
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)} disabled={disabled} className={inputCls}>
        <option value="">Not set</option><option value="true">Yes</option><option value="false">No</option>
      </select>
    );
  } else if (field.kind === 'textarea') {
    control = <textarea id={id} rows={3} value={value} onChange={(e) => onChange(e.target.value)} disabled={disabled} placeholder={field.placeholder} className="mt-1.5 w-full resize-y rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-[12px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70" />;
  } else {
    const type = field.kind === 'number' ? 'number' : field.kind === 'date' ? 'date' : field.kind === 'datetime' ? 'datetime-local' : 'text';
    control = <input id={id} type={type} step={field.kind === 'number' ? 'any' : undefined} value={value} onChange={(e) => onChange(e.target.value)} disabled={disabled} placeholder={field.placeholder} className={inputCls} />;
  }
  return (
    <div className={field.kind === 'textarea' ? 'sm:col-span-2' : ''}>
      <label htmlFor={id} className="block text-[11px] font-bold text-slate-700">
        {field.label}{field.required && <span className="ml-1 text-rose-500" aria-hidden="true">*</span>}
      </label>
      {control}
      {field.help && <p className="mt-1 text-[10px] leading-4 text-slate-400">{field.help}</p>}
    </div>
  );
}

/* ---------- param (filter) input ---------- */
function ParamInput({ param, value, onChange, patients, idPrefix }: { param: ParamMeta; value: string; onChange: (v: string) => void; patients: PatientsState; idPrefix: string }) {
  const id = `${idPrefix}-${param.name}`;
  return (
    <div>
      <label htmlFor={id} className="block text-[11px] font-bold text-slate-700">{param.label}{param.required && <span className="ml-1 text-rose-500">*</span>}</label>
      {param.kind === 'patient' ? <PatientPicker id={id} value={value} onChange={onChange} state={patients} optional={!param.required} />
        : param.kind === 'select' ? (
          <select id={id} value={value} onChange={(e) => onChange(e.target.value)} className={inputCls}><option value="">Any</option>{param.options!.map((o) => <option key={o} value={o}>{o}</option>)}</select>
        ) : param.kind === 'bool' ? (
          <select id={id} value={value} onChange={(e) => onChange(e.target.value)} className={inputCls}><option value="">Default</option><option value="true">Yes</option><option value="false">No</option></select>
        ) : <input id={id} type={param.kind === 'date' ? 'date' : param.kind === 'number' ? 'number' : 'text'} value={value} onChange={(e) => onChange(e.target.value)} className={inputCls} />}
      {param.help && <p className="mt-1 text-[10px] text-slate-400">{param.help}</p>}
    </div>
  );
}

function CellValue({ value }: { value: unknown }) {
  const text = displayValue(value);
  if (text === 'true' || text === 'false') {
    return <span className={`rounded-md px-1.5 py-0.5 text-[9px] font-bold ${text === 'true' ? 'bg-blue-50 text-blue-700' : 'bg-slate-100 text-slate-500'}`}>{text === 'true' ? 'Yes' : 'No'}</span>;
  }
  if (!text) return <span className="text-slate-300">-</span>;
  return <span className="line-clamp-2 max-w-[260px] break-words">{text}</span>;
}

/* ---------- main screen ---------- */
export default function ResourceScreen({ resource }: { resource: ResourceMeta }) {
  const r = resource;
  const patients = usePatients();
  const [modeId, setModeId] = useState('main');
  const mode = r.listMode?.find((m) => m.id === modeId);
  const params: ParamMeta[] = mode ? mode.params : r.params;
  const columns: ColumnMeta[] = mode ? mode.columns : r.columns;
  const paged = mode ? mode.paged : r.paged;
  const [values, setValues] = useState<Record<string, string>>({});
  const [rows, setRows] = useState<Row[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [offset, setOffset] = useState(0);
  const [offsetStack, setOffsetStack] = useState<number[]>([]);
  const [filter, setFilter] = useState('');
  const [notice, setNotice] = useState('');
  const [detail, setDetail] = useState<{ row: Row; loading: boolean; error: string } | null>(null);
  const [form, setForm] = useState<{ kind: 'create' | 'edit' | 'action'; row?: Row; action?: ActionMeta } | null>(null);
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);
  const [deleteRow, setDeleteRow] = useState<Row | null>(null);
  const [deleteText, setDeleteText] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [deleting, setDeleting] = useState(false);
  const lastQuery = useRef({ offset: 0 });
  const requestId = useRef(0);

  const paramsSnapshot = useRef(values);
  paramsSnapshot.current = values;

  const buildPath = useCallback((off: number) => {
    const vals = paramsSnapshot.current;
    const query: Record<string, string> = {};
    params.forEach((p) => { if ((vals[p.name] ?? '').trim()) query[p.name] = vals[p.name]; });
    if (paged && off > 0) query.Offset = String(off);
    if (r.singleByPatient) return `${r.basePath}/${encodeURIComponent(query.PatNum ?? '')}`;
    if (mode) return qs(mode.path, query);
    return r.listPath!(query);
  }, [params, paged, r, mode]);

  const run = useCallback(async (off = 0, stack?: number[]) => {
    const vals = paramsSnapshot.current;
    const missing = params.filter((p) => p.required && !(vals[p.name] ?? '').trim());
    if (missing.length) {
      setError(`Choose ${missing.map((p) => p.label.toLowerCase()).join(' and ')} before loading.`);
      setLoaded(false);
      return;
    }
    const id = ++requestId.current;
    setLoading(true);
    setError('');
    try {
      const data = await request<unknown>(buildPath(off));
      if (id !== requestId.current) return;
      setRows(toRows(data));
      setOffset(off);
      if (stack) setOffsetStack(stack);
      lastQuery.current = { offset: off };
      setLoaded(true);
    } catch (e) {
      if (id !== requestId.current) return;
      setRows([]);
      setLoaded(false);
      setError((e as Error).message);
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, [params, buildPath]);

  // Reset and auto-load when resource/mode changes and nothing is required.
  useEffect(() => {
    setValues({}); paramsSnapshot.current = {};
    setRows([]); setLoaded(false); setError(''); setOffset(0); setOffsetStack([]); setFilter(''); setNotice('');
    if (!params.some((p) => p.required)) void run(0, []);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [r.name, modeId]);

  const switchMode = (id: string) => { setModeId(id); };

  const visibleRows = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((row) => Object.values(row).some((v) => String(v ?? '').toLowerCase().includes(q)));
  }, [rows, filter]);

  const canEdit = Boolean(r.update);
  const nothingWritable = !r.create && !r.update && !r.del && !r.actions;

  /* --- detail --- */
  const openDetail = async (row: Row) => {
    setDetail({ row, loading: Boolean(r.getSingle), error: '' });
    if (!r.getSingle) return;
    try {
      const pk = String(getField(row, r.pk) ?? '');
      const fresh = await request<unknown>(`${r.basePath}/${encodeURIComponent(pk)}`);
      const first = toRows(fresh)[0];
      setDetail({ row: first ?? row, loading: false, error: '' });
    } catch (e) {
      setDetail({ row, loading: false, error: `Could not refresh this record from Open Dental: ${(e as Error).message}` });
    }
  };

  /* --- forms --- */
  const openCreate = () => {
    const d: Record<string, string> = {};
    r.create!.forEach((f) => { d[f.name] = values[f.name] && f.kind === 'patient' ? values[f.name] : ''; });
    setDraft(d); setFormError(''); setForm({ kind: 'create' });
  };
  const openEdit = (row: Row) => {
    const d: Record<string, string> = {};
    r.update!.fields.forEach((f) => { d[f.name] = formValue(f, getField(row, f.name)); });
    setDraft(d); setFormError(''); setDetail(null); setForm({ kind: 'edit', row });
  };
  const openAction = (action: ActionMeta) => {
    const d: Record<string, string> = {};
    action.fields.forEach((f) => { d[f.name] = f.kind === 'patient' ? values.PatNum ?? '' : ''; });
    setDraft(d); setFormError(''); setForm({ kind: 'action', action });
  };

  const formFields: FieldMeta[] = form?.kind === 'create' ? r.create! : form?.kind === 'edit' ? r.update!.fields : form?.action?.fields ?? [];
  const preview = useMemo(() => {
    if (!form) return {} as Record<string, unknown>;
    return form.kind === 'edit' ? buildUpdateBody(r, draft, form.row!) : buildBody(formFields, draft);
  }, [form, formFields, draft]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!form) return;
    const missing = missingRequired(formFields, draft, form.kind === 'edit' ? form.row : undefined);
    if (missing.length) { setFormError(`Required: ${missing.join(', ')}.`); return; }
    const body = form.kind === 'edit' ? buildUpdateBody(r, draft, form.row!) : buildBody(formFields, draft);
    if (r.name === 'Medications' && form.kind === 'create' && body.GenericNum !== undefined && body.genericName !== undefined) {
      setFormError('Use either Generic medication # or Generic name, not both.'); return;
    }
    if (r.name === 'Patients' && body.Guarantor !== undefined && body.SuperFamily !== undefined) {
      setFormError('Use either Guarantor or Super family head, not both.'); return;
    }
    if (form.kind === 'edit' && form.row) {
      if (Object.keys(body).length === 0) { setFormError('Nothing has changed, so nothing was sent to Open Dental.'); return; }
    }
    setSaving(true); setFormError('');
    try {
      let result: unknown;
      let message: string;
      if (form.kind === 'create') {
        result = await request<unknown>(r.basePath, { method: 'POST', body: JSON.stringify(body) });
        message = `${r.title}: record created in Open Dental.`;
      } else if (form.kind === 'edit') {
        result = await request<unknown>(updatePath(r, form.row!), { method: 'PUT', body: JSON.stringify(body) });
        message = `${r.title}: record ${String(getField(form.row!, r.pk) ?? '')} updated in Open Dental (${Object.keys(body).join(', ')}).`;
      } else {
        result = await request<unknown>(form.action!.path, { method: 'PUT', body: JSON.stringify(body) });
        message = `${form.action!.label}: Open Dental accepted the change.`;
      }
      const created = toRows(result)[0];
      if (form.kind === 'create' && created) {
        const id = getField(created, r.pk);
        if (id !== undefined) message += ` ${r.pk} ${String(id)}.`;
      }
      setForm(null);
      setNotice(message);
      if (loaded || !params.some((p) => p.required)) await run(lastQuery.current.offset);
    } catch (e) {
      setFormError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = async () => {
    if (!deleteRow) return;
    setDeleting(true); setDeleteError('');
    try {
      await request<unknown>(`${r.basePath}/${encodeURIComponent(String(getField(deleteRow, r.pk) ?? ''))}`, { method: 'DELETE' });
      setNotice(`${r.title}: record ${String(getField(deleteRow, r.pk))} deleted from Open Dental.`);
      setDeleteRow(null); setDeleteText('');
      await run(lastQuery.current.offset);
    } catch (e) {
      setDeleteError((e as Error).message);
    } finally { setDeleting(false); }
  };

  const hasRowActions = canEdit || r.del;

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-12 pt-7 sm:px-6 lg:px-9" data-testid={`screen-resource-${r.name}`}>
      <div className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[1px] text-slate-400">{r.group} / {r.name}</p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]" data-testid="text-resource-title">{r.title}<span className="text-blue-600">.</span></h1>
          <p className="mt-1.5 max-w-[720px] text-[12px] text-slate-500">{r.summary}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {r.actions?.map((a) => <button key={a.id} type="button" onClick={() => openAction(a)} data-testid={`button-action-${a.id}`} className={btnGhost}><Zap size={14} />{a.label}</button>)}
          {r.create && <button type="button" onClick={openCreate} data-testid="button-create-record" className={btnPrimary}><Plus size={16} />Add {r.title.toLowerCase()}</button>}
        </div>
      </div>

      <div role="note" data-testid="notice-resource-live" className="mb-5 flex items-start gap-3 rounded-2xl border border-amber-200/80 bg-[#fff8e9] px-4 py-3.5 text-amber-950">
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-amber-100 text-amber-700">{nothingWritable || (!r.create && !r.update && !r.del) ? <Lock size={16} /> : <ShieldAlert size={16} />}</span>
        <div className="min-w-0 flex-1">
          <p className="text-[12px] font-bold">{nothingWritable ? 'Live data - read only' : 'Live practice data - changes write to Open Dental'}</p>
          <p className="mt-0.5 text-[11px] leading-5 text-amber-900/80">{r.warning ?? r.readOnlyNote ?? 'Confirm the patient before making changes.'}{r.readOnlyNote && r.warning ? ` ${r.readOnlyNote}` : ''}</p>
        </div>
      </div>

      {r.listMode && (
        <div className="mb-4 flex gap-1 rounded-xl bg-slate-100 p-1 sm:w-fit" role="tablist" aria-label="Recall views">
          {[{ id: 'main', label: 'Recall records' }, ...r.listMode.map((m) => ({ id: m.id, label: m.label }))].map((tab) => (
            <button key={tab.id} role="tab" aria-selected={modeId === tab.id} type="button" onClick={() => switchMode(tab.id)} data-testid={`tab-mode-${tab.id}`}
              className={`h-8 rounded-lg px-4 text-[11px] font-bold transition ${modeId === tab.id ? 'bg-white text-blue-700 shadow-sm' : 'text-slate-500 hover:text-slate-800'}`}>{tab.label}</button>
          ))}
        </div>
      )}

      {notice && <div role="status" data-testid="resource-success" className="mb-4 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-[11px] font-semibold text-emerald-700"><Check size={15} className="mt-0.5 shrink-0" /><span className="flex-1">{notice}</span><button type="button" aria-label="Dismiss" onClick={() => setNotice('')}><X size={14} /></button></div>}

      <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]">
        {params.length > 0 && (
          <form onSubmit={(e) => { e.preventDefault(); setOffsetStack([]); void run(0, []); }} className="border-b border-slate-100 p-5 sm:p-6" data-testid="form-resource-query">
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {params.map((p) => <ParamInput key={`${modeId}-${p.name}`} param={p} value={values[p.name] ?? ''} onChange={(v) => setValues((c) => ({ ...c, [p.name]: v }))} patients={patients} idPrefix={`param-${modeId}`} />)}
            </div>
            <div className="mt-4 flex gap-2">
              <button type="submit" disabled={loading} data-testid="button-run-query" className={btnPrimary}>{loading ? <LoaderCircle size={14} className="animate-spin" /> : <Search size={14} />}{r.singleByPatient ? 'Load record' : 'Load records'}</button>
              <button type="button" onClick={() => { setValues({}); paramsSnapshot.current = {}; setOffsetStack([]); if (!params.some((p) => p.required)) void run(0, []); else { setRows([]); setLoaded(false); setError(''); } }} className={btnGhost}>Reset</button>
            </div>
          </form>
        )}

        <div className="flex flex-col gap-3 border-b border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div className="flex items-center gap-2">
            <h2 className="font-[Manrope] text-[15px] font-extrabold text-slate-800">{mode ? mode.label : 'Records'}</h2>
            <span className="rounded-md bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700" data-testid="text-resource-count">{rows.length}</span>
            <button type="button" onClick={() => void run(lastQuery.current.offset)} disabled={loading} aria-label="Refresh" data-testid="button-refresh" className="ml-1 flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 disabled:opacity-50"><RefreshCw size={14} className={loading ? 'animate-spin' : ''} /></button>
          </div>
          {rows.length > 1 && (
            <label className="relative block w-full sm:max-w-[300px]"><Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Filter loaded rows" aria-label="Filter loaded rows" className={`${inputCls} mt-0 pl-9`} /></label>
          )}
        </div>

        {error && (
          <div role="alert" data-testid="resource-error" className="m-5 flex items-start gap-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-medium text-rose-700">
            <CircleAlert size={16} className="mt-0.5 shrink-0" />
            <div className="flex-1"><p className="font-bold">Open Dental request failed</p><p className="mt-0.5">{error}</p>
              <p className="mt-1 text-rose-600/80">This is an error, not an empty result. No records were assumed.</p></div>
            <button type="button" onClick={() => void run(lastQuery.current.offset)} className="rounded-lg border border-rose-300 px-3 py-1.5 font-bold hover:bg-rose-100">Retry</button>
          </div>
        )}

        {loading && !error ? (
          <div className="space-y-3 p-6" data-testid="resource-loading" aria-busy="true">
            {[0, 1, 2, 3].map((i) => <div key={i} className="h-10 animate-pulse rounded-lg bg-slate-100" style={{ opacity: 1 - i * 0.18 }} />)}
          </div>
        ) : !error && !loaded ? (
          <div className="px-5 py-14 text-center" data-testid="resource-awaiting">
            <span className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-blue-50 text-blue-500"><Search size={19} /></span>
            <p className="mt-3 text-[12px] font-semibold text-slate-700">Choose a patient to load records</p>
            <p className="mt-1 text-[10px] text-slate-400">Open Dental requires this selection for {r.title.toLowerCase()}.</p>
          </div>
        ) : !error && rows.length === 0 ? (
          <div className="px-5 py-14 text-center" data-testid="resource-empty">
            <span className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-slate-100 text-slate-400"><Inbox size={19} /></span>
            <p className="mt-3 text-[12px] font-semibold text-slate-700">Open Dental returned no records</p>
            <p className="mt-1 text-[10px] text-slate-400">{offset > 0 ? 'This page is past the last record. Go back a page.' : 'Nothing matches the current selection.'}</p>
          </div>
        ) : !error && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] border-collapse text-left" data-testid="table-resource">
              <thead className="bg-[#fbfcfe] text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">
                <tr>{columns.map((c) => <th key={c.key} className="whitespace-nowrap px-4 py-3 first:pl-6">{c.label}</th>)}<th className="px-4 py-3 pr-6 text-right">{hasRowActions ? 'Actions' : 'View'}</th></tr>
              </thead>
              <tbody>
                {visibleRows.map((row, i) => {
                  const pk = String(getField(row, r.pk) ?? i);
                  return (
                    <tr key={`${pk}-${i}`} className="border-t border-slate-100 text-[11px] text-slate-600 transition hover:bg-slate-50/60" data-testid={`row-resource-${pk}`}>
                      {columns.map((c, ci) => <td key={c.key} className={`px-4 py-3.5 first:pl-6 ${ci === 0 ? 'font-mono text-[10px] font-semibold text-slate-500' : ''}`}><CellValue value={getField(row, c.key)} /></td>)}
                      <td className="px-4 py-3 pr-6"><div className="flex justify-end gap-1">
                        <button type="button" onClick={() => void openDetail(row)} aria-label={`View ${r.rowLabel(row)}`} data-testid={`button-view-${pk}`} className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700"><Eye size={14} /></button>
                        {canEdit && !mode && <button type="button" onClick={() => openEdit(row)} aria-label={`Edit ${r.rowLabel(row)}`} data-testid={`button-edit-${pk}`} className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-blue-50 hover:text-blue-700"><Edit2 size={14} /></button>}
                        {r.del && !mode && <button type="button" onClick={() => { setDeleteRow(row); setDeleteText(''); setDeleteError(''); }} aria-label={`Delete ${r.rowLabel(row)}`} data-testid={`button-delete-${pk}`} className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-rose-50 hover:text-rose-700"><Trash2 size={14} /></button>}
                      </div></td>
                    </tr>
                  );
                })}
                {visibleRows.length === 0 && <tr><td colSpan={columns.length + 1} className="px-6 py-10 text-center text-[11px] text-slate-500">No loaded rows match "{filter}".</td></tr>}
              </tbody>
            </table>
          </div>
        )}

        {paged && loaded && (
          <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3 text-[10px] text-slate-400 sm:px-6" data-testid="resource-pagination">
            <span>Showing {rows.length} records from offset {offset}</span>
            <div className="flex items-center gap-1">
              <button type="button" disabled={loading || offsetStack.length === 0} onClick={() => { const prev = offsetStack[offsetStack.length - 1] ?? 0; void run(prev, offsetStack.slice(0, -1)); }} data-testid="button-page-previous" className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2 font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40"><ChevronLeft size={14} />Previous</button>
              <button type="button" disabled={loading || rows.length === 0} onClick={() => void run(offset + rows.length, [...offsetStack, offset])} data-testid="button-page-next" className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2 font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40">Next<ChevronRight size={14} /></button>
            </div>
          </div>
        )}
      </section>

      <p className="mt-4 flex items-center gap-1.5 text-[10px] text-slate-400"><ExternalLink size={11} />Reference: opendental.com/site/apispecification.html - {r.basePath}</p>

      {/* detail */}
      {detail && (
        <div className="fixed inset-0 z-[90] flex justify-end bg-slate-950/35 backdrop-blur-[2px]" onMouseDown={(e) => { if (e.target === e.currentTarget) setDetail(null); }} data-testid="drawer-resource-detail">
          <section role="dialog" aria-modal="true" aria-label="Record detail" className="flex h-full w-full max-w-[460px] flex-col bg-white shadow-2xl">
            <header className="flex items-start justify-between border-b border-slate-100 px-5 py-4">
              <div><p className="text-[10px] font-bold uppercase tracking-[1px] text-blue-600">{r.name}</p><h2 className="mt-1 font-[Manrope] text-[17px] font-extrabold text-slate-900">{r.rowLabel(detail.row)}</h2></div>
              <button type="button" onClick={() => setDetail(null)} aria-label="Close detail" className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100"><X size={17} /></button>
            </header>
            <div className="flex-1 overflow-y-auto px-5 py-4">
              {detail.loading && <p className="mb-3 flex items-center gap-2 text-[11px] text-slate-500"><LoaderCircle size={14} className="animate-spin text-blue-600" />Refreshing from Open Dental...</p>}
              {detail.error && <p role="alert" className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-[10px] font-medium text-amber-800">{detail.error}</p>}
              <dl className="divide-y divide-slate-100">
                {Object.entries(detail.row).map(([k, v]) => {
                  const known = [...r.columns, ...(r.listMode?.flatMap((m) => m.columns) ?? [])].find((c) => c.key.trim().toLowerCase() === k.trim().toLowerCase());
                  const text = displayValue(typeof v === 'object' && v !== null ? JSON.stringify(v) : v);
                  return <div key={k} className="grid grid-cols-[140px_1fr] gap-3 py-2.5"><dt className="text-[10px] font-bold text-slate-400">{known?.label ?? humanize(k)}</dt><dd className="break-words text-[11px] text-slate-700">{text || <span className="text-slate-300">Not recorded</span>}</dd></div>;
                })}
              </dl>
            </div>
            {!mode && (r.update || r.del) && (
              <footer className="flex gap-2 border-t border-slate-100 px-5 py-4">
                {r.update && <button type="button" onClick={() => openEdit(detail.row)} className={btnPrimary}><Edit2 size={14} />Edit</button>}
                {r.del && <button type="button" onClick={() => { setDeleteRow(detail.row); setDeleteText(''); setDeleteError(''); setDetail(null); }} className={`${btnGhost} text-rose-700`}><Trash2 size={14} />Delete</button>}
              </footer>
            )}
          </section>
        </div>
      )}

      {/* create / edit / action form */}
      {form && (
        <div className="fixed inset-0 z-[95] flex items-end justify-center overflow-y-auto bg-slate-950/35 p-0 backdrop-blur-[2px] sm:items-center sm:p-5" data-testid="dialog-resource-backdrop">
          <section role="dialog" aria-modal="true" aria-labelledby="resource-form-title" data-testid="dialog-resource-form" className="flex max-h-[94dvh] w-full max-w-[720px] flex-col overflow-hidden rounded-t-[22px] bg-white shadow-2xl sm:rounded-[22px]">
            <header className="flex items-start justify-between border-b border-slate-100 px-5 py-4 sm:px-6">
              <div>
                <p className="text-[10px] font-bold uppercase tracking-[1px] text-amber-600">{form.kind === 'edit' ? `${r.pk} ${String(getField(form.row!, r.pk) ?? '')}` : 'Open Dental'}</p>
                <h2 id="resource-form-title" className="mt-1 font-[Manrope] text-[18px] font-extrabold text-slate-900">{form.kind === 'create' ? `Add ${r.title.toLowerCase()}` : form.kind === 'edit' ? `Edit ${r.rowLabel(form.row!)}` : form.action!.label}</h2>
                {form.kind === 'action' && <p className="mt-1 text-[11px] text-slate-500">{form.action!.description}</p>}
              </div>
              <button type="button" onClick={() => setForm(null)} aria-label="Close form" className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100"><X size={17} /></button>
            </header>
            <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col" noValidate>
              <div className="min-h-0 flex-1 overflow-y-auto px-5 py-5 sm:px-6">
                {(form.action?.warning ?? r.warning) && <p className="mb-4 flex gap-2 rounded-xl border border-amber-200 bg-[#fff8e9] px-3 py-2.5 text-[10px] leading-5 text-amber-900"><ShieldAlert size={14} className="mt-0.5 shrink-0" />{form.action?.warning ?? r.warning}</p>}
                <div className="grid gap-x-4 gap-y-4 sm:grid-cols-2">
                  {formFields.map((f) => <FieldInput key={f.name} field={f} idPrefix="form" value={draft[f.name] ?? ''} onChange={(v) => setDraft((c) => ({ ...c, [f.name]: v }))} patients={patients} disabled={form.kind === 'edit' && f.readOnlyOnUpdate} />)}
                </div>
              </div>
              <footer className="border-t border-slate-100 bg-[#fbfcfe] px-5 py-4 sm:px-6">
                <p className="mb-3 text-[10px] text-slate-500" data-testid="text-change-preview">
                  {Object.keys(preview).length === 0 ? 'No fields will be sent yet.' : <>Will send to Open Dental: <span className="font-mono text-slate-700">{Object.entries(preview).map(([k, v]) => `${k}=${typeof v === 'string' && v.length > 24 ? `${v.slice(0, 24)}...` : String(v)}`).join(', ')}</span></>}
                </p>
                {formError && <p role="alert" data-testid="resource-form-error" className="mb-3 rounded-lg bg-rose-50 px-3 py-2 text-[11px] font-medium text-rose-700">{formError}</p>}
                <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                  <button type="button" onClick={() => setForm(null)} disabled={saving} className={btnGhost}>Cancel</button>
                  <button type="submit" disabled={saving} data-testid="button-submit-resource" className={btnPrimary}>{saving && <LoaderCircle size={14} className="animate-spin" />}{saving ? 'Saving to Open Dental...' : form.kind === 'create' ? 'Create record' : form.kind === 'edit' ? 'Save changes' : form.action!.label}</button>
                </div>
              </footer>
            </form>
          </section>
        </div>
      )}

      {/* delete confirmation */}
      {deleteRow && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/45 p-4 backdrop-blur-[2px]" data-testid="dialog-delete-backdrop">
          <section role="alertdialog" aria-modal="true" aria-labelledby="delete-title" className="w-full max-w-[440px] rounded-2xl bg-white p-6 shadow-2xl" data-testid="dialog-delete-confirm">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-rose-50 text-rose-600"><Trash2 size={18} /></span>
            <h2 id="delete-title" className="mt-3 font-[Manrope] text-[17px] font-extrabold text-slate-900">Permanently delete {r.rowLabel(deleteRow)}?</h2>
            <p className="mt-2 text-[11px] leading-5 text-slate-600">{r.pk} {String(getField(deleteRow, r.pk) ?? '')} will be removed from Open Dental. This cannot be undone from SmileOS.</p>
            {r.warning && <p className="mt-2 text-[10px] leading-5 text-amber-800">{r.warning}</p>}
            <label className="mt-4 block text-[11px] font-bold text-slate-700" htmlFor="delete-confirm-input">Type DELETE to confirm
              <input id="delete-confirm-input" value={deleteText} onChange={(e) => setDeleteText(e.target.value)} autoFocus autoComplete="off" data-testid="input-delete-confirm" className={inputCls} /></label>
            {deleteError && <p role="alert" data-testid="resource-delete-error" className="mt-3 rounded-lg bg-rose-50 px-3 py-2 text-[11px] font-medium text-rose-700">{deleteError}</p>}
            <div className="mt-5 flex justify-end gap-2">
              <button type="button" onClick={() => setDeleteRow(null)} disabled={deleting} className={btnGhost}>Keep record</button>
              <button type="button" onClick={() => void confirmDelete()} disabled={deleting || deleteText !== 'DELETE'} data-testid="button-confirm-delete" className="flex h-10 items-center gap-2 rounded-xl bg-rose-600 px-4 text-[12px] font-bold text-white transition hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-40">{deleting && <LoaderCircle size={14} className="animate-spin" />}Delete permanently</button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

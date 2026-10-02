import { useEffect, useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight, FilePlus2, Plus, Search, UsersRound } from 'lucide-react';
import type { PatientsFamiliesPreviewProps } from './types';
import { accentFor, displayValue, inputClass, recordSearchText } from './format';
import PreviewNotice from './PreviewNotice';
import RecordDetail from './RecordDetail';
import RecordFormDialog from './RecordFormDialog';
import DeletePreviewDialog from './DeletePreviewDialog';

const PAGE_SIZE = 10;

export default function PatientsFamiliesPreviewScreen({ definition, rows, onCreate, onUpdate, onDelete }: PatientsFamiliesPreviewProps) {
  const [search, setSearch] = useState('');
  const [patient, setPatient] = useState('');
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [form, setForm] = useState<'create' | 'edit' | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [message, setMessage] = useState('');
  const accent = accentFor(definition.resource);

  const filtered = useMemo(() => {
    const s = search.trim().toLowerCase();
    const p = patient.trim().toLowerCase();
    return rows.filter((r) => {
      if (s && !recordSearchText(r).includes(s)) return false;
      if (p) {
        if (String(r.values.patientId ?? '').trim() !== p) return false;
      }
      return true;
    });
  }, [rows, search, patient]);

  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(page, pages - 1);
  useEffect(() => { if (page !== safePage) setPage(safePage); }, [page, safePage]);
  const visible = filtered.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);
  const selected = rows.find((r) => r.id === selectedId) ?? null;
  const cols = definition.columns.map((c) => definition.fields.find((f) => f.key === c)).filter((f) => !!f);

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9" data-testid={`screen-preview-${definition.resource}`}>
      <header className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className={`mb-1.5 flex items-center gap-2 text-[10px] font-bold uppercase tracking-[1.3px] ${accent.text}`}>
            <UsersRound size={13} /> Patients &amp; Families <span className="text-slate-300">/</span> {definition.resource}
          </p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]">{definition.title}<span className="text-blue-600">.</span></h1>
          <p className="mt-1.5 max-w-2xl text-[12px] leading-5 text-slate-500">{definition.description}</p>
        </div>
        <button type="button" onClick={() => setForm('create')} data-testid="button-preview-create" className="flex h-10 items-center gap-2 self-start rounded-xl bg-[#315fe7] px-4 text-[11px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] hover:bg-[#244fcf] sm:self-auto">
          <Plus size={15} /> New preview {definition.singular.toLowerCase()}
        </button>
      </header>

      <PreviewNotice />
      {message && <p role="status" className="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-[11px] font-semibold text-emerald-800">{message}</p>}

      <section className={`mb-5 grid grid-cols-1 gap-3 ${definition.patientScoped ? 'sm:grid-cols-2' : ''}`}>
        <label className="relative block">
          <span className="sr-only">Search {definition.title}</span>
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} placeholder={`Search all ${definition.title.toLowerCase()} values`} data-testid="input-preview-search" className={`${inputClass} mt-0 h-10 pl-9`} />
        </label>
        {definition.patientScoped && (
          <label className="relative block">
            <span className="sr-only">Filter by patient ID</span>
            <UsersRound size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input type="number" min="1" step="1" value={patient} onChange={(e) => { setPatient(e.target.value); setPage(0); }} placeholder="Filter by patient ID" data-testid="input-preview-patient-filter" className={`${inputClass} mt-0 h-10 pl-9`} />
          </label>
        )}
      </section>

      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
        <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white">
          <div className="flex items-center gap-2 border-b border-slate-100 px-5 py-4">
            <span className={`h-4 w-1 rounded-full ${accent.bar}`} />
            <h2 className="font-[Manrope] text-[14px] font-extrabold text-slate-800">Preview {definition.title.toLowerCase()}</h2>
            <span className={`rounded-md ${accent.soft} px-1.5 py-0.5 text-[9px] font-bold ${accent.text}`}>{rows.length}</span>
          </div>
          {rows.length === 0 ? (
            <div className="px-6 py-14 text-center" data-testid="preview-empty">
              <span className={`mx-auto flex h-11 w-11 items-center justify-center rounded-xl ${accent.soft} ${accent.text}`}><FilePlus2 size={19} /></span>
              <p className="mt-3 text-[12px] font-semibold text-slate-700">No preview records yet</p>
              <p className="mt-1 text-[10px] text-slate-400">This list starts empty. Add a fictional record to try the layout.</p>
              <button type="button" onClick={() => setForm('create')} className="mt-4 inline-flex h-9 items-center gap-2 rounded-lg bg-[#315fe7] px-3 text-[10px] font-bold text-white hover:bg-[#244fcf]"><Plus size={13} /> Add preview record</button>
            </div>
          ) : filtered.length === 0 ? (
            <div className="px-5 py-14 text-center text-[11px] text-slate-500" data-testid="preview-no-match">No preview records match the current filters.</div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[560px] border-collapse text-left">
                  <thead className="bg-[#fbfcfe] text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">
                    <tr>{cols.map((f) => <th key={f.key} className="px-4 py-3 first:pl-5">{f.label}</th>)}</tr>
                  </thead>
                  <tbody>
                    {visible.map((r) => (
                      <tr key={r.id} data-testid={`preview-row-${r.id}`} tabIndex={0} aria-selected={selectedId === r.id}
                        onClick={() => setSelectedId(r.id)}
                        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setSelectedId(r.id); } }}
                        className={`cursor-pointer border-t border-slate-100 outline-none transition hover:bg-slate-50/70 focus-visible:bg-blue-50/60 ${selectedId === r.id ? 'bg-blue-50/55' : ''}`}>
                        {cols.map((f) => <td key={f.key} className="max-w-[220px] truncate px-4 py-3.5 text-[11px] text-slate-700 first:pl-5 first:font-bold">{displayValue(f, r.values[f.key])}</td>)}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <footer className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 px-5 py-3">
                <span className="text-[10px] text-slate-400">Showing {visible.length} of {filtered.length} · page {safePage + 1} of {pages}</span>
                <div className="flex items-center gap-2">
                  <button type="button" onClick={() => setPage(safePage - 1)} disabled={safePage === 0} data-testid="button-preview-previous" className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2.5 text-[10px] font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40"><ChevronLeft size={13} /> Previous</button>
                  <button type="button" onClick={() => setPage(safePage + 1)} disabled={safePage >= pages - 1} data-testid="button-preview-next" className="flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2.5 text-[10px] font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40">Next <ChevronRight size={13} /></button>
                </div>
              </footer>
            </>
          )}
        </section>
        <RecordDetail definition={definition} record={selected} onEdit={() => setForm('edit')} onDelete={() => setConfirmDelete(true)} />
      </div>

      {form && (
        <RecordFormDialog definition={definition} record={form === 'edit' ? selected : null} onClose={() => setForm(null)}
          onSubmit={(values) => {
            if (form === 'edit' && selected) onUpdate(selected.id, values); else onCreate(values);
            setMessage(form === 'edit' ? 'Preview record updated. Nothing was sent to Open Dental.' : 'Preview record added. Nothing was sent to Open Dental.');
            setForm(null);
          }} />
      )}
      {confirmDelete && selected && (
        <DeletePreviewDialog singular={definition.singular} onCancel={() => setConfirmDelete(false)}
          onConfirm={() => { onDelete(selected.id); setSelectedId(null); setConfirmDelete(false); setMessage('Preview record removed. Nothing was deleted in Open Dental.'); }} />
      )}
    </div>
  );
}

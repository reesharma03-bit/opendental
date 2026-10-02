import { Edit2, MousePointerClick, Trash2 } from 'lucide-react';
import type { PreviewRecord, PreviewResourceDefinition } from './types';
import { displayValue } from './format';

interface Props { definition: PreviewResourceDefinition; record: PreviewRecord | null; onEdit: () => void; onDelete: () => void; }

export default function RecordDetail({ definition, record, onEdit, onDelete }: Props) {
  return (
    <aside aria-label={`${definition.singular} details`} data-testid="preview-detail" className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white">
      <div className="border-b border-slate-100 px-5 py-4">
        <p className="text-[9px] font-bold uppercase tracking-[1px] text-blue-600">Preview record</p>
        <h2 className="mt-1 font-[Manrope] text-[14px] font-extrabold text-slate-800">{definition.singular} details</h2>
      </div>
      {!record ? (
        <div className="px-5 py-10 text-center">
          <span className="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-slate-50 text-slate-400"><MousePointerClick size={17} /></span>
          <p className="mt-3 text-[11px] font-semibold text-slate-700">Choose a record</p>
          <p className="mt-1 text-[10px] leading-4 text-slate-400">Select a row to see every field.</p>
        </div>
      ) : (
        <div className="p-5">
          <p className="font-mono text-[9px] text-slate-400">Browser preview ID {record.id} (not an Open Dental ID)</p>
          <dl className="mt-3 divide-y divide-slate-100 border-y border-slate-100">
            {definition.fields.map((f) => (
              <div key={f.key} className="flex items-start justify-between gap-4 py-2.5">
                <dt className="text-[9px] font-semibold uppercase tracking-[.5px] text-slate-400">{f.label}</dt>
                <dd className="max-w-[60%] whitespace-pre-wrap break-words text-right text-[11px] font-semibold text-slate-700">{displayValue(f, record.values[f.key])}</dd>
              </div>
            ))}
          </dl>
          <div className="mt-4 flex gap-2">
            <button type="button" onClick={onEdit} data-testid="button-preview-edit" className="flex h-9 flex-1 items-center justify-center gap-2 rounded-xl border border-slate-200 text-[11px] font-semibold text-slate-700 hover:bg-slate-50"><Edit2 size={13} /> Edit preview</button>
            <button type="button" onClick={onDelete} data-testid="button-preview-delete" className="flex h-9 flex-1 items-center justify-center gap-2 rounded-xl border border-rose-200 text-[11px] font-semibold text-rose-700 hover:bg-rose-50"><Trash2 size={13} /> Delete preview</button>
          </div>
        </div>
      )}
    </aside>
  );
}

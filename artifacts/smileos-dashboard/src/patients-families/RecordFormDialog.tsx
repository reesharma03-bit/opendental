import { useState, type FormEvent } from 'react';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import type { PreviewRecord, PreviewResourceDefinition, PreviewValues } from './types';
import { createEmptyPreviewValues, validatePreviewValues } from './validation';
import { inputClass } from './format';

interface Props {
  definition: PreviewResourceDefinition;
  record: PreviewRecord | null;
  onClose: () => void;
  onSubmit: (values: PreviewValues) => void;
}

export default function RecordFormDialog({ definition, record, onClose, onSubmit }: Props) {
  const [values, setValues] = useState<PreviewValues>(() => ({ ...createEmptyPreviewValues(definition), ...(record?.values ?? {}) }));
  const [errors, setErrors] = useState<Record<string, string>>({});

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const trimmed = Object.fromEntries(Object.entries(values).map(([key, value]) => [
      key, typeof value === 'string' ? value.trim() : value,
    ]));
    const found = validatePreviewValues(definition, trimmed);
    setErrors(found);
    if (Object.keys(found).length === 0) onSubmit(trimmed);
  };
  const set = (k: string, v: string | boolean) => setValues((c) => ({ ...c, [k]: v }));

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[85dvh] max-w-[560px] overflow-y-auto rounded-2xl border-slate-200 bg-white p-5 sm:p-6">
        <div>
          <p className="mb-1 text-[9px] font-bold uppercase tracking-[1px] text-amber-700">Preview only. Fictional data.</p>
          <DialogTitle className="font-[Manrope] text-[18px] font-extrabold text-slate-900">{record ? `Edit ${definition.singular}` : `New ${definition.singular}`}</DialogTitle>
          <DialogDescription className="mt-1 text-[11px] leading-5 text-slate-500">This form only changes the in-memory preview list. Nothing is sent to Open Dental.</DialogDescription>
        </div>
        <form onSubmit={submit} noValidate data-testid="preview-form" className="space-y-4">
          {definition.fields.map((f) => {
            const id = `pf-${f.key}`;
            const err = errors[f.key];
            const common = {
              id, 'data-testid': `input-preview-${f.key}`, 'aria-invalid': !!err,
              'aria-required': !!f.required,
              'aria-describedby': err ? `${id}-err` : f.help ? `${id}-help` : undefined,
            };
            const v = values[f.key];
            return (
              <div key={f.key}>
                {f.type === 'checkbox' ? (
                  <label htmlFor={id} className="flex cursor-pointer items-center gap-3 rounded-xl border border-slate-200 px-3 py-3 text-[11px] font-bold text-slate-700">
                    <input {...common} type="checkbox" checked={v === true} onChange={(e) => set(f.key, e.target.checked)} className="h-4 w-4 rounded border-slate-300" />
                    {f.label}
                  </label>
                ) : (
                  <label htmlFor={id} className="block text-[11px] font-bold text-slate-700">
                    {f.label} {f.required && <span className="text-rose-500">*</span>}
                    {f.type === 'textarea' ? (
                      <textarea {...common} rows={3} value={String(v ?? '')} onChange={(e) => set(f.key, e.target.value)} className={`${inputClass} py-2`} />
                    ) : f.type === 'select' ? (
                      <select {...common} value={String(v ?? '')} onChange={(e) => set(f.key, e.target.value)} className={`${inputClass} h-10`}>
                        <option value="">Select...</option>
                        {(f.options ?? []).map((o) => <option key={o} value={o}>{o}</option>)}
                      </select>
                    ) : (
                      <input {...common} type={f.type} min={f.min} max={f.max} step={f.step} value={String(v ?? '')} onChange={(e) => set(f.key, e.target.value)} className={`${inputClass} h-10`} />
                    )}
                  </label>
                )}
                {f.help && !err && <p id={`${id}-help`} className="mt-1 text-[10px] text-slate-400">{f.help}</p>}
                {err && <p id={`${id}-err`} role="alert" className="mt-1 text-[10px] font-medium text-rose-700">{err}</p>}
              </div>
            );
          })}
          <div className="flex flex-col-reverse gap-2 border-t border-slate-100 pt-4 sm:flex-row sm:justify-end">
            <button type="button" onClick={onClose} className="h-10 rounded-xl border border-slate-200 px-4 text-[11px] font-semibold text-slate-600 hover:bg-slate-50">Cancel</button>
            <button type="submit" data-testid="button-preview-save" className="h-10 rounded-xl bg-[#315fe7] px-4 text-[11px] font-bold text-white hover:bg-[#244fcf]">{record ? 'Save preview' : 'Add preview record'}</button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

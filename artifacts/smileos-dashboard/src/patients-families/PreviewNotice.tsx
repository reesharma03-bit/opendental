import { FlaskConical } from 'lucide-react';

export default function PreviewNotice() {
  return (
    <aside role="note" data-testid="notice-preview-only" className="mb-5 flex items-start gap-3 rounded-2xl border border-amber-200/80 bg-[#fff8e9] px-4 py-3.5 text-amber-950">
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-amber-100 text-amber-700"><FlaskConical size={17} /></span>
      <div className="min-w-0 flex-1">
        <p className="text-[12px] font-bold">UI preview only. Not connected to Open Dental.</p>
        <p className="mt-0.5 text-[11px] leading-5 text-amber-900/80">The displayed records are fictional demo data. Nothing here is sent or saved to any system. Changes reset when the page reloads. Use fictional data only; never enter real patient information.</p>
      </div>
    </aside>
  );
}

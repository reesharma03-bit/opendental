/** Inline loading state for a section: slim moving bar, label and shimmering placeholder rows. */
export default function SectionLoading({ label = 'Loading', rows = 4 }: { label?: string; rows?: number }) {
  return (
    <div role="status" aria-live="polite" className="py-2" data-testid="section-loading">
      <div className="flex items-center justify-between">
        <p className="text-[11px] font-semibold text-slate-600">{label}…</p>
        <p className="text-[10px] text-slate-400">From your SmileOS database</p>
      </div>
      <div className="mt-2 h-1 w-full overflow-hidden rounded-full bg-slate-100" aria-hidden="true">
        <div className="smileos-progress h-full w-2/5 rounded-full bg-gradient-to-r from-blue-500 via-indigo-500 to-blue-600" />
      </div>
      <div className="mt-4 space-y-2.5" aria-hidden="true">
        {Array.from({ length: rows }, (_, i) => (
          <div key={i} className="flex items-center gap-3">
            <span className="smileos-shimmer h-8 w-8 shrink-0 rounded-lg" />
            <span className="smileos-shimmer h-3 rounded-full" style={{ width: `${70 - i * 9}%` }} />
            <span className="smileos-shimmer ml-auto h-3 w-16 rounded-full" />
          </div>
        ))}
      </div>
    </div>
  );
}

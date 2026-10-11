import { useEffect, useState } from 'react';
import BrandMark from './BrandMark';

/**
 * Centered loading card with an indeterminate progress bar. The bar's motion is
 * defined in index.css (smileos-progress) and is turned off for reduced-motion users.
 */
export default function LoadingPanel({
  title = 'Loading your practice',
  steps = ['Appointments', 'Patients', 'Production and collections', 'Sync status'],
  testId = 'loading-panel',
}: {
  title?: string;
  steps?: string[];
  testId?: string;
}) {
  const [step, setStep] = useState(0);

  useEffect(() => {
    if (steps.length < 2) return;
    const timer = window.setInterval(() => setStep((current) => (current + 1) % steps.length), 1400);
    return () => window.clearInterval(timer);
  }, [steps.length]);

  return (
    <div className="flex min-h-[52vh] items-center justify-center px-4 py-10" data-testid={testId}>
      <div role="status" aria-live="polite"
        className="w-full max-w-[380px] rounded-2xl border border-slate-200/80 bg-white px-8 py-9 text-center shadow-[0_12px_40px_rgba(26,49,91,0.08)]">
        <div className="relative mx-auto flex h-16 w-16 items-center justify-center">
          <span className="smileos-pulse absolute inset-0 rounded-[18px] bg-blue-500/20" aria-hidden="true" />
          <span className="relative drop-shadow-[0_8px_18px_rgba(30,58,138,0.28)]"><BrandMark size={64} animated /></span>
        </div>
        <p className="mt-4 font-[Manrope] text-[15px] font-extrabold tracking-[-0.5px] text-slate-900">smile<span className="text-blue-600">OS</span></p>
        <p className="text-[9px] font-semibold uppercase tracking-[1.6px] text-slate-400">AI practice suite</p>

        <h2 className="mt-5 font-[Manrope] text-[15px] font-extrabold tracking-[-0.3px] text-slate-900">{title}</h2>
        <p className="mt-1.5 h-4 text-[11px] font-medium text-slate-500">
          Fetching <span className="font-semibold text-slate-700">{steps[step] ?? ''}</span>…
        </p>

        <div className="mt-6 h-1.5 w-full overflow-hidden rounded-full bg-slate-100" aria-hidden="true">
          <div className="smileos-progress h-full w-2/5 rounded-full bg-gradient-to-r from-blue-500 via-indigo-500 to-blue-600" />
        </div>
        <p className="mt-3 text-[10px] text-slate-400">From your SmileOS database, synced with Open Dental</p>
      </div>
    </div>
  );
}

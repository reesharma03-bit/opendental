/**
 * The SmileOS mark (blue tile, heartbeat line, cyan dot). Animated: the heartbeat line
 * traces itself and the dot pulses (keyframes in index.css; still for reduced motion).
 */
export default function BrandMark({ size = 64, animated = false }: { size?: number; animated?: boolean }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" role="img" aria-label="SmileOS" className={animated ? 'smileos-mark' : undefined}>
      <defs>
        <linearGradient id="smileos-tile" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#2563eb" />
          <stop offset="100%" stopColor="#4338ca" />
        </linearGradient>
      </defs>
      <rect width="64" height="64" rx="18" fill="url(#smileos-tile)" />
      {/* faint track, then the line that draws over it */}
      <path d="M52 32h-8l-6 18L26 14l-6 18h-8" fill="none" stroke="#ffffff" strokeOpacity="0.22" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round" />
      <path className={animated ? 'smileos-trace' : undefined} d="M52 32h-8l-6 18L26 14l-6 18h-8" fill="none" stroke="#ffffff" strokeWidth="4.5"
        strokeLinecap="round" strokeLinejoin="round" pathLength={100} strokeDasharray={animated ? '100' : undefined} />
      <circle className={animated ? 'smileos-dot' : undefined} cx="47" cy="15" r="4.5" fill="#a5f3fc" stroke="#4338ca" strokeWidth="2" />
    </svg>
  );
}

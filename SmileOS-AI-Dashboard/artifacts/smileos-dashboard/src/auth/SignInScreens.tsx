import { useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react';
import {
  Activity, CalendarCheck2, Check, CircleAlert, Eye, EyeOff, KeyRound, LoaderCircle, Lock, LogIn,
  RefreshCw, ShieldCheck, Sparkles, UsersRound,
} from 'lucide-react';
import { changePassword, signIn } from '../lib/backendAuth';
import { useAuth } from './AuthContext';

const inputCls = 'mt-1.5 h-12 w-full rounded-xl border border-slate-200 bg-slate-50/60 px-4 text-[13.5px] text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-400 focus:bg-white focus:ring-4 focus:ring-blue-100';
const buttonCls = 'flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-[#2f5be6] to-[#4361dd] text-[13.5px] font-bold text-white shadow-[0_10px_24px_-10px_rgba(49,95,231,.7)] transition hover:from-[#244fcf] hover:to-[#3754cf] focus:outline-none focus:ring-4 focus:ring-blue-200 disabled:cursor-wait disabled:opacity-70';

const HIGHLIGHTS = [
  { icon: CalendarCheck2, title: 'Your day at a glance', text: 'Live schedule, check-ins and the patients who need attention today.' },
  { icon: RefreshCw, title: 'In step with Open Dental', text: 'Changes are saved instantly and synced to Open Dental automatically.' },
  { icon: Sparkles, title: 'AI practice assistant', text: 'Ask about schedules, allergies, claims and collections in plain English.' },
  { icon: UsersRound, title: 'Right access for every role', text: 'Dentists, front desk and billing each see exactly what they need.' },
];

const TRUST = ['Encrypted sessions', 'Automatic sign-out', 'Full audit trail'];

/** The branded split layout shared by sign-in and password screens. */
function Shell({ eyebrow, title, subtitle, children, footer }: {
  eyebrow: string; title: string; subtitle: string; children: ReactNode; footer?: ReactNode;
}) {
  return (
    <main className="flex min-h-[100dvh] bg-[#f4f6fb]">
      {/* Brand panel */}
      <aside className="relative hidden w-[46%] max-w-[640px] flex-col justify-between overflow-hidden bg-gradient-to-br from-[#1d3a99] via-[#2a4fc4] to-[#4a63d8] p-10 text-white lg:flex xl:p-14" aria-label="About SmileOS">
        <div className="pointer-events-none absolute -right-24 -top-24 h-80 w-80 rounded-full border border-white/10" />
        <div className="pointer-events-none absolute -right-8 top-10 h-52 w-52 rounded-full border border-white/10" />
        <div className="pointer-events-none absolute -bottom-32 -left-20 h-96 w-96 rounded-full bg-white/[0.04]" />

        <div className="relative flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-[14px] bg-white/15 ring-1 ring-white/25 backdrop-blur"><Activity size={22} strokeWidth={2.6} /></div>
          <div>
            <div className="font-[Manrope] text-[20px] font-extrabold tracking-[-0.6px]">smile<span className="text-cyan-200">OS</span></div>
            <div className="mt-[-2px] text-[10px] font-semibold uppercase tracking-[1.8px] text-blue-100/80">AI practice suite</div>
          </div>
        </div>

        <div className="relative max-w-[480px]">
          <p className="text-[11px] font-bold uppercase tracking-[2px] text-cyan-200">Practice management, simplified</p>
          <h2 className="mt-3 font-[Manrope] text-[32px] font-extrabold leading-[1.15] tracking-[-1.2px] xl:text-[38px]">
            Everything your dental practice runs on, in one calm workspace.
          </h2>
          <p className="mt-4 text-[13.5px] leading-[1.7] text-blue-100/85">
            SmileOS brings your patients, schedule, clinical records and billing together, keeps them in sync with
            Open Dental, and gives every team member a clear view of what matters today.
          </p>
          <ul className="mt-9 grid gap-5 xl:grid-cols-2">
            {HIGHLIGHTS.map(({ icon: Icon, title, text }) => (
              <li key={title} className="flex gap-3">
                <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white/12 ring-1 ring-white/20"><Icon size={17} /></span>
                <span>
                  <span className="block text-[13px] font-bold">{title}</span>
                  <span className="mt-0.5 block text-[11.5px] leading-[1.55] text-blue-100/75">{text}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>

        <div className="relative flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-white/15 pt-6 text-[11px] text-blue-100/85">
          <span className="flex items-center gap-1.5 font-semibold text-white"><ShieldCheck size={14} className="text-emerald-300" /> Built for protected health information</span>
          {TRUST.map((item) => <span key={item} className="flex items-center gap-1.5"><Check size={12} className="text-emerald-300" />{item}</span>)}
        </div>
      </aside>

      {/* Form panel */}
      <section className="flex flex-1 flex-col">
        <div className="flex items-center gap-3 px-6 pt-6 lg:hidden">
          <div className="flex h-10 w-10 items-center justify-center rounded-[13px] bg-gradient-to-br from-blue-600 to-indigo-700 text-white shadow-lg shadow-blue-900/15"><Activity size={20} strokeWidth={2.6} /></div>
          <div>
            <div className="font-[Manrope] text-[18px] font-extrabold tracking-[-0.6px] text-slate-900">smile<span className="text-blue-600">OS</span></div>
            <div className="mt-[-2px] text-[9.5px] font-semibold uppercase tracking-[1.65px] text-slate-400">AI practice suite</div>
          </div>
        </div>

        <div className="flex flex-1 items-center justify-center px-6 py-10 sm:px-10">
          <div className="w-full max-w-[420px]">
            <p className="text-[11px] font-bold uppercase tracking-[1.6px] text-blue-600">{eyebrow}</p>
            <h1 className="mt-2 font-[Manrope] text-[28px] font-extrabold tracking-[-1px] text-slate-900">{title}</h1>
            <p className="mt-2 text-[13px] leading-[1.65] text-slate-500">{subtitle}</p>
            {children}
            {footer}
          </div>
        </div>

        <footer className="flex flex-col items-center justify-between gap-2 border-t border-slate-200/70 px-6 py-4 text-[10.5px] text-slate-400 sm:flex-row sm:px-10">
          <span className="flex items-center gap-1.5"><Lock size={11} /> Authorised practice staff only. All access is logged.</span>
          <span>© {new Date().getFullYear()} SmileOS · Patient data handled under HIPAA safeguards</span>
        </footer>
      </section>
    </main>
  );
}

function ErrorNote({ text }: { text: string }) {
  return <p role="alert" data-testid="auth-error" className="mt-6 flex items-start gap-2.5 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[12px] font-medium leading-5 text-rose-700"><CircleAlert size={15} className="mt-0.5 shrink-0" />{text}</p>;
}

/** Password input with show/hide and a Caps Lock hint. */
function PasswordField({ label, value, onChange, autoComplete, testId, hint, autoFocus }: {
  label: string; value: string; onChange: (v: string) => void; autoComplete: string; testId: string; hint?: string; autoFocus?: boolean;
}) {
  const [visible, setVisible] = useState(false);
  const [capsLock, setCapsLock] = useState(false);
  const checkCaps = (e: KeyboardEvent<HTMLInputElement>) => setCapsLock(e.getModifierState('CapsLock'));
  return (
    <label className="block text-[12px] font-bold text-slate-700">{label}
      <span className="relative block">
        <input type={visible ? 'text' : 'password'} autoComplete={autoComplete} required autoFocus={autoFocus} value={value}
          onChange={(e) => onChange(e.target.value)} onKeyUp={checkCaps} onKeyDown={checkCaps}
          className={`${inputCls} pr-12`} data-testid={testId} />
        <button type="button" onClick={() => setVisible((v) => !v)} aria-label={visible ? 'Hide password' : 'Show password'}
          className="absolute right-2 top-1/2 mt-[3px] flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700">
          {visible ? <EyeOff size={17} /> : <Eye size={17} />}
        </button>
      </span>
      {capsLock && <span className="mt-1.5 block text-[10.5px] font-semibold text-amber-600">Caps Lock is on.</span>}
      {hint && !capsLock && <span className="mt-1.5 block text-[10.5px] font-normal text-slate-400">{hint}</span>}
    </label>
  );
}

export function SignInScreen() {
  const { setSignedIn, notice } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      setSignedIn(await signIn(email.trim(), password));
    } catch (e) {
      setError((e as Error).message);
      setPassword('');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Shell
      eyebrow="Welcome back"
      title="Sign in to your practice"
      subtitle="Use the work email and password your practice administrator set up for you."
      footer={(
        <div className="mt-8 space-y-3 border-t border-slate-200 pt-6 text-[11.5px] leading-5 text-slate-500">
          <p><b className="text-slate-700">Forgot your password?</b> Ask your practice administrator to reset it. You’ll get a temporary password to replace when you sign in.</p>
          <p><b className="text-slate-700">Shared computer?</b> Always sign out when you’re done. SmileOS also signs you out after a period of inactivity.</p>
        </div>
      )}
    >
      {notice && !error && <p role="status" className="mt-6 flex items-start gap-2.5 rounded-xl border border-blue-100 bg-blue-50 px-4 py-3 text-[12px] leading-5 text-blue-800"><ShieldCheck size={15} className="mt-0.5 shrink-0" />{notice}</p>}
      {error && <ErrorNote text={error} />}
      <form onSubmit={submit} className="mt-7 space-y-5" data-testid="form-sign-in">
        <label className="block text-[12px] font-bold text-slate-700">Work email
          <input type="email" autoComplete="username" required autoFocus value={email} onChange={(e) => setEmail(e.target.value)}
            placeholder="you@yourpractice.com" className={inputCls} data-testid="input-sign-in-email" />
        </label>
        <PasswordField label="Password" value={password} onChange={setPassword} autoComplete="current-password" testId="input-sign-in-password" />
        <button type="submit" disabled={busy} className={buttonCls} data-testid="button-sign-in">
          {busy ? <LoaderCircle size={17} className="animate-spin" /> : <LogIn size={17} />}{busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </Shell>
  );
}

const RULES: { label: string; test: (password: string) => boolean }[] = [
  { label: 'At least 12 characters', test: (p) => p.length >= 12 },
  { label: 'Contains a letter', test: (p) => /[A-Za-z]/.test(p) },
  { label: 'Contains a number', test: (p) => /\d/.test(p) },
];

/** New password form: inside the full-screen layout when a change is required, or in a dialog. */
export function ChangePasswordForm({ required, onDone, onCancel }: { required?: boolean; onDone?: () => void; onCancel?: () => void }) {
  const { setSignedIn } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const matches = confirm.length > 0 && next === confirm;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (next !== confirm) { setError('The new passwords don’t match.'); return; }
    setBusy(true);
    setError('');
    try {
      setSignedIn(await changePassword(current, next));
      onDone?.();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="mt-6 space-y-5" data-testid="form-change-password">
      {error && <ErrorNote text={error} />}
      <PasswordField label={required ? 'Temporary password' : 'Current password'} value={current} onChange={setCurrent}
        autoComplete="current-password" testId="input-current-password" autoFocus={required}
        hint={required ? 'The password you just signed in with.' : undefined} />
      <PasswordField label="New password" value={next} onChange={setNext} autoComplete="new-password" testId="input-new-password" />
      <ul className="grid gap-1.5 rounded-xl border border-slate-200 bg-slate-50/70 px-4 py-3" aria-label="Password requirements">
        {RULES.map((rule) => {
          const ok = rule.test(next);
          return (
            <li key={rule.label} className={`flex items-center gap-2 text-[11.5px] ${ok ? 'font-semibold text-emerald-700' : 'text-slate-500'}`}>
              <span className={`flex h-4 w-4 items-center justify-center rounded-full ${ok ? 'bg-emerald-100' : 'bg-slate-200'}`}>{ok && <Check size={11} strokeWidth={3} />}</span>
              {rule.label}
            </li>
          );
        })}
      </ul>
      <PasswordField label="Repeat new password" value={confirm} onChange={setConfirm} autoComplete="new-password" testId="input-confirm-password"
        hint={confirm.length > 0 ? (matches ? 'Passwords match.' : 'Passwords don’t match yet.') : undefined} />
      <button type="submit" disabled={busy} className={buttonCls} data-testid="button-change-password">
        {busy ? <LoaderCircle size={17} className="animate-spin" /> : <KeyRound size={17} />}{busy ? 'Saving…' : required ? 'Save password and continue' : 'Change password'}
      </button>
      {onCancel && <button type="button" onClick={onCancel} className="h-11 w-full rounded-xl border border-slate-200 text-[12.5px] font-semibold text-slate-600 transition hover:bg-slate-50">Cancel</button>}
    </form>
  );
}

export function ChangePasswordScreen() {
  const { user, signOut } = useAuth();
  return (
    <Shell
      eyebrow="One more step"
      title="Create your own password"
      subtitle={`Welcome${user ? `, ${user.fullName}` : ''}. You signed in with a temporary password. Choose a private one only you know before opening your workspace.`}
      footer={(
        <div className="mt-6 flex items-center justify-between border-t border-slate-200 pt-5 text-[11.5px] text-slate-500">
          <span>Signed in as <b className="text-slate-700">{user?.email}</b></span>
          <button type="button" onClick={() => void signOut()} className="font-semibold text-blue-600 hover:text-blue-800">Sign out</button>
        </div>
      )}
    >
      <ChangePasswordForm required />
    </Shell>
  );
}

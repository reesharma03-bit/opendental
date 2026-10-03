import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { CircleAlert, History, KeyRound, LoaderCircle, Lock, Plus, RefreshCw, ShieldCheck, UserCog, X } from 'lucide-react';
import { useAuth } from './auth/AuthContext';
import {
  createUser, listAudit, listRoles, listUsers, resetUserPassword, updateUser,
  type AppUser, type AuditEntry, type RoleInfo, type RoleName,
} from './lib/backendAuth';

const inputCls = 'mt-1.5 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-[12px] text-slate-700 outline-none transition focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70';
const btnPrimary = 'flex h-10 items-center justify-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[12px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf] disabled:opacity-50';

const PERMISSION_LABELS: Record<string, string> = {
  PATIENTS_READ: 'View patients', PATIENTS_WRITE: 'Edit patients',
  APPOINTMENTS_READ: 'View appointments', APPOINTMENTS_WRITE: 'Edit appointments',
  CLINICAL_READ: 'View clinical', CLINICAL_WRITE: 'Edit clinical',
  BILLING_READ: 'View billing', BILLING_WRITE: 'Edit billing',
  ASSISTANT_USE: 'AI Assistant', SYNC_MANAGE: 'Open Dental sync', USERS_MANAGE: 'Manage users', SYSTEM_ADMIN: 'System admin',
};

/** Temporary passwords: 16 characters from a crypto source, letters and digits guaranteed. */
function temporaryPassword(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';
  const bytes = crypto.getRandomValues(new Uint8Array(14));
  return `${Array.from(bytes, (b) => chars[b % chars.length]).join('')}7a`;
}

const when = (iso: string | null) => (iso ? new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date(iso)) : 'Never');

export default function UsersScreen() {
  const { user: me } = useAuth();
  const [tab, setTab] = useState<'users' | 'audit'>('users');
  const [users, setUsers] = useState<AppUser[]>([]);
  const [roles, setRoles] = useState<RoleInfo[]>([]);
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [adding, setAdding] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [issued, setIssued] = useState<{ email: string; password: string } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [u, r, a] = await Promise.all([listUsers(), listRoles(), listAudit(200)]);
      setUsers(u); setRoles(r); setAudit(a); setError('');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const change = async (target: AppUser, body: { role?: RoleName; active?: boolean }, message: string) => {
    setBusyId(target.id); setError('');
    try {
      await updateUser(target.id, body);
      setNotice(message);
      await load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const reset = async (target: AppUser) => {
    const password = temporaryPassword();
    setBusyId(target.id); setError('');
    try {
      await resetUserPassword(target.id, password);
      setIssued({ email: target.email, password });
      setNotice('');
      await load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="mx-auto max-w-[1300px] px-4 pb-12 pt-7 sm:px-6 lg:px-9" data-testid="screen-users">
      <div className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[1px] text-slate-400"><UserCog size={13} className="text-blue-500" /> Administration</p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]">Users &amp; roles<span className="text-blue-600">.</span></h1>
          <p className="mt-1.5 max-w-[720px] text-[12px] text-slate-500">Who can sign in to SmileOS and what each person may see and change. New users and password resets get a temporary password they must replace at their first sign-in.</p>
        </div>
        <div className="flex gap-2">
          <button type="button" onClick={() => void load()} disabled={loading} className="flex h-10 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 text-[11px] font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"><RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh</button>
          <button type="button" onClick={() => setAdding(true)} className={btnPrimary} data-testid="button-add-user"><Plus size={16} /> Add user</button>
        </div>
      </div>

      {error && <p role="alert" className="mb-4 flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-semibold text-rose-700"><CircleAlert size={14} className="mt-0.5 shrink-0" />{error}</p>}
      {notice && <p role="status" className="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-[11px] font-semibold text-emerald-700">{notice}</p>}
      {issued && (
        <div role="status" className="mb-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-[11.5px] text-amber-900" data-testid="temporary-password">
          <div className="flex items-start justify-between gap-3">
            <p><b>Temporary password for {issued.email}:</b> <code className="ml-1 rounded bg-white px-2 py-0.5 font-mono text-[12px] text-slate-800">{issued.password}</code></p>
            <button type="button" aria-label="Dismiss" onClick={() => setIssued(null)}><X size={14} /></button>
          </div>
          <p className="mt-1 text-[10.5px]">Give it to them privately (not by email). It is shown only once; they must change it at sign-in.</p>
        </div>
      )}

      <div className="mb-4 flex gap-1 rounded-xl bg-slate-100 p-1 sm:w-fit" role="tablist">
        {([['users', 'Users'], ['audit', 'Audit log']] as const).map(([id, label]) => (
          <button key={id} role="tab" aria-selected={tab === id} type="button" onClick={() => setTab(id)} className={`h-8 rounded-lg px-4 text-[11px] font-bold transition ${tab === id ? 'bg-white text-blue-700 shadow-sm' : 'text-slate-500 hover:text-slate-800'}`}>{label}</button>
        ))}
      </div>

      {tab === 'users' ? (
        <>
          <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[860px] border-collapse text-left">
                <thead><tr className="border-b border-slate-100 bg-slate-50/65 text-[9px] font-bold uppercase tracking-[.8px] text-slate-400"><th className="px-5 py-3">User</th><th className="px-3 py-3">Role</th><th className="px-3 py-3">Status</th><th className="px-3 py-3">Last sign-in</th><th className="px-5 py-3 text-right">Actions</th></tr></thead>
                <tbody>
                  {users.map((u) => (
                    <tr key={u.id} className="border-b border-slate-100/80 last:border-0" data-testid={`row-user-${u.email}`}>
                      <td className="px-5 py-3"><p className="text-[11.5px] font-bold text-slate-700">{u.fullName}{u.id === me?.id && <span className="ml-1.5 rounded bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700">You</span>}</p><p className="text-[10px] text-slate-400">{u.email}</p></td>
                      <td className="px-3 py-3">
                        <select aria-label={`Role for ${u.email}`} value={u.role} disabled={busyId === u.id} onChange={(e) => void change(u, { role: e.target.value as RoleName }, `${u.fullName} is now ${roles.find((r) => r.role === e.target.value)?.label ?? e.target.value}.`)} className="h-8 rounded-lg border border-slate-200 bg-white px-2 text-[11px] text-slate-700">
                          {roles.map((r) => <option key={r.role} value={r.role}>{r.label}</option>)}
                        </select>
                      </td>
                      <td className="px-3 py-3 text-[10.5px]">
                        {!u.active ? <span className="rounded-full bg-slate-100 px-2 py-1 font-semibold text-slate-500">Deactivated</span>
                          : u.locked ? <span className="inline-flex items-center gap-1 rounded-full bg-rose-50 px-2 py-1 font-semibold text-rose-600"><Lock size={10} />Locked</span>
                            : u.mustChangePassword ? <span className="rounded-full bg-amber-50 px-2 py-1 font-semibold text-amber-700">Temporary password</span>
                              : <span className="rounded-full bg-emerald-50 px-2 py-1 font-semibold text-emerald-700">Active</span>}
                      </td>
                      <td className="px-3 py-3 text-[10.5px] text-slate-500">{when(u.lastLoginAt)}</td>
                      <td className="px-5 py-3">
                        <div className="flex justify-end gap-1.5">
                          <button type="button" disabled={busyId === u.id} onClick={() => void reset(u)} className="inline-flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-2.5 text-[10px] font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"><KeyRound size={12} /> Reset password</button>
                          {u.id !== me?.id && (
                            <button type="button" disabled={busyId === u.id} onClick={() => void change(u, { active: !u.active }, `${u.fullName} ${u.active ? 'deactivated — signed out within minutes' : 'reactivated'}.`)} className={`inline-flex h-8 items-center rounded-lg border px-2.5 text-[10px] font-semibold disabled:opacity-50 ${u.active ? 'border-rose-100 text-rose-600 hover:bg-rose-50' : 'border-emerald-100 text-emerald-700 hover:bg-emerald-50'}`}>{busyId === u.id ? <LoaderCircle size={12} className="animate-spin" /> : u.active ? 'Deactivate' : 'Reactivate'}</button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                  {!loading && users.length === 0 && <tr><td colSpan={5} className="px-6 py-10 text-center text-[11px] text-slate-400">No users.</td></tr>}
                </tbody>
              </table>
            </div>
          </section>

          <section className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-5" aria-label="What each role can do">
            {roles.map((r) => (
              <div key={r.role} className="rounded-xl border border-slate-200/75 bg-white p-4">
                <p className="flex items-center gap-1.5 text-[12px] font-extrabold text-slate-800"><ShieldCheck size={13} className="text-blue-600" />{r.label}</p>
                <ul className="mt-2 space-y-1 text-[10px] text-slate-500">{r.permissions.filter((p) => p !== 'SYSTEM_ADMIN').map((p) => <li key={p}>· {PERMISSION_LABELS[p] ?? p}</li>)}</ul>
              </div>
            ))}
          </section>
        </>
      ) : (
        <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white" data-testid="section-audit-log">
          <p className="flex items-center gap-1.5 border-b border-slate-100 px-5 py-3 text-[10.5px] text-slate-500"><History size={13} /> Last 200 events: sign-ins, failed attempts, user changes and every change made through SmileOS.</p>
          <div className="max-h-[620px] overflow-auto">
            <table className="w-full min-w-[760px] border-collapse text-left">
              <thead className="sticky top-0"><tr className="border-b border-slate-100 bg-slate-50 text-[9px] font-bold uppercase tracking-[.8px] text-slate-400"><th className="px-5 py-2.5">When</th><th className="px-3 py-2.5">Who</th><th className="px-3 py-2.5">What</th><th className="px-3 py-2.5">Details</th><th className="px-5 py-2.5">From</th></tr></thead>
              <tbody>
                {audit.map((a) => (
                  <tr key={a.id} className="border-b border-slate-100/80 text-[10.5px] text-slate-600 last:border-0">
                    <td className="whitespace-nowrap px-5 py-2">{when(a.at)}</td>
                    <td className="px-3 py-2">{a.user ?? '—'}</td>
                    <td className="px-3 py-2"><span className={`rounded px-1.5 py-0.5 text-[9.5px] font-bold ${a.action.includes('FAILED') || a.action.includes('DENIED') || a.action.includes('LOCKED') ? 'bg-rose-50 text-rose-600' : 'bg-slate-100 text-slate-600'}`}>{a.action}</span></td>
                    <td className="max-w-[360px] truncate px-3 py-2" title={a.detail ?? ''}>{a.detail ?? ''}</td>
                    <td className="px-5 py-2 text-slate-400">{a.ip ?? ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {adding && <AddUserDialog roles={roles} onClose={() => setAdding(false)} onCreated={async (email, password) => { setAdding(false); setIssued({ email, password }); await load(); }} />}
    </div>
  );
}

function AddUserDialog({ roles, onClose, onCreated }: { roles: RoleInfo[]; onClose: () => void; onCreated: (email: string, password: string) => Promise<void> }) {
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [role, setRole] = useState<RoleName>('FRONT_DESK');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true); setError('');
    const password = temporaryPassword();
    try {
      await createUser({ email: email.trim(), fullName: fullName.trim(), role, password });
      await onCreated(email.trim(), password);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[90] flex items-center justify-center bg-slate-950/35 p-4 backdrop-blur-[2px]" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <section role="dialog" aria-modal="true" aria-labelledby="add-user-title" className="w-full max-w-[440px] rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl">
        <div className="flex items-start justify-between"><h2 id="add-user-title" className="font-[Manrope] text-[18px] font-extrabold text-slate-900">Add user</h2><button type="button" aria-label="Close" onClick={onClose} className="text-slate-400 hover:text-slate-700"><X size={17} /></button></div>
        <p className="mt-1 text-[11px] text-slate-500">They get a temporary password to replace at their first sign-in.</p>
        {error && <p role="alert" className="mt-3 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-[11px] font-semibold text-rose-700">{error}</p>}
        <form onSubmit={submit} className="mt-4 space-y-3.5">
          <label className="block text-[11px] font-bold text-slate-700">Full name<input required value={fullName} onChange={(e) => setFullName(e.target.value)} className={inputCls} data-testid="input-new-user-name" /></label>
          <label className="block text-[11px] font-bold text-slate-700">Email<input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className={inputCls} data-testid="input-new-user-email" /></label>
          <label className="block text-[11px] font-bold text-slate-700">Role
            <select value={role} onChange={(e) => setRole(e.target.value as RoleName)} className={inputCls} data-testid="select-new-user-role">
              {roles.map((r) => <option key={r.role} value={r.role}>{r.label}</option>)}
            </select>
          </label>
          <div className="flex justify-end gap-2 pt-1">
            <button type="button" onClick={onClose} className="h-10 rounded-xl border border-slate-200 px-4 text-[11px] font-semibold text-slate-600 hover:bg-slate-50">Cancel</button>
            <button type="submit" disabled={busy} className={btnPrimary} data-testid="button-create-user">{busy && <LoaderCircle size={14} className="animate-spin" />}Create user</button>
          </div>
        </form>
      </section>
    </div>
  );
}

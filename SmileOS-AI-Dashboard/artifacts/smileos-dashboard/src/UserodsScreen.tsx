import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Check, CircleAlert, Edit2, EyeOff, Info, KeyRound, LoaderCircle, Plus, Search, ShieldCheck, UserCog, X } from 'lucide-react';
import { fieldClass } from './components/PatientPicker';
import {
  createUserod, listEmployees, listUserGroups, listUserods, newUserProblem, toEditDraft, updateUserod, userChanges,
  type EmployeeOption, type NewUserDraft, type UserEditDraft, type UserGroup, type Userod,
} from './lib/backendUserods';
import { listProviders, type ProviderOption } from './lib/backendLookups';

const blankNewUser = (): NewUserDraft => ({ userName: '', groupNum: '', password: '', confirm: '', resetRequired: true });

/** Open Dental's own users: who can sign in to Open Dental, and their security groups. */
export default function UserodsScreen() {
  const [users, setUsers] = useState<Userod[]>([]);
  const [groups, setGroups] = useState<UserGroup[]>([]);
  const [providers, setProviders] = useState<ProviderOption[]>([]);
  const [employees, setEmployees] = useState<EmployeeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [query, setQuery] = useState('');
  const [showHidden, setShowHidden] = useState(false);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Userod | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const [u, g] = await Promise.all([listUserods(), listUserGroups()]);
      setUsers(u);
      setGroups(g);
      setError('');
    } catch (e) {
      setError(`Unable to load Open Dental users: ${(e as Error).message}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    listProviders().then(setProviders).catch(() => setProviders([]));
    listEmployees().then(setEmployees).catch(() => setEmployees([]));
  }, []);

  const groupName = (num: number) => groups.find((g) => g.userGroupNum === num)?.description ?? `Group ${num}`;
  const shown = useMemo(() => {
    const term = query.trim().toLowerCase();
    return users
      .filter((u) => showHidden || !u.hidden)
      .filter((u) => !term || `${u.userName} ${u.providerName} ${u.employeeName} ${u.email}`.toLowerCase().includes(term));
  }, [users, query, showHidden]);
  const hiddenCount = users.filter((u) => u.hidden).length;

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9" data-testid="screen-userods">
      <div className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[11px] font-semibold text-slate-400"><ShieldCheck size={13} className="text-blue-600" /> USERS &amp; SECURITY</p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]">Open Dental users<span className="text-blue-600">.</span></h1>
          <p className="mt-1.5 text-[12px] text-slate-500">Who can sign in to Open Dental, and which security groups they belong to.</p>
        </div>
        <button type="button" onClick={() => { setCreating(true); setNotice(''); }} disabled={loading} data-testid="button-create-userod"
          className="flex h-10 items-center justify-center gap-2 self-start rounded-xl bg-[#315fe7] px-4 text-[12px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] hover:bg-[#244fcf] disabled:opacity-50 sm:self-auto">
          <Plus size={16} /> Add user
        </button>
      </div>

      <div role="note" className="mb-5 flex items-start gap-3 rounded-2xl border border-blue-200/80 bg-blue-50/60 px-4 py-3.5 text-[11px] leading-5 text-blue-950">
        <Info size={16} className="mt-0.5 shrink-0 text-blue-600" />
        <p>These are <b>Open Dental</b> sign-ins, not SmileOS dashboard users (those are under your profile → Users). New users are created straight in Open Dental and their password is never stored here. Open Dental can’t delete users through its API: <b>hide</b> a user instead. User names and passwords are changed in Open Dental itself.</p>
      </div>

      {error && <div role="alert" className="mb-4 flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-medium text-rose-700"><CircleAlert size={15} className="mt-0.5 shrink-0" />{error}</div>}
      {notice && <div role="status" className="mb-4 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-[11px] font-semibold text-emerald-700"><Check size={15} />{notice}</div>}

      <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]">
        <div className="flex flex-col gap-3 border-b border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div className="flex items-center gap-2">
            <h2 className="font-[Manrope] text-[15px] font-extrabold text-slate-800">Users</h2>
            <span className="rounded-md bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700">{users.length - hiddenCount} active</span>
            {hiddenCount > 0 && <span className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[9px] font-bold text-slate-500">{hiddenCount} hidden</span>}
          </div>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            <label className="flex items-center gap-2 text-[11px] font-semibold text-slate-600">
              <input type="checkbox" checked={showHidden} onChange={(e) => setShowHidden(e.target.checked)} className="h-4 w-4 rounded border-slate-300 text-blue-600" /> Show hidden
            </label>
            <label className="relative block w-full sm:w-[260px]">
              <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search users" aria-label="Search users" className={`${fieldClass} mt-0 pl-9`} />
            </label>
          </div>
        </div>
        {loading ? (
          <div className="flex items-center justify-center gap-2 px-5 py-14 text-[12px] text-slate-500"><LoaderCircle size={17} className="animate-spin text-blue-600" /> Loading users…</div>
        ) : shown.length === 0 ? (
          <p className="px-5 py-14 text-center text-[11px] text-slate-500">{users.length ? 'No users match.' : 'No Open Dental users synced yet. Run Force Sync to copy them.'}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] border-collapse text-left">
              <thead className="bg-[#fbfcfe] text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">
                <tr><th className="px-5 py-3 sm:px-6">User</th><th className="px-4 py-3">Security groups</th><th className="px-4 py-3">Provider</th><th className="px-4 py-3">Employee</th><th className="px-4 py-3">Clinic</th><th className="px-4 py-3">Status</th><th className="px-5 py-3 text-right sm:px-6">Edit</th></tr>
              </thead>
              <tbody>
                {shown.map((u) => (
                  <tr key={u.userNum} data-testid={`row-userod-${u.userNum}`} className={`border-t border-slate-100 hover:bg-slate-50/60 ${u.hidden ? 'opacity-60' : ''}`}>
                    <td className="px-5 py-3.5 sm:px-6"><p className="text-[12px] font-bold text-slate-800">{u.userName}</p><p className="text-[9px] text-slate-400">User #{u.userNum}{u.email ? ` · ${u.email}` : ''}</p></td>
                    <td className="px-4 py-3.5"><div className="flex flex-wrap gap-1">{u.groupNums.length ? u.groupNums.map((g) => <span key={g} className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[10px] font-semibold text-slate-600">{groupName(g)}</span>) : <span className="text-[10px] text-slate-400">None</span>}</div></td>
                    <td className="px-4 py-3.5 text-[11px] text-slate-600">{u.providerName || (u.providerNum ? `#${u.providerNum}` : '—')}</td>
                    <td className="px-4 py-3.5 text-[11px] text-slate-600">{u.employeeName || (u.employeeNum ? `#${u.employeeNum}` : '—')}</td>
                    <td className="px-4 py-3.5 text-[11px] text-slate-600">{u.clinicNum || 'All'}</td>
                    <td className="px-4 py-3.5"><div className="flex flex-wrap gap-1">
                      {u.hidden ? <span className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-2 py-0.5 text-[9px] font-bold text-slate-500"><EyeOff size={10} /> Hidden</span>
                        : <span className="rounded-md bg-emerald-50 px-2 py-0.5 text-[9px] font-bold text-emerald-700">Active</span>}
                      {u.resetRequired && <span className="inline-flex items-center gap-1 rounded-md bg-amber-50 px-2 py-0.5 text-[9px] font-bold text-amber-700"><KeyRound size={10} /> Must reset password</span>}
                    </div></td>
                    <td className="px-5 py-3.5 text-right sm:px-6"><button type="button" onClick={() => { setEditing(u); setNotice(''); }} aria-label={`Edit ${u.userName}`} className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-blue-50 hover:text-blue-700"><Edit2 size={14} /></button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {creating && <NewUserDialog groups={groups} users={users} onClose={() => setCreating(false)}
        onSaved={(u) => { setCreating(false); setNotice(`User ${u.userName} created in Open Dental.`); void load(); }} />}
      {editing && <EditUserDialog user={editing} groups={groups} providers={providers} employees={employees} onClose={() => setEditing(null)}
        onSaved={(u) => { setEditing(null); setNotice(`${u.userName} updated. Saved here and sent to Open Dental.`); void load(); }} />}
    </div>
  );
}

function Dialog({ title, subtitle, onClose, children }: { title: string; subtitle: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-[90] flex items-center justify-center overflow-y-auto bg-slate-950/35 p-4 backdrop-blur-[2px]">
      <section role="dialog" aria-modal="true" aria-label={title} className="my-auto w-full max-w-[540px] rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div><p className="mb-1 flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-[1px] text-blue-600"><UserCog size={12} /> Open Dental user</p>
            <h2 className="font-[Manrope] text-[18px] font-extrabold text-slate-900">{title}</h2><p className="mt-1 text-[11px] text-slate-500">{subtitle}</p></div>
          <button type="button" onClick={onClose} aria-label="Close" className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100"><X size={17} /></button>
        </div>
        {children}
      </section>
    </div>
  );
}

function Footer({ saving, label, onCancel }: { saving: boolean; label: string; onCancel: () => void }) {
  return (
    <div className="flex flex-col-reverse gap-2 border-t border-slate-100 pt-4 sm:flex-row sm:justify-end">
      <button type="button" onClick={onCancel} disabled={saving} className="h-10 rounded-xl border border-slate-200 px-4 text-[11px] font-semibold text-slate-600 hover:bg-slate-50">Cancel</button>
      <button type="submit" disabled={saving} className="flex h-10 items-center justify-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[11px] font-bold text-white hover:bg-[#244fcf] disabled:opacity-60">
        {saving && <LoaderCircle size={14} className="animate-spin" />}{saving ? 'Saving…' : label}
      </button>
    </div>
  );
}

function NewUserDialog({ groups, users, onClose, onSaved }: { groups: UserGroup[]; users: Userod[]; onClose: () => void; onSaved: (u: Userod) => void }) {
  const [draft, setDraft] = useState<NewUserDraft>(blankNewUser());
  const [saving, setSaving] = useState(false);
  const [problem, setProblem] = useState('');
  const set = <K extends keyof NewUserDraft>(k: K, v: NewUserDraft[K]) => setDraft((d) => ({ ...d, [k]: v }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const issue = newUserProblem(draft, users);
    if (issue) { setProblem(issue); return; }
    setSaving(true); setProblem('');
    try { onSaved(await createUserod(draft)); } catch (err) { setProblem((err as Error).message); } finally { setSaving(false); }
  };

  return (
    <Dialog title="Add user" subtitle="Created straight in Open Dental. Needs Open Dental to be reachable." onClose={onClose}>
      <form onSubmit={submit} className="mt-5 space-y-4" noValidate>
        <label className="block text-[11px] font-bold text-slate-700">User name <span className="text-rose-500">*</span>
          <input value={draft.userName} onChange={(e) => set('userName', e.target.value)} autoComplete="off" data-testid="input-userod-name" className={fieldClass} placeholder="e.g. Sally" /></label>
        <label className="block text-[11px] font-bold text-slate-700">Security group <span className="text-rose-500">*</span>
          <select value={draft.groupNum} onChange={(e) => set('groupNum', e.target.value)} data-testid="select-userod-group" className={fieldClass}>
            <option value="">{groups.length ? 'Choose a group' : 'No groups synced yet'}</option>
            {groups.map((g) => <option key={g.userGroupNum} value={g.userGroupNum}>{g.description}</option>)}
          </select>
          <span className="mt-1 block text-[10px] font-normal text-slate-400">More groups can be added after the user is created.</span></label>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-[11px] font-bold text-slate-700">Password <span className="text-rose-500">*</span>
            <input type="password" value={draft.password} onChange={(e) => set('password', e.target.value)} autoComplete="new-password" data-testid="input-userod-password" className={fieldClass} /></label>
          <label className="block text-[11px] font-bold text-slate-700">Repeat password <span className="text-rose-500">*</span>
            <input type="password" value={draft.confirm} onChange={(e) => set('confirm', e.target.value)} autoComplete="new-password" className={fieldClass} /></label>
        </div>
        <p className="text-[10px] text-slate-400">At least 8 characters, with a number, an upper-case and a lower-case letter.</p>
        <label className="flex items-center gap-2.5 rounded-xl border border-slate-200 px-3 py-3 text-[11px] font-semibold text-slate-700">
          <input type="checkbox" checked={draft.resetRequired} onChange={(e) => set('resetRequired', e.target.checked)} className="h-4 w-4 rounded border-slate-300 text-blue-600" />
          Ask the user to choose a new password at first sign-in <span className="font-normal text-slate-400">(Open Dental 25.1.10+)</span></label>
        {problem && <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-[10px] font-medium text-rose-700">{problem}</p>}
        <Footer saving={saving} label="Create user" onCancel={onClose} />
      </form>
    </Dialog>
  );
}

function EditUserDialog({ user, groups, providers, employees, onClose, onSaved }: {
  user: Userod; groups: UserGroup[]; providers: ProviderOption[]; employees: EmployeeOption[]; onClose: () => void; onSaved: (u: Userod) => void;
}) {
  const [draft, setDraft] = useState<UserEditDraft>(toEditDraft(user));
  const [saving, setSaving] = useState(false);
  const [problem, setProblem] = useState('');
  const set = <K extends keyof UserEditDraft>(k: K, v: UserEditDraft[K]) => setDraft((d) => ({ ...d, [k]: v }));
  const toggleGroup = (num: number) => set('groupNums', draft.groupNums.includes(num) ? draft.groupNums.filter((g) => g !== num) : [...draft.groupNums, num]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (draft.groupNums.length === 0) { setProblem('Keep at least one security group.'); return; }
    if (Object.keys(userChanges(user, draft)).length === 0) { onClose(); return; }
    setSaving(true); setProblem('');
    try { onSaved(await updateUserod(user, draft)); } catch (err) { setProblem((err as Error).message); } finally { setSaving(false); }
  };

  return (
    <Dialog title={`Edit ${user.userName}`} subtitle="The user name and password are changed in Open Dental itself." onClose={onClose}>
      <form onSubmit={submit} className="mt-5 space-y-4" noValidate>
        <fieldset>
          <legend className="text-[11px] font-bold text-slate-700">Security groups</legend>
          <div className="mt-1.5 grid max-h-[180px] gap-1 overflow-y-auto rounded-xl border border-slate-200 p-2 sm:grid-cols-2">
            {groups.map((g) => (
              <label key={g.userGroupNum} className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-[11px] text-slate-700 hover:bg-slate-50">
                <input type="checkbox" checked={draft.groupNums.includes(g.userGroupNum)} onChange={() => toggleGroup(g.userGroupNum)} className="h-4 w-4 rounded border-slate-300 text-blue-600" />{g.description}
              </label>
            ))}
          </div>
          <span className="mt-1 block text-[10px] text-slate-400">Changing several groups at once needs Open Dental 25.1.10+.</span>
        </fieldset>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-[11px] font-bold text-slate-700">Provider
            <select value={draft.providerNum} onChange={(e) => set('providerNum', e.target.value)} className={fieldClass}>
              <option value="">None</option>{providers.map((p) => <option key={p.ProvNum} value={p.ProvNum}>{p.name}</option>)}
            </select></label>
          <label className="block text-[11px] font-bold text-slate-700">Employee
            <select value={draft.employeeNum} onChange={(e) => set('employeeNum', e.target.value)} className={fieldClass}>
              <option value="">None</option>{employees.map((em) => <option key={em.employeeNum} value={em.employeeNum}>{em.name}</option>)}
            </select></label>
          <label className="block text-[11px] font-bold text-slate-700">Clinic number <span className="font-normal text-slate-400">(0 = all)</span>
            <input inputMode="numeric" value={draft.clinicNum} onChange={(e) => set('clinicNum', e.target.value.replace(/\D/g, ''))} className={fieldClass} placeholder="0" /></label>
        </div>
        <label className="flex items-center gap-2.5 rounded-xl border border-slate-200 px-3 py-3 text-[11px] font-semibold text-slate-700">
          <input type="checkbox" checked={draft.hidden} onChange={(e) => set('hidden', e.target.checked)} className="h-4 w-4 rounded border-slate-300 text-blue-600" />
          Hidden <span className="font-normal text-slate-400">(can’t sign in to Open Dental; this is how a user is removed)</span></label>
        <label className="flex items-center gap-2.5 rounded-xl border border-slate-200 px-3 py-3 text-[11px] font-semibold text-slate-700">
          <input type="checkbox" checked={draft.resetRequired} onChange={(e) => set('resetRequired', e.target.checked)} className="h-4 w-4 rounded border-slate-300 text-blue-600" />
          Must choose a new password at next sign-in</label>
        {problem && <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-[10px] font-medium text-rose-700">{problem}</p>}
        <Footer saving={saving} label="Save changes" onCancel={onClose} />
      </form>
    </Dialog>
  );
}

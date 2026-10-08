// Open Dental's own users ("userods") and security groups, read from our database.
// New users go straight to Open Dental (the request carries a password, which is never
// stored here); changes are saved here first, then sent. Open Dental has no delete: hide instead.
// https://www.opendental.com/site/apiuserods.html
import { pick, pickNum, request, type Raw } from './backend';

export interface Userod {
  userNum: number;
  userName: string;
  groupNums: number[];
  employeeNum: number;
  employeeName: string;
  providerNum: number;
  providerName: string;
  clinicNum: number;
  email: string;
  hidden: boolean;
  resetRequired: boolean;
}

export interface UserGroup { userGroupNum: number; description: string }
export interface EmployeeOption { employeeNum: number; name: string }

export interface NewUserDraft { userName: string; groupNum: string; password: string; confirm: string; resetRequired: boolean }
export interface UserEditDraft { groupNums: number[]; providerNum: string; employeeNum: string; clinicNum: string; hidden: boolean; resetRequired: boolean }

const isTrue = (raw: Raw, key: string) => pick(raw, key).toLowerCase() === 'true';

export function mapUserod(raw: Raw): Userod {
  const groups = Array.isArray(raw.userGroupNums) ? raw.userGroupNums.map(Number).filter((n) => n > 0)
    : pickNum(raw, 'UserGroupNum') ? [pickNum(raw, 'UserGroupNum') as number] : [];
  return {
    userNum: pickNum(raw, 'UserNum') ?? 0,
    userName: pick(raw, 'UserName'),
    groupNums: groups,
    employeeNum: pickNum(raw, 'EmployeeNum') ?? 0,
    employeeName: pick(raw, 'employeeName'),
    providerNum: pickNum(raw, 'ProviderNum') ?? 0,
    providerName: pick(raw, 'providerName'),
    clinicNum: pickNum(raw, 'ClinicNum') ?? 0,
    email: pick(raw, 'emailAddress'),
    hidden: isTrue(raw, 'IsHidden'),
    resetRequired: isTrue(raw, 'IsPasswordResetRequired'),
  };
}

export async function listUserods(): Promise<Userod[]> {
  return (await request<Raw[]>('/api/database/userods?Limit=1000')).map(mapUserod)
    .sort((a, b) => a.userName.localeCompare(b.userName));
}

export async function listUserGroups(): Promise<UserGroup[]> {
  return (await request<Raw[]>('/api/database/usergroups?Limit=1000'))
    .map((raw) => ({ userGroupNum: pickNum(raw, 'UserGroupNum') ?? 0, description: pick(raw, 'Description') || `Group ${pick(raw, 'UserGroupNum')}` }))
    .filter((g) => g.userGroupNum > 0)
    .sort((a, b) => a.description.localeCompare(b.description));
}

export async function listEmployees(): Promise<EmployeeOption[]> {
  return (await request<Raw[]>('/api/database/employees?Limit=1000'))
    .filter((raw) => !isTrue(raw, 'IsHidden'))
    .map((raw) => ({
      employeeNum: pickNum(raw, 'EmployeeNum') ?? 0,
      name: `${pick(raw, 'FName')} ${pick(raw, 'LName')}`.trim() || `Employee ${pick(raw, 'EmployeeNum')}`,
    }))
    .filter((e) => e.employeeNum > 0);
}

/** Open Dental's password rule: 8+ characters with a number, an upper-case and a lower-case letter. */
export function newUserProblem(draft: NewUserDraft, existing: Userod[]): string {
  const name = draft.userName;
  if (!name.trim()) return 'Enter a user name.';
  if (name !== name.trimEnd()) return 'The user name can’t end with a space.';
  if (existing.some((u) => u.userName.toLowerCase() === name.trim().toLowerCase())) return 'That user name is already taken in Open Dental.';
  if (!draft.groupNum) return 'Choose a security group.';
  const p = draft.password;
  if (p.length < 8 || !/\d/.test(p) || !/[A-Z]/.test(p) || !/[a-z]/.test(p)) {
    return 'The password needs at least 8 characters, with a number, an upper-case and a lower-case letter.';
  }
  if (p !== draft.confirm) return 'The passwords don’t match.';
  return '';
}

export async function createUserod(draft: NewUserDraft): Promise<Userod> {
  return mapUserod(await request<Raw>('/api/database/userods', {
    method: 'POST',
    body: JSON.stringify({
      UserName: draft.userName,
      UserGroupNum: Number(draft.groupNum),
      Password: draft.password,
      IsPasswordResetRequired: String(draft.resetRequired),
    }),
  }));
}

export function toEditDraft(user: Userod): UserEditDraft {
  return {
    groupNums: [...user.groupNums],
    providerNum: String(user.providerNum || ''),
    employeeNum: String(user.employeeNum || ''),
    clinicNum: String(user.clinicNum || ''),
    hidden: user.hidden,
    resetRequired: user.resetRequired,
  };
}

/** Only what changed, in Open Dental's field names. */
export function userChanges(user: Userod, draft: UserEditDraft): Record<string, unknown> {
  const changes: Record<string, unknown> = {};
  const sameGroups = draft.groupNums.length === user.groupNums.length && draft.groupNums.every((g) => user.groupNums.includes(g));
  if (!sameGroups) changes.userGroupNums = draft.groupNums;
  if (Number(draft.providerNum || 0) !== user.providerNum) changes.ProviderNum = Number(draft.providerNum || 0);
  if (Number(draft.employeeNum || 0) !== user.employeeNum) changes.EmployeeNum = Number(draft.employeeNum || 0);
  if (Number(draft.clinicNum || 0) !== user.clinicNum) changes.ClinicNum = Number(draft.clinicNum || 0);
  if (draft.hidden !== user.hidden) changes.IsHidden = String(draft.hidden);
  if (draft.resetRequired !== user.resetRequired) changes.IsPasswordResetRequired = String(draft.resetRequired);
  return changes;
}

export async function updateUserod(user: Userod, draft: UserEditDraft): Promise<Userod> {
  const changes = userChanges(user, draft);
  if (Object.keys(changes).length === 0) return user;
  return mapUserod(await request<Raw>(`/api/database/userods/${user.userNum}`, { method: 'PUT', body: JSON.stringify(changes) }));
}

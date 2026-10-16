// Backend bridge for the SmileOS dashboard.
//
// Reads the Spring Boot backend through the same-origin development proxy,
// or VITE_BACKEND_URL when explicitly set. The backend serialises
// with a global SNAKE_CASE strategy but Open Dental itself emits PascalCase,
// so every mapper below accepts snake_case, camelCase and PascalCase keys.

export const BACKEND_URL = (import.meta.env?.VITE_BACKEND_URL as string | undefined) ?? '';

export function backendAvailable(): boolean {
  return (import.meta.env?.VITE_BACKEND_OFFLINE as string | undefined) !== '1';
}

export type Raw = Record<string, unknown>;

export function pick(raw: Raw, ...keys: string[]): string {
  for (const key of keys) {
    const value = raw[key];
    if (value !== undefined && value !== null && value !== '') return String(value);
  }
  return '';
}

export function pickNum(raw: Raw, ...keys: string[]): number | undefined {
  for (const key of keys) {
    const value = raw[key];
    if (value !== undefined && value !== null && value !== '') {
      const n = Number(value);
      if (!Number.isNaN(n)) return n;
    }
  }
  return undefined;
}

// The clinic an Admin (SmileOS administrator) is working on, chosen with the clinic switcher.
// Sent with every request; the backend only honours it for Admins (everyone else is held to
// their own clinic), so it is a convenience, not a permission.
const CLINIC_KEY = 'smileos.clinic';

export function chosenClinic(): string {
  try {
    return localStorage.getItem(CLINIC_KEY) ?? '';
  } catch {
    return '';
  }
}

/** Fired when a clinic was registered, edited, connected or disconnected: lists refresh quietly. */
export const CLINICS_CHANGED_EVENT = 'smileos:clinics-changed';

export function clinicsChanged() {
  window.dispatchEvent(new CustomEvent(CLINICS_CHANGED_EVENT));
}

/** Fired when the chosen clinic changes: the app then reloads every screen's data for it. */
export const CLINIC_EVENT = 'smileos:clinic';

export function chooseClinic(code: string | null) {
  try {
    if (code) localStorage.setItem(CLINIC_KEY, code);
    else localStorage.removeItem(CLINIC_KEY);
  } catch {
    // storage unavailable: the backend falls back to the first connected clinic
  }
  window.dispatchEvent(new CustomEvent<string | null>(CLINIC_EVENT, { detail: code }));
}

/** Fired when the backend says the session is over (401) or a password change is due. */
export const AUTH_EVENT = 'smileos:auth';
export type AuthEventDetail = { reason: 'signed-out' | 'password-change'; message: string };

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    ...init,
    // The session is an HttpOnly cookie; send it with every call.
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(chosenClinic() ? { 'X-SmileOS-Clinic': chosenClinic() } : {}),
      ...(init?.headers ?? {}),
    },
  });
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    let code = '';
    const text = await res.text();
    if (text.trim()) {
      try {
        const data = JSON.parse(text) as string | { message?: string; error?: string; code?: string };
        message = typeof data === 'string' ? data : data.message ?? data.error ?? message;
        code = typeof data === 'string' ? '' : data.code ?? '';
      } catch {
        message = `${message}: ${text.slice(0, 1000)}`;
      }
    }
    const isSignIn = path.startsWith('/api/auth/login');
    if (res.status === 401 && !isSignIn) {
      window.dispatchEvent(new CustomEvent<AuthEventDetail>(AUTH_EVENT, { detail: { reason: 'signed-out', message } }));
    } else if (res.status === 403 && code === 'PASSWORD_CHANGE_REQUIRED') {
      window.dispatchEvent(new CustomEvent<AuthEventDetail>(AUTH_EVENT, { detail: { reason: 'password-change', message } }));
    }
    throw new Error(message);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

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

/** Fired when the backend says the session is over (401) or a password change is due. */
export const AUTH_EVENT = 'smileos:auth';
export type AuthEventDetail = { reason: 'signed-out' | 'password-change'; message: string };

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    ...init,
    // The session is an HttpOnly cookie; send it with every call.
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
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

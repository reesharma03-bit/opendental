// Backend bridge for the SmileOS dashboard.
//
// Reads the Spring Boot backend (default http://localhost:8080, override with
// VITE_BACKEND_URL) which proxies the Open Dental API. The backend serialises
// with a global SNAKE_CASE strategy but Open Dental itself emits PascalCase,
// so every mapper below accepts snake_case, camelCase and PascalCase keys.

export const BACKEND_URL = (import.meta.env.VITE_BACKEND_URL as string | undefined) ?? 'http://localhost:8080';

export function backendAvailable(): boolean {
  return (import.meta.env.VITE_BACKEND_OFFLINE as string | undefined) !== '1';
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

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const data = await res.json();
      message = (data as { message?: string; error?: string }).message
        ?? (data as { error?: string }).error
        ?? message;
    } catch { /* non-JSON error body */ }
    throw new Error(message);
  }
  return (await res.json()) as T;
}

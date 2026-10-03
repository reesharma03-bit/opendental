import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { AUTH_EVENT, type AuthEventDetail } from '../lib/backend';
import { getMe, signOut as apiSignOut, type Permission, type SignedInUser } from '../lib/backendAuth';
import { resetCapabilities } from '../lib/databaseResources';

interface AuthState {
  user: SignedInUser | null;
  loading: boolean;
  /** Why the user was sent back to the sign-in screen, if they were. */
  notice: string;
  can: (permission: Permission) => boolean;
  setSignedIn: (user: SignedInUser) => void;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SignedInUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    getMe().then(setUser).catch(() => setUser(null)).finally(() => setLoading(false));
  }, []);

  // The backend ended the session (inactivity, deactivated account) or wants a new password.
  useEffect(() => {
    const onAuth = (event: Event) => {
      const { reason, message } = (event as CustomEvent<AuthEventDetail>).detail;
      if (reason === 'signed-out') {
        resetCapabilities();
        setUser(null);
        setNotice(message);
      } else {
        setUser((current) => (current ? { ...current, mustChangePassword: true } : current));
      }
    };
    window.addEventListener(AUTH_EVENT, onAuth);
    return () => window.removeEventListener(AUTH_EVENT, onAuth);
  }, []);

  const setSignedIn = useCallback((next: SignedInUser) => {
    resetCapabilities();
    setNotice('');
    setUser(next);
  }, []);

  const signOut = useCallback(async () => {
    try {
      await apiSignOut();
    } finally {
      resetCapabilities();
      setNotice('You have signed out.');
      setUser(null);
    }
  }, []);

  const value = useMemo<AuthState>(() => ({
    user,
    loading,
    notice,
    can: (permission) => Boolean(user?.permissions.includes(permission)),
    setSignedIn,
    signOut,
  }), [user, loading, notice, setSignedIn, signOut]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>');
  return value;
}

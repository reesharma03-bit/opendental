import type { ReactNode } from 'react';
import { LoaderCircle } from 'lucide-react';
import { AuthProvider, useAuth } from './AuthContext';
import { ChangePasswordScreen, SignInScreen } from './SignInScreens';

function Gate({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) {
    return <div className="flex min-h-[100dvh] items-center justify-center gap-2 bg-[#f5f7fb] text-[12px] text-slate-500"><LoaderCircle size={16} className="animate-spin text-blue-600" /> Loading…</div>;
  }
  if (!user) return <SignInScreen />;
  if (user.mustChangePassword) return <ChangePasswordScreen />;
  return <>{children}</>;
}

/** Nothing of the app renders until someone has signed in (and replaced a temporary password). */
export default function AuthGate({ children }: { children: ReactNode }) {
  return <AuthProvider><Gate>{children}</Gate></AuthProvider>;
}

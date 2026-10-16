import { useEffect, useState } from 'react';
import { Building2, Plus } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { CLINICS_CHANGED_EVENT, CLINIC_EVENT, chooseClinic, chosenClinic } from '../lib/backend';
import { listClinics, type ConnectedClinic } from '../lib/backendConnection';

/** For staff: the clinic they belong to, shown in the top bar, with whether it is connected. */
export function ClinicName() {
  const { user } = useAuth();
  if (!user || user.role === 'ADMIN' || user.role === 'DENTIST' || !user.clinicName) return null;
  return (
    <span className="hidden h-9 items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 text-[11px] font-semibold text-slate-600 md:flex" data-testid="text-current-clinic">
      <Building2 size={14} className="text-slate-400" /> {user.clinicName}
      <span className={`ml-1 rounded-full px-1.5 py-0.5 text-[9.5px] font-bold ${user.clinicConnected ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
        {user.clinicConnected ? 'Connected' : 'Not connected'}
      </span>
    </span>
  );
}

/**
 * For staff whose clinic was disconnected, on every screen: its data is hidden until it is
 * connected again; a Dentist can reconnect it on the Open Dental page.
 */
export function DisconnectedBanner({ onReconnect }: { onReconnect: () => void }) {
  const { user, can } = useAuth();
  if (!user || user.role === 'ADMIN' || user.role === 'DENTIST' || !user.clinicCode || user.clinicConnected) return null;
  return (
    <div role="status" className="sticky top-[72px] z-20 flex flex-wrap items-center gap-2 border-b border-amber-200 bg-amber-50/95 px-4 py-2 text-[11.5px] text-amber-900 backdrop-blur-md sm:px-6 lg:px-9" data-testid="banner-disconnected">
      <b>{user.clinicName} isn't connected to Open Dental.</b>
      <span>Its patients, appointments and other data are hidden until it is connected again; nothing is lost.</span>
      {can('CLINIC_CONNECT')
        ? <button type="button" onClick={onReconnect} className="ml-auto rounded-lg border border-amber-300 bg-white px-2.5 py-1 text-[11px] font-semibold text-amber-800 hover:bg-amber-100">Reconnect</button>
        : <span className="ml-auto text-amber-800">Ask your dentist or SmileOS admin to reconnect it.</span>}
    </div>
  );
}

/**
 * For Admins (SmileOS administrators), on every screen: which clinic's data the screen shows,
 * a dropdown to switch clinic, and a way to register a new one.
 */
export default function ClinicBar({ onRegister }: { onRegister: () => void }) {
  const { user } = useAuth();
  // Admins choose among every clinic, dentists among the clinics they own.
  const admin = user?.role === 'ADMIN' || user?.role === 'DENTIST';
  const [clinics, setClinics] = useState<ConnectedClinic[] | null>(null);
  const [, setChosen] = useState(chosenClinic());

  // Another part of the app chose a clinic (Open / Edit, a registration): show it, and refresh the
  // list quietly (statuses and patient counts) without blanking the bar.
  useEffect(() => {
    if (!admin) return undefined;
    const onClinic = () => {
      setChosen(chosenClinic());
      listClinics().then(setClinics).catch(() => undefined);
    };
    window.addEventListener(CLINIC_EVENT, onClinic);
    window.addEventListener(CLINICS_CHANGED_EVENT, onClinic);
    return () => {
      window.removeEventListener(CLINIC_EVENT, onClinic);
      window.removeEventListener(CLINICS_CHANGED_EVENT, onClinic);
    };
  }, [admin]);

  useEffect(() => {
    if (!admin) return;
    listClinics().then((list) => {
      setClinics(list);
      // A chosen clinic that no longer exists: back to the default.
      const chosen = chosenClinic();
      if (chosen && !list.some((c) => c.clinicCode === chosen)) {
        chooseClinic(null);
      }
    }).catch(() => setClinics([]));
  }, [admin]);

  if (!admin) return null;

  // The backend shows the chosen clinic, or the first connected one when none is chosen.
  const list = clinics ?? [];
  const current = chosenClinic() || list.find((c) => c.connected)?.clinicCode || '';
  const currentClinic = list.find((c) => c.clinicCode === current);
  const change = (value: string) => {
    chooseClinic(value); // the screens reload their data for the new clinic
  };

  return (
    <div className="sticky top-[72px] z-20 border-b border-indigo-100 bg-indigo-50/90 px-4 py-2 backdrop-blur-md sm:px-6 lg:px-9" data-testid="clinic-bar">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <span className="flex items-center gap-1.5 text-[11px] font-bold text-indigo-900">
          <Building2 size={15} className="text-indigo-600" /> Viewing clinic
        </span>
        <select value={current} onChange={(e) => change(e.target.value)} disabled={!clinics || list.length === 0}
          aria-label="Clinic whose data every screen shows" data-testid="select-clinic"
          className="h-8 min-w-[220px] max-w-full rounded-lg border border-indigo-200 bg-white px-2.5 text-[12px] font-semibold text-slate-800 outline-none focus:ring-4 focus:ring-indigo-100">
          {!clinics && <option value="">Loading clinics…</option>}
          {clinics && list.length === 0 && <option value="">No clinics yet</option>}
          {list.map((c) => (
            <option key={c.clinicCode} value={c.clinicCode}>
              {c.clinicName}{c.connected ? '' : ' (not connected)'}
            </option>
          ))}
        </select>
        {currentClinic && (
          <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${currentClinic.connected ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-600'}`}>
            {currentClinic.connected ? 'Connected' : 'Not connected'}
            {typeof currentClinic.patients === 'number' ? ` · ${currentClinic.patients.toLocaleString()} patients` : ''}
          </span>
        )}
        <span className="hidden text-[10.5px] text-indigo-800/70 lg:inline">
          {list.length > 1 ? `Every screen shows this clinic's data · ${list.length} clinics` : 'Every screen shows this clinic\'s data'}
        </span>
        <button type="button" onClick={onRegister} data-testid="button-clinic-bar-register"
          className="ml-auto flex h-8 items-center gap-1 rounded-lg border border-indigo-200 bg-white px-2.5 text-[11px] font-semibold text-indigo-700 hover:bg-indigo-50">
          <Plus size={13} /> Register a new clinic
        </button>
      </div>
    </div>
  );
}

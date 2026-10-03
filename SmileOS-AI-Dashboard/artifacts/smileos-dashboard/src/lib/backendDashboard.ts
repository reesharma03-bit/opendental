// Home dashboard: GET /api/dashboard/summary, computed from our database (never Open Dental).
// A figure the backend could not compute (e.g. payments not synced yet) is null.
import { request } from './backend';

export interface DashboardAppointment {
  aptNum: number;
  patNum: number;
  patientName: string;
  time: string | null;
  minutes: number | null;
  treatment: string;
  provider: string | null;
  status: string;
  arrived: boolean;
  dismissed: boolean;
}

export interface DailyAmount { day: string; date: string; value: number }

export interface DashboardSummary {
  date: string;
  appointments: {
    today: number;
    sameDayLastWeek: number;
    byStatus: Record<string, number>;
    nextAt: string | null;
    list: DashboardAppointment[];
  } | null;
  providers: { onDuty: number; total: number } | null;
  patients: { active: number; total: number; newLast30Days: number; newPrevious30Days: number } | null;
  production: { monthToDate: number; lastMonthToDate: number } | null;
  collections: {
    thisWeek: number;
    lastWeek: number;
    dailyThisWeek: DailyAmount[];
    dailyLastWeek: DailyAmount[];
  } | null;
  attention: {
    plannedAppointments: number | null;
    brokenThisWeek: number | null;
    recallsDueNext7Days: number | null;
    openClaims: { count: number; amount: number } | null;
  };
  recentPatients: { patNum: number; name: string; nextAppointment: string | null; firstVisit: string | null }[] | null;
  sync: {
    pendingChanges: number | null;
    failedChanges: number | null;
    lastFullSync: { at: string; status: string } | null;
    running: boolean;
  };
  unavailable: string[];
}

export const getDashboardSummary = () => request<DashboardSummary>('/api/dashboard/summary');

/** Records the patient's arrival: saved in our database first, then sent to Open Dental. */
export function checkInAppointment(aptNum: number, at = new Date()) {
  const pad = (n: number) => String(n).padStart(2, '0');
  const stamp = `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())} ${pad(at.getHours())}:${pad(at.getMinutes())}:${pad(at.getSeconds())}`;
  return request<unknown>(`/api/database/appointments/${aptNum}`, {
    method: 'PUT',
    body: JSON.stringify({ DateTimeArrived: stamp }),
  });
}

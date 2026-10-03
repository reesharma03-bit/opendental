// Appointment endpoints: GET /api/appointments/database (+?PatNum=&AptStatus=) reads our database; POST /api/appointments saves here first, then in Open Dental.
import { pick, pickNum, request, type Raw } from './backend';

export interface BackendAppointment {
  aptNum: number;
  patNum: number;
  status: string;
  dateTime: string;
  op: number;
  note: string;
  provNum: number;
  pattern: string;
  confirmed: number;
  clinicNum: number;
  procDescript: string;
  priority: string;
  isNewPatient: string;
  isHygiene: string;
}

export function mapBackendAppointment(raw: Raw): BackendAppointment {
  return {
    aptNum: pickNum(raw, 'apt_num', 'aptNum', 'AptNum') ?? 0,
    patNum: pickNum(raw, 'pat_num', 'patNum', 'PatNum') ?? 0,
    status: pick(raw, 'apt_status', 'aptStatus', 'AptStatus'),
    dateTime: pick(raw, 'apt_date_time', 'aptDateTime', 'AptDateTime'),
    op: pickNum(raw, 'op', 'Op') ?? 0,
    note: pick(raw, 'note', 'Note'),
    provNum: pickNum(raw, 'prov_num', 'provNum', 'ProvNum') ?? 0,
    pattern: pick(raw, 'pattern', 'Pattern'),
    confirmed: pickNum(raw, 'confirmed', 'Confirmed') ?? 0,
    clinicNum: pickNum(raw, 'clinic_num', 'clinicNum', 'ClinicNum') ?? 0,
    procDescript: pick(raw, 'proc_descript', 'procDescript', 'ProcDescript'),
    priority: pick(raw, 'priority', 'Priority'),
    isNewPatient: pick(raw, 'is_new_patient', 'isNewPatient', 'IsNewPatient'),
    isHygiene: pick(raw, 'is_hygiene', 'isHygiene', 'IsHygiene'),
  };
}

export async function listBackendAppointments(params?: Record<string, string>): Promise<BackendAppointment[]> {
  const query = params ? `?${new URLSearchParams(params)}` : '';
  const data = await request<Raw[]>(`/api/appointments/database${query}`);
  return (Array.isArray(data) ? data : []).map(mapBackendAppointment);
}

export interface CreateAppointmentBody {
  PatNum: number;
  AptDateTime: string;
  Op: number;
  AptStatus?: string;
  Pattern?: string;
  Note?: string;
  ProvNum?: number;
  ClinicNum?: number;
  Confirmed?: number;
}

export async function createBackendAppointment(body: CreateAppointmentBody): Promise<BackendAppointment> {
  // Send snake_case (what the global SNAKE_CASE strategy binds) plus the
  // PascalCase originals so either naming convention deserializes.
  const payload: Record<string, unknown> = {};
  const set = (value: unknown, ...keys: string[]) => {
    if (value === undefined || value === null) return;
    keys.forEach((k) => (payload[k] = value));
  };
  set(body.PatNum, 'pat_num', 'PatNum');
  set(body.AptDateTime, 'apt_date_time', 'AptDateTime');
  set(body.Op, 'op', 'Op');
  set(body.AptStatus, 'apt_status', 'AptStatus');
  set(body.Pattern, 'pattern', 'Pattern');
  set(body.Note, 'note', 'Note');
  set(body.ProvNum, 'prov_num', 'ProvNum');
  set(body.ClinicNum, 'clinic_num', 'ClinicNum');
  set(body.Confirmed, 'confirmed', 'Confirmed');
  return mapBackendAppointment(await request<Raw>('/api/appointments', { method: 'POST', body: JSON.stringify(payload) }));
}

/** Fields an appointment edit may change (Open Dental's names). */
export interface UpdateAppointmentBody {
  AptStatus?: string;
  Pattern?: string;
  Note?: string;
  Op?: number;
  ProvNum?: number;
  AptDateTime?: string;
  IsHygiene?: string;
  IsNewPatient?: string;
  Priority?: string;
}

/** Saved in our database first, then sent to Open Dental by the backend. */
export async function updateBackendAppointment(aptNum: number, body: UpdateAppointmentBody): Promise<BackendAppointment> {
  return mapBackendAppointment(await request<Raw>(`/api/database/appointments/${aptNum}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  }));
}

// Appointment endpoints: GET /api/appointments (+?PatNum=&AptStatus=), POST /api/appointments.
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
  };
}

export async function listBackendAppointments(params?: Record<string, string>): Promise<BackendAppointment[]> {
  const query = params ? `?${new URLSearchParams(params)}` : '';
  const data = await request<Raw[]>(`/api/appointments${query}`);
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
  return mapBackendAppointment(await request<Raw>('/api/appointments', { method: 'POST', body: JSON.stringify(body) }));
}

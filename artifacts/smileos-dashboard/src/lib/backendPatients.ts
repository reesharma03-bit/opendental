// Patient endpoints: GET /api/patients (+?LName=&FName= search), POST /api/patients.
import { pick, pickNum, request, type Raw } from './backend';

export interface BackendPatient {
  patNum: number;
  firstName: string;
  lastName: string;
  birthdate: string;
  gender: string;
  email: string;
  phone: string;
  homePhone: string;
  address: string;
  address2: string;
  city: string;
  state: string;
  zip: string;
  status: string;
  chartNumber: string;
  preferContactMethod: string;
  medUrgNote: string;
  apptModNote: string;
  dateFirstVisit: string;
  language: string;
  medicaidId: string;
}

export function mapBackendPatient(raw: Raw): BackendPatient {
  return {
    patNum: pickNum(raw, 'pat_num', 'patNum', 'PatNum') ?? 0,
    firstName: pick(raw, 'f_name', 'fname', 'fName', 'FName'),
    lastName: pick(raw, 'l_name', 'lname', 'lName', 'LName'),
    birthdate: pick(raw, 'birthdate', 'Birthdate'),
    gender: pick(raw, 'gender', 'Gender'),
    email: pick(raw, 'email', 'Email'),
    phone: pick(raw, 'wireless_phone', 'wirelessphone', 'hm_phone', 'hmphone', 'WirelessPhone', 'HmPhone'),
    homePhone: pick(raw, 'hm_phone', 'hmPhone', 'HmPhone'),
    address: pick(raw, 'address', 'Address'),
    address2: pick(raw, 'address2', 'Address2'),
    city: pick(raw, 'city', 'City'),
    state: pick(raw, 'state', 'State'),
    zip: pick(raw, 'zip', 'Zip'),
    status: pick(raw, 'pat_status', 'patstatus', 'PatStatus'),
    chartNumber: pick(raw, 'chart_number', 'chartNumber', 'ChartNumber'),
    preferContactMethod: pick(raw, 'prefer_contact_method', 'preferContactMethod', 'PreferContactMethod'),
    medUrgNote: pick(raw, 'med_urg_note', 'medUrgNote', 'MedUrgNote'),
    apptModNote: pick(raw, 'appt_mod_note', 'apptModNote', 'ApptModNote'),
    dateFirstVisit: pick(raw, 'date_first_visit', 'dateFirstVisit', 'DateFirstVisit'),
    language: pick(raw, 'language', 'Language'),
    medicaidId: pick(raw, 'medicaid_id', 'medicaidId', 'MedicaidID', 'MedicaidId'),
  };
}

export async function listBackendPatients(search: string): Promise<BackendPatient[]> {
  const term = search.trim();
  if (!term) return (await request<Raw[]>('/api/patients')).map(mapBackendPatient);
  const [byLast, byFirst] = await Promise.all([
    request<Raw[]>(`/api/patients?${new URLSearchParams({ LName: term })}`),
    request<Raw[]>(`/api/patients?${new URLSearchParams({ FName: term })}`),
  ]);
  const seen = new Map<number, BackendPatient>();
  [...byLast, ...byFirst].map(mapBackendPatient).forEach((p) => seen.set(p.patNum, p));
  return [...seen.values()];
}

export async function createBackendPatient(input: Record<string, string>): Promise<BackendPatient> {
  const body: Record<string, string> = {};
  for (const [k, v] of Object.entries(input)) {
    if (v !== undefined && v !== null && String(v).trim() !== '') body[k] = String(v);
  }
  if (body.phone) { body.WirelessPhone = body.phone; delete body.phone; }
  if (body.birthdate) { body.Birthdate = body.birthdate; delete body.birthdate; }
  return mapBackendPatient(await request<Raw>('/api/patients', { method: 'POST', body: JSON.stringify(body) }));
}

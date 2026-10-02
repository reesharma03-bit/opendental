// Patient endpoints: GET /api/patients (+?LName=&FName= search), POST /api/patients.
import { pick, pickNum, request, type Raw } from './backend';

export interface BackendPatient {
  recordKey: string;
  patNum: number;
  firstName: string;
  lastName: string;
  middleInitial: string;
  preferred: string;
  birthdate: string;
  gender: string;
  email: string;
  phone: string;
  homePhone: string;
  address: string;
  address2: string;
  city: string;
  state: string;
  clinicAbbr: string;
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
  const patNum = pickNum(raw, 'pat_num', 'patNum', 'PatNum') ?? 0;
  return {
    recordKey: pick(raw, 'record_key', 'recordKey') || `P-${patNum}`,
    patNum,
    firstName: pick(raw, 'f_name', 'fname', 'fName', 'FName'),
    lastName: pick(raw, 'l_name', 'lname', 'lName', 'LName'),
    middleInitial: pick(raw, 'middle_i', 'middleI', 'middlei', 'MiddleI'),
    preferred: pick(raw, 'preferred', 'Preferred'),
    birthdate: pick(raw, 'birthdate', 'Birthdate'),
    gender: pick(raw, 'gender', 'Gender'),
    email: pick(raw, 'email', 'Email'),
    phone: pick(raw, 'wireless_phone', 'wirelessphone', 'hm_phone', 'hmphone', 'WirelessPhone', 'HmPhone'),
    homePhone: pick(raw, 'hm_phone', 'hmPhone', 'HmPhone'),
    address: pick(raw, 'address', 'Address'),
    address2: pick(raw, 'address2', 'Address2'),
    city: pick(raw, 'city', 'City'),
    state: pick(raw, 'state', 'State'),
    clinicAbbr: pick(raw, 'clinic_abbr', 'clinicAbbr'),
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

export async function listSupabasePatients(search: string): Promise<BackendPatient[]> {
  const term = search.trim();
  const path = '/api/patients/database';
  const query = term ? `?${new URLSearchParams({ Search: term })}` : '';
  return (await request<Raw[]>(`${path}${query}`)).map(mapBackendPatient);
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
  const src: Record<string, string> = {};
  for (const [k, v] of Object.entries(input)) {
    if (v !== undefined && v !== null && String(v).trim() !== '') src[k] = String(v);
  }
  // The REST API binds snake_case (global SNAKE_CASE strategy), but we also send
  // camel/Pascal variants per field so the body still binds if naming changes;
  // Spring ignores unknown keys.
  const body: Record<string, string> = {};
  const set = (value: string | undefined, ...keys: string[]) => {
    if (!value) return;
    keys.forEach((k) => (body[k] = value));
  };
  set(src.firstName, 'fname', 'f_name', 'FName');
  set(src.lastName, 'lname', 'l_name', 'LName');
  set(src.middleName, 'middle_i', 'middleI', 'MiddleI');
  set(src.preferredName, 'preferred', 'Preferred');
  set(src.birthdate, 'birthdate', 'Birthdate');
  set(src.gender, 'gender', 'Gender');
  set(src.status, 'pat_status', 'patStatus', 'PatStatus');
  set(src.phone, 'wireless_phone', 'wirelessPhone', 'WirelessPhone');
  set(src.homePhone, 'hm_phone', 'hmPhone', 'HmPhone');
  set(src.email, 'email', 'Email');
  set(src.address, 'address', 'Address');
  set(src.address2, 'address2', 'Address2');
  set(src.city, 'city', 'City');
  set(src.state, 'state', 'State');
  set(src.zip, 'zip', 'Zip');
  set(src.preferContactMethod, 'prefer_contact_method', 'preferContactMethod', 'PreferContactMethod');
  return mapBackendPatient(await request<Raw>('/api/patients', { method: 'POST', body: JSON.stringify(body) }));
}

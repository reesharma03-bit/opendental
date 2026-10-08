// Open Dental's Account Module views for a patient's family, read live (Open Dental computes
// them from the whole ledger). https://www.opendental.com/site/apiaccountmodules.html
import { pick, pickNum, request, type Raw } from './backend';

export interface Aging {
  bal0to30: number; bal31to60: number; bal61to90: number; balOver90: number;
  total: number; insEst: number; estBal: number; patEstBal: number; unearned: number;
}

export interface PatientBalance { patNum: number; name: string; balance: number; isFamilyTotal: boolean }

export interface LedgerLine {
  objectType: string; primaryKey: string; type: string; serviceDate: string; transDate: string;
  patient: string; patNum: string; reference: string; charge: number; credit: number;
  provider: string; insBal: string; acctBal: string; isDayTotal: boolean;
}

const base = (patNum: number) => `/api/accountmodules/${patNum}`;
const money = (raw: Raw, key: string) => pickNum(raw, key) ?? 0;

export async function getAging(patNum: number): Promise<Aging> {
  const raw = await request<Raw>(`${base(patNum)}/Aging`);
  return {
    bal0to30: money(raw, 'Bal_0_30'), bal31to60: money(raw, 'Bal_31_60'), bal61to90: money(raw, 'Bal_61_90'),
    balOver90: money(raw, 'BalOver90'), total: money(raw, 'Total'), insEst: money(raw, 'InsEst'),
    estBal: money(raw, 'EstBal'), patEstBal: money(raw, 'PatEstBal'), unearned: money(raw, 'Unearned'),
  };
}

export async function getPatientBalances(patNum: number): Promise<PatientBalance[]> {
  const rows = await request<Raw[]>(`${base(patNum)}/PatientBalances`);
  return rows.map((raw) => {
    const name = pick(raw, 'Name');
    return { patNum: pickNum(raw, 'PatNum') ?? 0, name, balance: money(raw, 'Balance'), isFamilyTotal: name === 'Entire Family' };
  });
}

export async function getServiceDateView(patNum: number, family: boolean): Promise<LedgerLine[]> {
  const rows = await request<Raw[]>(`${base(patNum)}/ServiceDateView?isFamily=${family}`);
  return rows.map((raw) => ({
    objectType: pick(raw, 'ObjectType'), primaryKey: pick(raw, 'PrimaryKey'), type: pick(raw, 'Type'),
    serviceDate: pick(raw, 'ServiceDate'), transDate: pick(raw, 'TransDate'), patient: pick(raw, 'Patient'),
    patNum: pick(raw, 'PatNum'), reference: pick(raw, 'Reference'), charge: money(raw, 'Charge'),
    credit: money(raw, 'Credit'), provider: pick(raw, 'Provider'), insBal: pick(raw, 'InsBal'), acctBal: pick(raw, 'AcctBal'),
    isDayTotal: pick(raw, 'Type') === 'Day Total',
  }));
}

export const formatMoney = (value: number) =>
  value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

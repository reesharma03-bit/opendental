import { pick, pickNum, request, type Raw } from './backend';

export const DISEASE_DEFINITION_PAGE_SIZE = 100;

export interface BackendDiseaseDefinition {
  diseaseDefNum: number;
  diseaseName: string;
  isHidden: boolean;
  dateTStamp: string;
  icd9Code: string;
  icd10Code: string;
  snomedCode: string;
}

export function mapBackendDiseaseDefinition(raw: Raw): BackendDiseaseDefinition {
  return {
    diseaseDefNum: pickNum(raw, 'DiseaseDefNum', 'disease_def_num', 'diseaseDefNum') ?? 0,
    diseaseName: pick(raw, 'DiseaseName', 'disease_name', 'diseaseName'),
    isHidden: pick(raw, 'IsHidden', 'is_hidden', 'isHidden').toLowerCase() === 'true',
    dateTStamp: pick(raw, 'DateTStamp', 'date_t_stamp', 'dateTStamp'),
    icd9Code: pick(raw, 'ICD9Code', 'icd9_code', 'icd9Code'),
    icd10Code: pick(raw, 'ICD10Code', 'icd10_code', 'icd10Code'),
    snomedCode: pick(raw, 'SnomedCode', 'snomed_code', 'snomedCode'),
  };
}

export async function listBackendDiseaseDefinitions(offset = 0): Promise<BackendDiseaseDefinition[]> {
  const params = new URLSearchParams({
    Limit: String(DISEASE_DEFINITION_PAGE_SIZE),
    Offset: String(offset),
  });
  const rows = await request<Raw[]>(`/api/database/diseasedefs?${params.toString()}`);
  return rows.map(mapBackendDiseaseDefinition);
}

export async function getBackendDiseaseDefinition(
  diseaseDefNum: number,
): Promise<BackendDiseaseDefinition> {
  return mapBackendDiseaseDefinition(await request<Raw>(`/api/database/diseasedefs/${diseaseDefNum}`));
}

// Open Dental documents a bodyless 201 response for this operation.
export async function createBackendDiseaseDefinition(diseaseName: string): Promise<void> {
  await request<void>('/api/database/diseasedefs', {
    method: 'POST',
    body: JSON.stringify({ DiseaseName: diseaseName.trim() }),
  });
}
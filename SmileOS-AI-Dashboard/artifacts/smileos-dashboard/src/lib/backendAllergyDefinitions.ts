import { pick, pickNum, request, type Raw } from './backend';

export interface BackendAllergyDefinition {
  allergyDefNum: number;
  description: string;
  isHidden: boolean;
  dateTStamp: string;
  snomedType: string;
  medicationNum: number;
  uniiCode: string;
}

export type AllergyDefinitionDraft = {
  description: string;
  isHidden: boolean;
};

export function mapBackendAllergyDefinition(raw: Raw): BackendAllergyDefinition {
  return {
    allergyDefNum: pickNum(raw, 'AllergyDefNum', 'allergy_def_num', 'allergyDefNum') ?? 0,
    description: pick(raw, 'Description ', 'Description', 'description', 'description_'),
    isHidden: pick(raw, 'IsHidden', 'is_hidden', 'isHidden').toLowerCase() === 'true',
    dateTStamp: pick(raw, 'DateTStamp', 'date_t_stamp', 'dateTStamp'),
    snomedType: pick(raw, 'SnomedType', 'snomed_type', 'snomedType'),
    medicationNum: pickNum(raw, 'MedicationNum', 'medication_num', 'medicationNum') ?? 0,
    uniiCode: pick(raw, 'UniiCode', 'unii_code', 'uniiCode'),
  };
}

export async function listBackendAllergyDefinitions(offset = 0): Promise<BackendAllergyDefinition[]> {
  const params = new URLSearchParams({ Limit: '100' });
  if (offset > 0) params.set('Offset', String(offset));
  const query = `?${params.toString()}`;
  const rows = await request<Raw[]>(`/api/database/allergydefs${query}`);
  return rows.map(mapBackendAllergyDefinition);
}

export async function getBackendAllergyDefinition(
  allergyDefNum: number,
): Promise<BackendAllergyDefinition> {
  const row = await request<Raw>(`/api/database/allergydefs/${allergyDefNum}`);
  return mapBackendAllergyDefinition(row);
}

export async function createBackendAllergyDefinition(
  draft: AllergyDefinitionDraft,
): Promise<BackendAllergyDefinition> {
  const row = await request<Raw>('/api/database/allergydefs', {
    method: 'POST',
    body: JSON.stringify({ Description: draft.description.trim() }),
  });
  return mapBackendAllergyDefinition(row);
}

export async function updateBackendAllergyDefinition(
  allergyDefNum: number,
  draft: AllergyDefinitionDraft,
): Promise<BackendAllergyDefinition> {
  const row = await request<Raw>(`/api/database/allergydefs/${allergyDefNum}`, {
    method: 'PUT',
    body: JSON.stringify({
      Description: draft.description.trim(),
      IsHidden: String(draft.isHidden),
    }),
  });
  return mapBackendAllergyDefinition(row);
}
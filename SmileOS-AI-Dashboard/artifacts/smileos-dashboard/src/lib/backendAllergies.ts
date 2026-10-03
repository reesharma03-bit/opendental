import { pick, pickNum, request, type Raw } from './backend';

export interface BackendAllergy {
  allergyNum: number;
  allergyDefNum?: number;
  patNum: number;
  description: string;
  snomedType: string;
  reaction: string;
  isActive: boolean;
  dateAdverseReaction: string;
  dateTStamp: string;
}

export type AllergyDraft = {
  description: string;
  reaction: string;
  dateAdverseReaction: string;
  isActive: boolean;
};

export function mapBackendAllergy(raw: Raw): BackendAllergy {
  const dateAdverseReaction = pick(
    raw,
    'date_adverse_reaction',
    'dateAdverseReaction',
    'DateAdverseReaction',
  );
  return {
    allergyNum: pickNum(raw, 'allergy_num', 'allergyNum', 'AllergyNum') ?? 0,
    allergyDefNum: pickNum(raw, 'allergy_def_num', 'allergyDefNum', 'AllergyDefNum'),
    patNum: pickNum(raw, 'pat_num', 'patNum', 'PatNum') ?? 0,
    description: pick(raw, 'def_description', 'defDescription'),
    snomedType: pick(raw, 'def_snomed_type', 'defSnomedType'),
    reaction: pick(raw, 'reaction', 'Reaction'),
    isActive: pick(raw, 'status_is_active', 'statusIsActive', 'StatusIsActive').toLowerCase() === 'true',
    dateAdverseReaction: dateAdverseReaction.startsWith('0001-01-01') ? '' : dateAdverseReaction.slice(0, 10),
    dateTStamp: pick(raw, 'date_t_stamp', 'dateTStamp', 'DateTStamp'),
  };
}

export async function listBackendAllergies(patNum: number): Promise<BackendAllergy[]> {
  const rows = await request<Raw[]>(`/api/database/allergies?${new URLSearchParams({ PatNum: String(patNum) })}`);
  return rows.map(mapBackendAllergy);
}

export async function createBackendAllergy(patNum: number, draft: AllergyDraft): Promise<BackendAllergy> {
  const row = await request<Raw>('/api/database/allergies', {
    method: 'POST',
    body: JSON.stringify({
      PatNum: patNum,
      defDescription: draft.description.trim(),
      ...(draft.reaction.trim() ? { Reaction: draft.reaction.trim() } : {}),
      ...(draft.dateAdverseReaction ? { DateAdverseReaction: draft.dateAdverseReaction } : {}),
    }),
  });
  return mapBackendAllergy(row);
}

export async function updateBackendAllergy(allergyNum: number, draft: AllergyDraft): Promise<BackendAllergy> {
  const row = await request<Raw>(`/api/database/allergies/${allergyNum}`, {
    method: 'PUT',
    body: JSON.stringify({
      Reaction: draft.reaction.trim(),
      DateAdverseReaction: draft.dateAdverseReaction || '0001-01-01',
      StatusIsActive: String(draft.isActive),
    }),
  });
  return mapBackendAllergy(row);
}

export function deleteBackendAllergy(allergyNum: number): Promise<void> {
  return request<void>(`/api/database/allergies/${allergyNum}`, { method: 'DELETE' });
}
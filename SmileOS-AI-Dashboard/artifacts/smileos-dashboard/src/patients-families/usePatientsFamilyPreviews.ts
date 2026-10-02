import { useReducer } from 'react';
import { previewReducer } from './store';

export function usePatientsFamilyPreviews() {
  const [records, dispatch] = useReducer(previewReducer, {});
  return { records, dispatch };
}
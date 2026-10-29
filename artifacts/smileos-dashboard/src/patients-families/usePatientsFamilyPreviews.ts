import { useReducer } from 'react';
import { previewReducer } from './store';
import { createDemoPreviewStore } from './demoRecords';

export function usePatientsFamilyPreviews() {
  const [records, dispatch] = useReducer(previewReducer, {}, createDemoPreviewStore);
  return { records, dispatch };
}
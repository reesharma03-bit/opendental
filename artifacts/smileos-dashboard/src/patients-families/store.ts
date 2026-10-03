import type { PreviewRecord, PreviewValues } from './types';

export type PreviewStore = Record<string, readonly PreviewRecord[]>;
export type PreviewAction =
  | { type: 'create'; resource: string; id: string; values: PreviewValues }
  | { type: 'update'; resource: string; id: string; values: PreviewValues }
  | { type: 'delete'; resource: string; id: string };

// Preview data intentionally lives only in React memory; never persist patient input.
export function previewReducer(state: PreviewStore, action: PreviewAction): PreviewStore {
  const rows = state[action.resource] ?? [];
  switch (action.type) {
    case 'create':
      return {
        ...state,
        [action.resource]: [...rows, { id: action.id, values: { ...action.values } }],
      };
    case 'update':
      return {
        ...state,
        [action.resource]: rows.map((row) => row.id === action.id
          ? { ...row, values: { ...action.values } } : row),
      };
    case 'delete':
      return {
        ...state,
        [action.resource]: rows.filter((row) => row.id !== action.id),
      };
  }
}
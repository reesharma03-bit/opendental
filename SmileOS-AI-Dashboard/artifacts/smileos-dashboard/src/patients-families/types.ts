export type PreviewValues = Record<string, string | boolean>;

export interface PreviewRecord {
  id: string;
  values: PreviewValues;
}

export interface PreviewField {
  key: string;
  label: string;
  type: 'text' | 'textarea' | 'number' | 'date' | 'select' | 'checkbox' | 'email' | 'tel';
  required?: boolean;
  options?: readonly string[];
  min?: number;
  max?: number;
  step?: string;
  help?: string;
}

export interface PreviewResourceDefinition {
  resource: string;
  title: string;
  singular: string;
  description: string;
  patientScoped: boolean;
  fields: readonly PreviewField[];
  columns: readonly string[];
}

export interface PatientsFamiliesPreviewProps {
  definition: PreviewResourceDefinition;
  rows: readonly PreviewRecord[];
  createRequest?: number;
  onCreate: (values: PreviewValues) => void;
  onUpdate: (id: string, values: PreviewValues) => void;
  onDelete: (id: string) => void;
}
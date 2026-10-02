import { patientFamilyPreviewDefinitions } from './definitions';
import type { PreviewStore } from './store';
import type { PreviewField, PreviewValues } from './types';

// Fictional, in-memory UI examples only. These never become API requests.
const numericExamples: Record<string, number> = {
  heightCm: 170,
  weightKg: 68,
  temperatureC: 36.7,
  systolicMmhg: 120,
  diastolicMmhg: 80,
  pulseBpm: 72,
  respiratoryRatePerMin: 16,
  oxygenSaturationPercent: 98,
};

function exampleValue(field: PreviewField, singular: string, index: number): string | boolean {
  if (field.key === 'patientId' || field.key === 'guarantorId') return String(900001 + index);
  if (field.key === 'memberIds') return '900001, 900002';
  if (field.key === 'firstName') return 'Demo';
  if (field.key === 'lastName') return `Patient ${index + 1}`;
  if (field.key.toLowerCase().includes('ssn')) return '';
  if (field.type === 'checkbox') return false;
  if (field.type === 'select') return field.options?.[0] ?? '';
  if (field.type === 'date') return '2026-01-15';
  if (field.type === 'email') return `demo${index + 1}@example.test`;
  if (field.type === 'tel') return `202555010${index + 1}`;
  if (field.type === 'number') {
    const value = numericExamples[field.key] ?? field.min ?? 1;
    return String(Math.min(field.max ?? value, Math.max(field.min ?? value, value)));
  }
  if (field.type === 'textarea') return `Fictional ${singular} example ${index + 1}. UI preview only.`;
  return `Demo ${field.label.toLowerCase()} ${index + 1}`;
}

export function createDemoPreviewStore(): PreviewStore {
  return Object.fromEntries(Object.values(patientFamilyPreviewDefinitions).map((definition) => [
    definition.resource,
    [0, 1].map((index) => {
      const values: PreviewValues = Object.fromEntries(definition.fields.map((field) => [
        field.key, exampleValue(field, definition.singular, index),
      ]));
      return { id: `demo-${definition.resource}-${index + 1}`, values };
    }),
  ]));
}
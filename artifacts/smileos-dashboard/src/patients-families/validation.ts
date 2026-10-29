import type { PreviewResourceDefinition, PreviewValues } from './types';

export function createEmptyPreviewValues(definition: PreviewResourceDefinition): PreviewValues {
  return Object.fromEntries(definition.fields.map((field) => [
    field.key, field.type === 'checkbox' ? false : '',
  ]));
}

export function validatePreviewValues(
  definition: PreviewResourceDefinition,
  values: PreviewValues,
): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const field of definition.fields) {
    const raw = values[field.key];
    const value = typeof raw === 'string' ? raw.trim() : '';
    if (field.type === 'checkbox') {
      if (typeof raw !== 'boolean') errors[field.key] = `${field.label} must be yes or no.`;
      continue;
    }
    if (!value) {
      if (field.required) errors[field.key] = `${field.label} is required.`;
      continue;
    }
    if (field.type === 'number') {
      const number = Number(value);
      if (!Number.isFinite(number)) errors[field.key] = `${field.label} must be a valid number.`;
      else if (field.min !== undefined && number < field.min) {
        errors[field.key] = `${field.label} must be at least ${field.min}.`;
      } else if (field.max !== undefined && number > field.max) {
        errors[field.key] = `${field.label} must be no more than ${field.max}.`;
      } else if ((field.step ?? '1') === '1' && !Number.isInteger(number)) {
        errors[field.key] = `${field.label} must be a whole number.`;
      }
    }
    if (field.type === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
      errors[field.key] = 'Enter a valid email address.';
    }
    if (field.type === 'date') {
      const parsed = new Date(`${value}T00:00:00Z`);
      if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(parsed.getTime())
        || parsed.toISOString().slice(0, 10) !== value) {
        errors[field.key] = 'Enter a valid date.';
      }
    }
    if (field.type === 'select' && !field.options?.includes(value)) {
      errors[field.key] = `Select a valid ${field.label.toLowerCase()}.`;
    }
  }
  return errors;
}
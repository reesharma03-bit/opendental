import assert from 'node:assert/strict';
import { test } from 'node:test';
import { apiNavigationGroups } from '../apiNavigation';
import { patientFamilyPreviewDefinitions } from './definitions';
import { previewReducer } from './store';
import { createEmptyPreviewValues, validatePreviewValues } from './validation';
import type { PreviewResourceDefinition, PreviewValues } from './types';

const excluded = new Set(['Allergies', 'AllergyDefs', 'DiseaseDefs']);
const expected = apiNavigationGroups[0].resources.filter((key) => !excluded.has(key));

test('covers exactly the 19 requested resources, excluding the existing three screens', () => {
  assert.equal(expected.length, 19);
  assert.deepEqual(Object.keys(patientFamilyPreviewDefinitions).sort(), [...expected].sort());
  for (const resource of expected) {
    const definition = patientFamilyPreviewDefinitions[resource];
    assert.equal(definition.resource, resource);
    assert.ok(definition.description && definition.title && definition.singular);
    assert.ok(definition.fields.length >= 3, resource);
    const keys = definition.fields.map((field) => field.key);
    assert.equal(new Set(keys).size, keys.length, resource);
    assert.ok(definition.columns.length >= 2, resource);
    assert.ok(definition.columns.every((key) => keys.includes(key)), resource);
  }
});

for (const resource of expected) {
  test(`${resource}: empty defaults and resource-specific valid drafts`, () => {
    const definition = patientFamilyPreviewDefinitions[resource];
    const draft = createEmptyPreviewValues(definition);
    assert.ok(Object.keys(validatePreviewValues(definition, draft)).length > 0, 'Required input is validated');
    for (const field of definition.fields) {
      switch (field.type) {
        case 'number': draft[field.key] = String(Math.max(field.min ?? 0, 1)); break;
        case 'date': draft[field.key] = '2026-01-01'; break;
        case 'email': draft[field.key] = 'preview@example.test'; break;
        case 'select': draft[field.key] = field.options?.[0] ?? ''; break;
        case 'checkbox': draft[field.key] = false; break;
        default: draft[field.key] = 'Fictional preview';
      }
    }
    assert.deepEqual(validatePreviewValues(definition, draft), {});
  });
}

test('preview records support create, edit and delete without affecting other resources', () => {
  const values: PreviewValues = { patientId: '1', note: 'Fictional draft' };
  const initial = previewReducer({}, { type: 'create', resource: 'PatientNotes', id: 'preview-1', values });
  const added = previewReducer(initial, {
    type: 'create', resource: 'Guardians', id: 'preview-2', values: { patientId: '2' },
  });
  const changed = previewReducer(added, {
    type: 'update', resource: 'PatientNotes', id: 'preview-1', values: { ...values, note: 'Edited preview' },
  });
  assert.equal(changed.PatientNotes[0].values.note, 'Edited preview');
  assert.equal(initial.PatientNotes[0].values.note, 'Fictional draft', 'Previous state remains unchanged');
  assert.deepEqual(changed.Guardians, added.Guardians);
  const removed = previewReducer(changed, { type: 'delete', resource: 'PatientNotes', id: 'preview-1' });
  assert.deepEqual(removed.PatientNotes, []);
  assert.equal(removed.Guardians.length, 1);
  assert.deepEqual(previewReducer({}, { type: 'delete', resource: 'Patients', id: 'missing' }), { Patients: [] });
});

test('validation rejects invalid IDs, dates, options and email', () => {
  const definition: PreviewResourceDefinition = {
    resource: 'Test', title: 'Test', singular: 'Test', description: 'Test', patientScoped: true,
    columns: ['id'],
    fields: [
      { key: 'id', label: 'Patient ID', type: 'number', required: true, min: 1 },
      { key: 'date', label: 'Date', type: 'date' },
      { key: 'email', label: 'Email', type: 'email' },
      { key: 'status', label: 'Status', type: 'select', options: ['Active'] },
    ],
  };
  assert.equal(Object.keys(validatePreviewValues(definition, {
    id: '-1', date: '2026-02-31', email: 'bad', status: 'Unsupported',
  })).length, 4);
  assert.ok(validatePreviewValues(definition, { id: '1.5' }).id);
  assert.ok(validatePreviewValues(definition, { id: 'Infinity' }).id);
  assert.deepEqual(validatePreviewValues(definition, { id: '1', date: '2024-02-29' }), {});
});
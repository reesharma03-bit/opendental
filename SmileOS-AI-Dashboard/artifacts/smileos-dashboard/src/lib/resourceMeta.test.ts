import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { apiNavigationGroups } from '../apiNavigation';
import { buildBody, buildUpdateBody, getField, missingRequired, resourceMap, resources, updatePath } from './resourceMeta';

test('covers exactly the 22 approved Patients & Families resources', () => {
  assert.deepEqual(resources.map((r) => r.name).sort(), [...apiNavigationGroups[0].resources].sort());
  assert.equal(resources.length, 22);
});
test('preserves response keys, including the documented trailing-space Description key', () => {
  assert.equal(getField({ 'Description ': 'Latex' }, 'Description'), 'Latex');
});
test('read-only resources and recalls expose no undocumented mutations', () => {
  for (const name of ['Pharmacies', 'PatientRaces', 'RecallTypes', 'RxPats', 'FamilyModules']) {
    const r = resourceMap[name];
    assert.ok(!r.create && !r.update && !r.del && !r.actions);
  }
  assert.ok(!resourceMap.Recalls.del && !resourceMap.Recalls.getSingle);
  assert.deepEqual(resourceMap.Recalls.actions!.map((a) => a.path), ['/api/recalls/Status', '/api/recalls/SwitchType']);
});
test('sends numeric zero and string booleans instead of omitting them', () => {
  assert.deepEqual(buildBody(resourceMap.Vitalsigns.create!, { PatNum: '3', Pulse: '0' }), { PatNum: 3, Pulse: 0 });
  assert.deepEqual(buildBody(resourceMap.MedicationPats.update!.fields, { ProvNum: '0' }), { ProvNum: 0 });
  assert.deepEqual(buildBody(resourceMap.Guardians.update!.fields, { IsGuardian: 'false' }), { IsGuardian: 'false' });
});
test('PatFields collection update includes patient, field identity and changed value only', () => {
  const row = { PatFieldNum: 10, PatNum: 3, FieldName: 'Verified', FieldValue: 'old' };
  const draft = { PatNum: '3', FieldName: 'Verified', FieldValue: 'new' };
  assert.equal(updatePath(resourceMap.PatFields, row), '/api/patfields');
  assert.deepEqual(buildUpdateBody(resourceMap.PatFields, draft, row), { FieldValue: 'new', PatNum: 3, FieldName: 'Verified' });
  assert.deepEqual(buildUpdateBody(resourceMap.PatFields, { ...draft, FieldValue: 'old' }, row), {});
  assert.deepEqual(buildUpdateBody(resourceMap.PatFields, { ...draft, FieldValue: '' }, row), { FieldValue: '', PatNum: 3, FieldName: 'Verified' });
  assert.deepEqual(missingRequired(resourceMap.PatFields.update!.fields, { ...draft, FieldValue: '' }), []);
});
test('partial updates do not overwrite untouched notes and can clear text and dates', () => {
  assert.deepEqual(buildBody(resourceMap.PatientNotes.update!.fields, { Medical: '' }, { Medical: 'old' }), { Medical: '' });
  assert.deepEqual(buildBody(resourceMap.MedicationPats.update!.fields, { DateStop: '' }, { DateStop: '2026-10-02' }), { DateStop: '0001-01-01' });
  assert.deepEqual(buildBody(resourceMap.Popups.update!.fields, { DateTimeDisabled: '' }, { DateTimeDisabled: '2026-10-02 09:00:00' }), { DateTimeDisabled: '0001-01-01 00:00:00' });
});
test('PickList requires values and normalizes browser line breaks to CRLF', () => {
  assert.ok(missingRequired(resourceMap.PatFieldDefs.create!, { FieldName: 'Color', FieldType: 'PickList' }).includes('Pick list items'));
  assert.equal(buildBody(resourceMap.PatFieldDefs.create!, { PickList: 'Red\nBlue' }).PickList, 'Red\r\nBlue');
  assert.deepEqual(missingRequired(resourceMap.PatFieldDefs.update!.fields, { FieldType: 'PickList', IsHidden: 'true' }, { FieldType: 'PickList' }), []);
});
test('every collection allowed Offset by the adapter has UI paging', () => {
  for (const name of ['Diseases', 'Guardians', 'MedicationPats', 'Medications', 'PatFieldDefs', 'PatFields', 'PatPlans', 'PatRestrictions', 'Pharmacies', 'Recalls', 'RecallTypes', 'RxPats', 'Vitalsigns']) {
    assert.ok(resourceMap[name].paged, name);
  }
});
test('patient search uses only standard GET filters and includes hospital fields', () => {
  assert.ok(!resourceMap.Patients.params.some((p) => p.name === 'PatStatus'));
  for (const field of ['Ward', 'AdmitDate']) assert.ok(resourceMap.Patients.create!.some((f) => f.name === field));
});

import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { databaseMeta, specFields, type DatabaseCapability } from './databaseResources';
import { buildBody, formValue } from './resourceMeta';

const labCases: DatabaseCapability = {
  resource: 'labcases', keyField: 'LabCaseNum', create: true, update: true, delete: true,
  createFields: [
    { name: 'PatNum', required: true, kind: 'patient' },
    { name: 'LaboratoryNum', required: true, kind: 'number' },
    { name: 'DateTimeDue', required: false, kind: 'datetime' },
  ],
  updateFields: [{ name: 'Instructions', required: false, kind: 'text' }],
};

test('Add and Edit come from Open Dental\'s documented fields even with nothing synced yet', () => {
  const meta = databaseMeta('LabCases', labCases, []);
  assert.deepEqual(meta.create?.map((f) => f.name), ['PatNum', 'LaboratoryNum', 'DateTimeDue']);
  assert.equal(meta.create?.[0].required, true);
  assert.deepEqual(meta.update?.fields.map((f) => f.name), ['Instructions']);
  assert.equal(meta.del, true);
});

test('a read-only resource gets no forms', () => {
  const meta = databaseMeta('Schedules', { resource: 'schedules', keyField: 'ScheduleNum', create: false, update: false, delete: false }, []);
  assert.equal(meta.create, undefined);
  assert.equal(meta.update, undefined);
  assert.equal(meta.del, false);
});

test('list fields are typed as "1, 2" and sent as numbers', () => {
  const [procNums] = specFields([{ name: 'procNums', required: true, kind: 'list' }]);
  assert.deepEqual(buildBody([procNums], { procNums: '12, 15 18' }), { procNums: [12, 15, 18] });
  assert.equal(formValue(procNums, [2, 4]), '2, 4');
  const [mode] = specFields([{ name: 'Mode_', required: false, kind: 'select', options: ['Phone', 'Email'] }]);
  assert.equal(mode.label, 'Mode');
  assert.deepEqual(mode.options, ['Phone', 'Email']);
});

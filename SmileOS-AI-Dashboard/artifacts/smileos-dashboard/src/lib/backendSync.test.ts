import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { groupSyncable, syncLabel } from './backendSync';

test('syncable resources are grouped like the menu, the rest under Other', () => {
  const groups = groupSyncable(['patients', 'allergies', 'claims', 'documents', 'subscriptions', 'appointments']);
  assert.deepEqual(groups.map((g) => g.label), ['Patients & Families', 'Scheduling', 'Insurance & Billing', 'Other']);
  assert.deepEqual(groups[0]!.resources.map((r) => r.name), ['allergies', 'patients']);
  assert.deepEqual(groups.at(-1)!.resources.map((r) => r.label), ['Documents (images)', 'Subscriptions (webhooks)']);
  assert.equal(syncLabel(groups, 'claims'), 'Claims');
});

test('menu entries that are not synced (computed views) are left out', () => {
  const groups = groupSyncable(['procedurelogs']);
  assert.equal(groups.length, 1);
  assert.deepEqual(groups[0]!.resources.map((r) => r.label), ['Procedure Logs']);
});

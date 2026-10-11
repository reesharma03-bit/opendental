import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { field, money, newestFirst } from './backendPatientRecord';

test('fields are read whatever casing Open Dental or our API uses', () => {
  assert.equal(field({ AptDateTime: '2026-10-08 09:30:00' }, 'AptDateTime'), '2026-10-08 09:30:00');
  assert.equal(field({ procCode: 'D1110' }, 'ProcCode'), 'D1110');
  assert.equal(field({ date_created: '2026-01-02' }, 'DateCreated'), '2026-01-02');
  assert.equal(field({ DateStop: '0001-01-01' }, 'DateStop'), '', 'Open Dental\'s "no date" shows as empty');
  assert.equal(field({}, 'Missing'), '');
});

test('records are listed newest first and amounts are formatted', () => {
  const rows = [{ ProcDate: '2025-01-01' }, { ProcDate: '2026-03-01' }, { ProcDate: '2024-12-31' }];
  assert.deepEqual(newestFirst(rows, 'ProcDate').map((r) => r.ProcDate), ['2026-03-01', '2025-01-01', '2024-12-31']);
  assert.equal(money(''), '');
  assert.match(money('235'), /235\.00/);
});

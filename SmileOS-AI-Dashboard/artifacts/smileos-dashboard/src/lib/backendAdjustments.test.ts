import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { draftProblem, mapAdjustment, toDraft, type AdjustmentDraft, type AdjustmentType } from './backendAdjustments';

const discount: AdjustmentType = { defNum: 1, name: 'Misc Neg Adjustment', sign: '-' };
const charge: AdjustmentType = { defNum: 2, name: 'Misc Pos Adjustment', sign: '+' };
const draft = (changes: Partial<AdjustmentDraft>): AdjustmentDraft => ({
  adjDate: '2026-10-08', adjAmt: '-25', adjType: '1', provNum: '', procNum: '', procDate: '', note: '', ...changes,
});

test('reads an adjustment as Open Dental returns it, keeping the type number and its name apart', () => {
  const a = mapAdjustment({
    AdjNum: 1, AdjDate: '2022-07-02', AdjAmt: -25.0, PatNum: 26, AdjType: 1, adjType: 'Misc Neg Adjustment',
    ProvNum: 1, AdjNote: 'Cash Discount', ProcDate: '2022-07-02', ProcNum: 0, DateEntry: '2022-07-02', ClinicNum: 0,
  });
  assert.equal(a.adjType, 1);
  assert.equal(a.adjTypeName, 'Misc Neg Adjustment');
  assert.equal(a.adjAmt, -25);
  assert.equal(toDraft(a).adjType, '1');
});

test('checks the same rules as Open Dental before saving', () => {
  assert.equal(draftProblem(draft({}), discount, '2026-10-08'), '');
  assert.match(draftProblem(draft({ adjType: '' }), undefined, '2026-10-08'), /Choose an adjustment type/);
  assert.match(draftProblem(draft({ adjDate: '2026-10-09' }), discount, '2026-10-08'), /future/);
  assert.match(draftProblem(draft({ adjAmt: '0' }), discount, '2026-10-08'), /zero/);
  assert.match(draftProblem(draft({ adjAmt: '25' }), discount, '2026-10-08'), /negative amount/);
  assert.match(draftProblem(draft({ adjType: '2', adjAmt: '-25' }), charge, '2026-10-08'), /positive amount/);
  assert.match(draftProblem(draft({ adjAmt: 'abc' }), discount, '2026-10-08'), /Enter an amount/);
});

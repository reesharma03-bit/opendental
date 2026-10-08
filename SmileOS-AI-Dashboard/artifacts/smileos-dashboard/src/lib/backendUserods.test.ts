import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { mapUserod, newUserProblem, toEditDraft, userChanges } from './backendUserods';

const lynda = mapUserod({
  UserNum: 1, UserName: 'Lynda', userGroupNums: [2], EmployeeNum: 0, employeeName: '', ClinicNum: 1, ProviderNum: 9,
  providerName: 'Lynda Larson, DMD', emailAddress: 'LyndaLarson@email.com', IsHidden: 'false', UserNumCEMT: 0, IsPasswordResetRequired: 'false',
});

test('reads a user as Open Dental lists it', () => {
  assert.deepEqual(lynda.groupNums, [2]);
  assert.equal(lynda.providerName, 'Lynda Larson, DMD');
  assert.equal(lynda.hidden, false);
  // The create answer has a single UserGroupNum instead of the list.
  assert.deepEqual(mapUserod({ UserNum: 7, UserName: 'Sally', UserGroupNum: 2 }).groupNums, [2]);
});

test('a new user needs a unique name, a group and Open Dental\'s strong password', () => {
  const ok = { userName: 'Sally', groupNum: '2', password: 'My1password', confirm: 'My1password', resetRequired: true };
  assert.equal(newUserProblem(ok, [lynda]), '');
  assert.match(newUserProblem({ ...ok, userName: 'lynda' }, [lynda]), /already taken/);
  assert.match(newUserProblem({ ...ok, userName: 'Sally ' }, [lynda]), /end with a space/);
  assert.match(newUserProblem({ ...ok, groupNum: '' }, [lynda]), /security group/);
  assert.match(newUserProblem({ ...ok, password: 'password1', confirm: 'password1' }, [lynda]), /upper-case/);
  assert.match(newUserProblem({ ...ok, confirm: 'Other1pass' }, [lynda]), /don’t match/);
});

test('an edit sends only what changed, in Open Dental\'s field names', () => {
  const draft = toEditDraft(lynda);
  assert.deepEqual(userChanges(lynda, draft), {});
  assert.deepEqual(userChanges(lynda, { ...draft, groupNums: [2, 4], hidden: true }), { userGroupNums: [2, 4], IsHidden: 'true' });
  assert.deepEqual(userChanges(lynda, { ...draft, providerNum: '' }), { ProviderNum: 0 });
});

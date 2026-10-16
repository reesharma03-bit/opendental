import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { eventLabel, webhookSummary, type Webhook } from './backendConnection';

const hook = (event: string, state: Webhook['state'], detail = ''): Webhook => ({ event, state, detail });

test('the webhooks headline counts active subscriptions', () => {
  assert.deepEqual(webhookSummary([hook('Patient', 'ok'), hook('Appointment', 'ok')]), { tone: 'ok', text: 'All 2 webhooks active' });
  assert.deepEqual(webhookSummary([hook('Patient', 'ok'), hook('Appointment', 'failing')]), { tone: 'warn', text: '1 of 2 webhooks active' });
  assert.deepEqual(webhookSummary([hook('Patient', 'off', 'Waiting for the public address')]), { tone: 'off', text: 'Waiting for the public address' });
  assert.deepEqual(webhookSummary([]), { tone: 'off', text: 'Not checked yet' });
});

test('event names read as words', () => {
  assert.equal(eventLabel('AppointmentDeleted'), 'Appointment deleted');
  assert.equal(eventLabel('PatientSelected'), 'Patient selected');
  assert.equal(eventLabel('Patient'), 'Patient');
});

// Open Dental webhook subscriptions, read from our database (synced from Open Dental).
// Changes are saved there first and sent to Open Dental by the backend.
import { pick, pickNum, request, type Raw } from './backend';

export type EventKind = 'Database' | 'UI';

export interface Subscription {
  /** SubscriptionNum; negative while Open Dental has not received a new subscription yet. */
  id: number;
  endpoint: string;
  workstation: string;
  eventKind: EventKind;
  watchTable: string;
  uiEventType: string;
  pollingSeconds: number;
  dateTimeStart: string;
  dateTimeStop: string;
  note: string;
  failureReason: string;
  subsequentFailures: number;
  lastFailure: string;
  nextRetry: string;
}

export type SubscriptionValues = Omit<Subscription, 'id' | 'failureReason' | 'subsequentFailures' | 'lastFailure' | 'nextRetry'>;

const PATH = '/api/database/subscriptions';

/** Open Dental's "no date" value. */
const dateOrEmpty = (value: string) => (!value || value.startsWith('0001-01-01') ? '' : value);

export function mapSubscription(raw: Raw): Subscription {
  const watchTable = pick(raw, 'WatchTable', 'watchTable');
  return {
    id: pickNum(raw, 'SubscriptionNum', 'subscriptionNum') ?? 0,
    endpoint: pick(raw, 'EndPointUrl', 'endPointUrl'),
    workstation: pick(raw, 'Workstation', 'workstation'),
    eventKind: watchTable ? 'Database' : 'UI',
    watchTable,
    uiEventType: pick(raw, 'UiEventType', 'uiEventType'),
    pollingSeconds: pickNum(raw, 'PollingSeconds', 'pollingSeconds') ?? 0,
    dateTimeStart: dateOrEmpty(pick(raw, 'DateTimeStart', 'dateTimeStart')).slice(0, 10),
    dateTimeStop: dateOrEmpty(pick(raw, 'DateTimeStop', 'dateTimeStop')).slice(0, 10),
    note: pick(raw, 'Note', 'note'),
    failureReason: pick(raw, 'FailureReason', 'failureReason'),
    subsequentFailures: pickNum(raw, 'SubsequentFailures', 'subsequentFailures') ?? 0,
    lastFailure: dateOrEmpty(pick(raw, 'DateTimeLastFailure', 'dateTimeLastFailure')),
    nextRetry: dateOrEmpty(pick(raw, 'DateTimeNextRetry', 'dateTimeNextRetry')),
  };
}

/** The request Open Dental expects for these values. */
function toBody(values: SubscriptionValues): Record<string, string | number> {
  const body: Record<string, string | number> = {
    EndPointUrl: values.endpoint.trim(),
    Workstation: values.workstation.trim(),
    Note: values.note,
  };
  if (values.eventKind === 'Database') {
    body.WatchTable = values.watchTable;
    body.PollingSeconds = values.pollingSeconds;
  } else {
    body.UiEventType = values.uiEventType || 'PatientSelected';
  }
  if (values.dateTimeStart) body.DateTimeStart = `${values.dateTimeStart} 00:00:00`;
  body.DateTimeStop = values.dateTimeStop ? `${values.dateTimeStop} 00:00:00` : '0001-01-01 00:00:00';
  return body;
}

export async function listSubscriptions(): Promise<Subscription[]> {
  return (await request<Raw[]>(`${PATH}?Limit=1000`)).map(mapSubscription);
}

export async function createSubscription(values: SubscriptionValues): Promise<Subscription> {
  return mapSubscription(await request<Raw>(PATH, { method: 'POST', body: JSON.stringify(toBody(values)) }));
}

/** Sends only the fields that changed. */
export async function updateSubscription(original: Subscription, values: SubscriptionValues): Promise<Subscription> {
  const before = toBody(original);
  const after = toBody(values);
  const changes = Object.fromEntries(Object.entries(after).filter(([key, value]) => before[key] !== value));
  if (Object.keys(changes).length === 0) return original;
  return mapSubscription(await request<Raw>(`${PATH}/${original.id}`, { method: 'PUT', body: JSON.stringify(changes) }));
}

export function deleteSubscription(id: number): Promise<void> {
  return request<void>(`${PATH}/${id}`, { method: 'DELETE' });
}

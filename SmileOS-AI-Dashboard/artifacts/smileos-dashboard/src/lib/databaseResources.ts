// Every API Catalog screen reads our database (/api/database/{resource}), which the
// Open Dental sync fills. Changes are saved there first and sent to Open Dental by the
// backend. A screen uses the hand-written field definitions in resourceMeta.ts when it
// has them; otherwise its columns and form are built from the fields Open Dental returned.
import { apiNavigationGroups, formatApiResourceName } from '../apiNavigation';
import { request } from './backend';
import {
  displayValue, qs, resourceMap, toRows,
  type ColumnMeta, type FieldKind, type FieldMeta, type ParamMeta, type ResourceMeta,
} from './resourceMeta';

export interface DatabaseCapability {
  resource: string;
  keyField: string;
  create: boolean;
  update: boolean;
  delete: boolean;
}

type Row = Record<string, unknown>;

export const PAGE_SIZE = 100;
const SAMPLE_SIZE = 50;

/** Fields Open Dental maintains itself; never offered in forms. */
const SYSTEM_FIELDS = /^(DateTStamp|SecDateTEdit|SecDateTEntry|SecDateEntry|SecUserNumEntry|DateTEntry|DateEntry|serverDateTime)$/i;

let capabilities: Promise<Map<string, DatabaseCapability>> | null = null;

/** What a user may see and change depends on who is signed in: forget it on sign-in / sign-out. */
export function resetCapabilities() {
  capabilities = null;
}

/** What each resource may change, from the backend (cached for the session). */
export function getCapabilities(): Promise<Map<string, DatabaseCapability>> {
  capabilities ??= request<DatabaseCapability[]>('/api/database')
    .then((rows) => new Map(rows.map((row) => [row.resource, row])))
    .catch((e: Error) => { capabilities = null; throw e; });
  return capabilities;
}

export const databasePath = (name: string) => `/api/database/${name.toLowerCase()}`;

export async function loadSample(name: string): Promise<Row[]> {
  return toRows(await request<unknown>(qs(databasePath(name), { Limit: String(SAMPLE_SIZE) })));
}

function groupOf(name: string): string {
  return apiNavigationGroups.find((g) => (g.resources as readonly string[]).includes(name))?.label ?? 'Open Dental';
}

function humanize(key: string) {
  return key.trim().replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/^./, (c) => c.toUpperCase());
}

function isScalar(v: unknown) {
  return v === null || ['string', 'number', 'boolean'].includes(typeof v);
}

/** Guesses a form control from the values Open Dental returned for a field. */
function kindOf(key: string, values: unknown[]): FieldKind {
  if (key === 'PatNum') return 'patient';
  const texts = values.map(displayValue).filter((v) => v !== '');
  if (!texts.length) return 'text';
  if (texts.every((v) => v === 'true' || v === 'false')) return 'bool';
  if (texts.every((v) => /^\d{4}-\d{2}-\d{2}$/.test(v))) return 'date';
  if (texts.every((v) => /^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(:\d{2})?$/.test(v))) return 'datetime';
  if (values.every((v) => v === null || v === '' || typeof v === 'number')) return 'number';
  if (texts.some((v) => v.length > 80 || v.includes('\n'))) return 'textarea';
  return 'text';
}

/** Fields seen in the synced records, in the order Open Dental sends them. */
export function fieldsFromRows(rows: Row[], keyField: string): FieldMeta[] {
  const order: string[] = [];
  const values = new Map<string, unknown[]>();
  rows.slice(0, SAMPLE_SIZE).forEach((row) => {
    Object.entries(row).forEach(([key, value]) => {
      if (!isScalar(value) || key === keyField || SYSTEM_FIELDS.test(key)) return;
      if (!values.has(key)) { values.set(key, []); order.push(key); }
      values.get(key)!.push(value);
    });
  });
  return order.map((key) => ({ name: key, label: humanize(key), kind: kindOf(key, values.get(key)!) }));
}

function columnsFromRows(rows: Row[], keyField: string): ColumnMeta[] {
  const filled = fieldsFromRows(rows, keyField)
    .filter((f) => f.kind !== 'textarea')
    .filter((f) => rows.some((row) => displayValue(row[f.name]) !== ''))
    .slice(0, 6);
  return [{ key: keyField, label: humanize(keyField) }, ...filled.map((f) => ({ key: f.name, label: f.label }))];
}

/** Lookups in resourceMeta point at Open Dental; read them from our database instead. */
function fromDatabase(fields: FieldMeta[]): FieldMeta[] {
  return fields.map((f) => (f.lookup
    ? { ...f, lookup: { ...f.lookup, path: qs(databasePath(f.lookup.resource), { Limit: '1000' }) } }
    : f));
}

const patientFilter: ParamMeta = {
  name: 'PatNum', label: 'Patient', kind: 'patient',
  help: 'Optional. Leave empty for all patients.',
};

/**
 * Screen definition for one resource, read from and saved to our database.
 * Open Dental-only routes (special list views and actions) are not offered here.
 */
export function databaseMeta(name: string, capability: DatabaseCapability, sample: Row[]): ResourceMeta {
  const base = resourceMap[name];
  const path = databasePath(name);
  const pk = capability.keyField;
  const autoFields = fieldsFromRows(sample, pk);
  const hasPatient = sample.some((row) => 'PatNum' in row) || Boolean(base?.params.some((p) => p.name === 'PatNum'));
  const createFields = base?.create ? fromDatabase(base.create) : autoFields;
  const updateFields = base?.update ? fromDatabase(base.update.fields) : autoFields;
  const writable = capability.create || capability.update || capability.delete;

  return {
    name,
    title: base?.title ?? formatApiResourceName(name),
    group: base?.group ?? groupOf(name),
    summary: base?.summary ?? `${formatApiResourceName(name)} copied from Open Dental into our database.`,
    basePath: path,
    pk,
    params: hasPatient && name !== 'Patients' ? [patientFilter] : [],
    listPath: (params) => qs(path, { ...params, Limit: String(PAGE_SIZE) }),
    paged: true,
    getSingle: false,
    keyedUpdates: true,
    create: capability.create && createFields.length ? createFields : undefined,
    update: capability.update && updateFields.length
      ? { style: base?.update?.style ?? 'id', fields: updateFields }
      : undefined,
    del: capability.delete,
    columns: base?.columns?.length ? base.columns : columnsFromRows(sample, pk),
    rowLabel: base?.rowLabel ?? ((row) => `${humanize(pk)} ${displayValue(row[pk])}`),
    warning: base?.warning,
    readOnlyNote: writable
      ? (capability.create && !createFields.length
        ? 'Adding becomes available once Force Sync has copied at least one record (the form is built from its fields).'
        : undefined)
      : 'Open Dental does not allow changes to this through its API, so it is shown read only.',
  };
}

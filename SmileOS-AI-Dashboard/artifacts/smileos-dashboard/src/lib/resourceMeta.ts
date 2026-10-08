// Pure, documentation-derived metadata for the Patients & Families resources.
// Source: Open Dental API docs. Local routes mirror official routes with /api prefix.

export type FieldKind = 'text' | 'textarea' | 'number' | 'date' | 'datetime' | 'select' | 'bool' | 'patient' | 'lookup' | 'list';

export interface Lookup { resource: string; path: string; valueKey: string; labelKeys: string[] }

export interface FieldMeta {
  name: string;
  label: string;
  kind: FieldKind;
  required?: boolean;
  allowEmpty?: boolean;
  options?: string[];
  help?: string;
  lookup?: Lookup;
  /** Only sent on create (not on update). */
  createOnly?: boolean;
  /** Only sent on update. */
  updateOnly?: boolean;
  /** Field is used for URL/identity only on update, shown read-only. */
  readOnlyOnUpdate?: boolean;
  placeholder?: string;
}

export interface ParamMeta {
  name: string;
  label: string;
  kind: 'text' | 'select' | 'patient' | 'date' | 'bool' | 'number';
  options?: string[];
  required?: boolean;
  help?: string;
}

export interface ColumnMeta { key: string; label: string }

export interface ActionMeta {
  id: string;
  label: string;
  method: 'PUT';
  path: string;
  description: string;
  fields: FieldMeta[];
  warning?: string;
}

export interface ResourceMeta {
  name: string;
  title: string;
  group: string;
  summary: string;
  basePath: string;
  pk: string;
  /** 'allergies' uses the dedicated AllergiesScreen. */
  screen?: 'allergies';
  listPath?: (params: Record<string, string>) => string;
  params: ParamMeta[];
  paged?: boolean;
  /** Updates always go to {basePath}/{pk}, even for collection-style updates (our database API). */
  keyedUpdates?: boolean;
  /** Response of GET is a single object keyed by patient (no list endpoint). */
  singleByPatient?: boolean;
  getSingle?: boolean;
  create?: FieldMeta[];
  update?: { fields: FieldMeta[]; style: 'id' | 'collection' };
  del?: boolean;
  columns: ColumnMeta[];
  rowLabel: (row: Record<string, unknown>) => string;
  warning?: string;
  readOnlyNote?: string;
  actions?: ActionMeta[];
  listMode?: { id: string; label: string; path: string; params: ParamMeta[]; columns: ColumnMeta[]; paged?: boolean }[];
}

const GROUP = 'Patients & Families';
const t = (name: string, label: string, o: Partial<FieldMeta> = {}): FieldMeta => ({ name, label, kind: 'text', ...o });
const ta = (name: string, label: string, o: Partial<FieldMeta> = {}): FieldMeta => ({ name, label, kind: 'textarea', ...o });
const n = (name: string, label: string, o: Partial<FieldMeta> = {}): FieldMeta => ({ name, label, kind: 'number', ...o });
const d = (name: string, label: string, o: Partial<FieldMeta> = {}): FieldMeta => ({ name, label, kind: 'date', ...o });
const dt = (name: string, label: string, o: Partial<FieldMeta> = {}): FieldMeta => ({ name, label, kind: 'datetime', ...o });
const s = (name: string, label: string, options: string[], o: Partial<FieldMeta> = {}): FieldMeta => ({ name, label, kind: 'select', options, ...o });
const b = (name: string, label: string, o: Partial<FieldMeta> = {}): FieldMeta => ({ name, label, kind: 'bool', ...o });
const pat = (name: string, label: string, o: Partial<FieldMeta> = {}): FieldMeta => ({ name, label, kind: 'patient', ...o });
const lk = (name: string, label: string, lookup: Lookup, o: Partial<FieldMeta> = {}): FieldMeta => ({ name, label, kind: 'lookup', lookup, ...o });
const col = (key: string, label: string): ColumnMeta => ({ key, label });
const pp = (name: string, label: string, o: Partial<ParamMeta> = {}): ParamMeta => ({ name, label, kind: 'patient', ...o });

const PAT_STATUS = ['Patient', 'NonPatient', 'Inactive', 'Archived', 'Deceased', 'Prospective'];
const CONTACT = ['None', 'DoNotCall', 'HmPhone', 'WkPhone', 'WirelessPh', 'Email', 'SeeNotes', 'Mail', 'TextMessage'];
const RELATIONSHIPS = ['Mother', 'Stepfather', 'Stepmother', 'Grandfather', 'Grandmother', 'Father', 'Brother', 'CareGiver', 'FosterChild', 'Guardian', 'Grandparent', 'Other', 'Parent', 'Stepchild', 'Self', 'Sibling', 'Sister', 'Spouse', 'Child', 'LifePartner', 'Friend', 'Grandchild', 'Sitter'];
const PLAN_REL = ['Self', 'Spouse', 'Child', 'Employee', 'HandicapDep', 'SignifOther', 'InjuredPlantiff', 'LifePartner', 'Dependent'];

const LK_ALLERGYDEF: Lookup = { resource: 'AllergyDefs', path: '/api/allergydefs', valueKey: 'AllergyDefNum', labelKeys: ['Description'] };
const LK_DISEASEDEF: Lookup = { resource: 'DiseaseDefs', path: '/api/diseasedefs', valueKey: 'DiseaseDefNum', labelKeys: ['DiseaseName'] };
const LK_MED: Lookup = { resource: 'Medications', path: '/api/medications', valueKey: 'MedicationNum', labelKeys: ['MedName'] };
const LK_RECALLTYPE: Lookup = { resource: 'RecallTypes', path: '/api/recalltypes', valueKey: 'RecallTypeNum', labelKeys: ['Description'] };
const LK_PATFIELDDEF: Lookup = { resource: 'PatFieldDefs', path: '/api/patfielddefs', valueKey: 'FieldName', labelKeys: ['FieldName', 'FieldType'] };

const PATIENT_FIELDS: FieldMeta[] = [
  t('LName', 'Last name', { required: true }), t('FName', 'First name', { required: true }),
  t('MiddleI', 'Middle initial or name'), t('Preferred', 'Preferred name'),
  s('PatStatus', 'Patient status', PAT_STATUS), s('Gender', 'Gender', ['Male', 'Female', 'Unknown']),
  s('Position', 'Marital status', ['Single', 'Married', 'Child', 'Widowed', 'Divorced']),
  d('Birthdate', 'Date of birth'),
  t('SSN', 'Social security number', { help: 'US: 9 digits, no dashes. Never shown in lists; only sent when you enter a value.' }),
  t('Address', 'Address'), t('Address2', 'Address line 2'), t('City', 'City'),
  t('State', 'State / province', { help: 'Two capital letters in the USA.' }),
  t('Zip', 'Postal code', { help: 'Format 12345, 12345-1234, 123456789 or A0A 0A0.' }),
  t('HmPhone', 'Home phone'), t('WkPhone', 'Work phone'), t('WirelessPhone', 'Mobile phone'), t('Email', 'Email'),
  s('PreferConfirmMethod', 'Preferred confirmation method', CONTACT),
  s('PreferContactMethod', 'Preferred contact method', CONTACT),
  s('PreferRecallMethod', 'Preferred recall method', CONTACT),
  s('TxtMsgOk', 'Text message permission', ['Unknown', 'Yes', 'No']),
  t('Language', 'Language code', { help: 'eng = English, spa = Spanish.' }),
  n('Guarantor', 'Guarantor PatNum', { help: 'Head of household. Use either Guarantor or SuperFamily in one request.' }),
  n('SuperFamily', 'Super family head PatNum', { help: 'Set 0 on update to remove the family from its super family.' }),
  n('PriProv', 'Primary provider (ProvNum)'), n('SecProv', 'Secondary provider (ProvNum)'),
  n('FeeSched', 'Fee schedule (FeeSchedNum)'), t('BillingType', 'Billing type', { help: 'Must match a Billing Type definition name.' }),
  t('ChartNumber', 'Chart number', { help: 'Maximum 15 characters.' }), t('MedicaidID', 'Medicaid ID'),
  n('EmployerNum', 'Employer (EmployerNum)'), d('DateFirstVisit', 'Date of first visit'),
  n('ClinicNum', 'Clinic (ClinicNum)', { help: 'Zero when not attached to a clinic.' }),
  b('Premed', 'Premedicate for appointments'),
  ta('FamFinUrgNote', 'Family financial urgent note', { help: 'Only allowed when this patient is the guarantor.' }),
  ta('MedUrgNote', 'Urgent medical note'), ta('ApptModNote', 'Appointment module note'),
  t('Ward', 'Hospital ward', { help: 'Requires Hospitals enabled and Open Dental 24.1.12 or later.' }),
  d('AdmitDate', 'Hospital admission date', { help: 'Requires Hospitals enabled and Open Dental 24.1.12 or later.' }),
];

const low = (name: string) => name.toLowerCase();
export const qs = (path: string, params: Record<string, string>) => {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v.trim() !== '') q.set(k, v.trim()); });
  const str = q.toString();
  return str ? `${path}?${str}` : path;
};
/** Read a field from a returned Map tolerant of trailing spaces, case and underscores. */
export const normKey = (k: string) => k.replace(/[\s_]/g, '').toLowerCase();
export function getField(row: Record<string, unknown>, key: string): unknown {
  if (key in row) return row[key];
  const target = normKey(key);
  for (const k of Object.keys(row)) if (normKey(k) === target) return row[k];
  return undefined;
}
export function displayValue(v: unknown): string {
  if (v === undefined || v === null || v === '') return '';
  const str = String(v);
  if (str.startsWith('0001-01-01')) return '';
  return str;
}

const simpleLabel = (...keys: string[]) => (row: Record<string, unknown>) =>
  keys.map((k) => displayValue(getField(row, k))).filter(Boolean).join(' ') || 'this record';

const patParam = (required = false): ParamMeta => pp('PatNum', 'Patient', { required, help: required ? 'Required by Open Dental for this query.' : 'Optional. Leave empty for all patients.' });

const PAGED_COLLECTIONS = new Set([
  'AllergyDefs', 'DiseaseDefs', 'Diseases', 'Guardians', 'MedicationPats', 'Medications',
  'Patients', 'PatFieldDefs', 'PatFields', 'PatPlans', 'PatRestrictions', 'Pharmacies',
  'Recalls', 'RecallTypes', 'RxPats', 'Vitalsigns',
]);
function defineResources(items: ResourceMeta[]): ResourceMeta[] {
  return items.map((item) => ({ ...item, paged: item.paged ?? PAGED_COLLECTIONS.has(item.name) }));
}

export const resources = defineResources([
  {
    name: 'Allergies', title: 'Allergies', group: GROUP, summary: 'Patient allergy records.', basePath: '/api/allergies', pk: 'AllergyNum',
    screen: 'allergies', params: [], columns: [], rowLabel: simpleLabel('defDescription'),
  },
  {
    name: 'AllergyDefs', title: 'Allergy definitions', group: GROUP,
    summary: 'The practice-wide list of allergens that can be attached to patients.', basePath: '/api/allergydefs', pk: 'AllergyDefNum',
    listPath: (p) => qs('/api/allergydefs', p), params: [], paged: true, getSingle: true,
    create: [t('Description', 'Allergen name', { required: true, placeholder: 'e.g. Tylenol' })],
    update: { style: 'id', fields: [t('Description', 'Allergen name'), b('IsHidden', 'Hidden from selection lists')] },
    columns: [col('AllergyDefNum', 'Def #'), col('Description', 'Allergen'), col('IsHidden', 'Hidden'), col('SnomedType', 'SNOMED type'), col('MedicationNum', 'Linked medication #'), col('UniiCode', 'UNII code'), col('DateTStamp', 'Last changed')],
    rowLabel: simpleLabel('Description'),
    warning: 'Definitions are shared by every patient. Renaming or hiding one changes how existing allergy records read. Open Dental offers no delete; hide instead.',
  },
  {
    name: 'DiseaseDefs', title: 'Problem definitions', group: GROUP,
    summary: 'The practice-wide list of problems (diseases) that can be assigned to patients.', basePath: '/api/diseasedefs', pk: 'DiseaseDefNum',
    listPath: (p) => qs('/api/diseasedefs', p), params: [], paged: true, getSingle: true,
    create: [t('DiseaseName', 'Problem name', { required: true })],
    columns: [col('DiseaseDefNum', 'Def #'), col('DiseaseName', 'Problem'), col('IsHidden', 'Hidden'), col('ICD9Code', 'ICD-9'), col('ICD10Code', 'ICD-10'), col('SnomedCode', 'SNOMED'), col('DateTStamp', 'Last changed')],
    rowLabel: simpleLabel('DiseaseName'),
    readOnlyNote: 'Open Dental supports list, view and create for problem definitions. Editing and deleting are not offered by the API.',
  },
  {
    name: 'Diseases', title: 'Patient problems', group: GROUP,
    summary: 'Problems attached to a patient. Attaching by name creates the definition automatically.', basePath: '/api/diseases', pk: 'DiseaseNum',
    listPath: (p) => qs('/api/diseases', p), params: [patParam()], getSingle: true,
    create: [
      pat('PatNum', 'Patient', { required: true }),
      t('diseaseDefName', 'Problem name', { required: true, help: 'Creates the problem definition if it does not exist yet.' }),
      lk('DiseaseDefNum', 'Existing problem definition (rare)', LK_DISEASEDEF, { help: 'Optional. Normally use the problem name instead.' }),
      s('ProbStatus', 'Status', ['Active', 'Resolved', 'Inactive']), d('DateStart', 'Start date'), d('DateStop', 'Stop date'), ta('PatNote', 'Note'),
    ],
    update: { style: 'id', fields: [s('ProbStatus', 'Status', ['Active', 'Resolved', 'Inactive']), d('DateStart', 'Start date'), d('DateStop', 'Stop date'), ta('PatNote', 'Note', { help: 'Overwrites the existing note.' })] },
    del: true,
    columns: [col('DiseaseNum', 'Problem #'), col('PatNum', 'Patient #'), col('diseaseDefName', 'Problem'), col('ProbStatus', 'Status'), col('DateStart', 'Start'), col('DateStop', 'Stop'), col('PatNote', 'Note')],
    rowLabel: simpleLabel('diseaseDefName'),
    warning: 'Problem list entries are part of the patient medical record. Deleting is permanent; mark Inactive or Resolved to keep history.',
  },
  {
    name: 'EhrPatients', title: 'EHR patient data', group: GROUP,
    summary: 'Electronic health record demographics for one patient.', basePath: '/api/ehrpatients', pk: 'PatNum',
    singleByPatient: true, params: [pp('PatNum', 'Patient', { required: true })],
    update: { style: 'id', fields: [d('DischargeDate', 'Discharge date'), t('MedicaidState', 'Medicaid state', { help: 'State abbreviation for the patient Medicaid ID (version 24.4.13+).' })] },
    columns: [col('PatNum', 'Patient #'), col('MotherMaidenFname', 'Mother maiden first'), col('MotherMaidenLname', 'Mother maiden last'), col('VacShareOk', 'Vaccine sharing OK'), col('MedicaidState', 'Medicaid state'), col('SexualOrientation', 'Sexual orientation code'), col('GenderIdentity', 'Gender identity code'), col('DischargeDate', 'Discharge date')],
    rowLabel: simpleLabel('PatNum'),
    warning: 'Only discharge date and Medicaid state are editable through the API; other EHR fields are read-only.',
  },
  {
    name: 'FamilyModules', title: 'Family module: insurance', group: GROUP,
    summary: 'Insurance plans for a patient as shown in the Family module.', basePath: '/api/familymodules', pk: 'InsSubNum',
    listPath: (p) => `/api/familymodules/${encodeURIComponent((p.PatNum || '').trim())}/Insurance`,
    params: [pp('PatNum', 'Patient', { required: true })],
    columns: [col('ordinal', 'Order'), col('CarrierName', 'Carrier'), col('subscriber', 'Subscriber'), col('SubscriberID', 'Subscriber ID'), col('Relationship', 'Relationship'), col('GroupName', 'Group name'), col('GroupNum', 'Group #'), col('planType', 'Plan type'), col('feeschedule', 'Fee schedule'), col('employer', 'Employer'), col('IsPending', 'Pending'), col('IsMedical', 'Medical')],
    rowLabel: simpleLabel('CarrierName'),
    readOnlyNote: 'Read-only. To change coverage use Patient plans (PatPlans).',
  },
  {
    name: 'Guardians', title: 'Guardians', group: GROUP,
    summary: 'Guardian and relationship links between patients.', basePath: '/api/guardians', pk: 'GuardianNum',
    listPath: (p) => qs('/api/guardians', p), getSingle: true,
    params: [pp('PatNumChild', 'Child patient'), pp('PatNumGuardian', 'Guardian patient')],
    create: [
      pat('PatNumChild', 'Child patient', { required: true, help: 'The patient being cared for.' }),
      pat('PatNumGuardian', 'Guardian patient', { required: true }),
      s('Relationship', 'Relationship of the guardian to the child', RELATIONSHIPS, { required: true }),
      b('IsGuardian', 'Is a guardian with patient portal access'),
    ],
    update: { style: 'id', fields: [s('Relationship', 'Relationship', RELATIONSHIPS), b('IsGuardian', 'Is a guardian with patient portal access')] },
    del: true,
    columns: [col('GuardianNum', 'Guardian #'), col('PatNumChild', 'Child #'), col('PatNumGuardian', 'Guardian patient #'), col('Relationship', 'Relationship'), col('IsGuardian', 'Portal access')],
    rowLabel: simpleLabel('GuardianNum'),
    warning: 'Setting "Is a guardian" to true grants the guardian patient portal access to the child\'s protected health information.',
  },
  {
    name: 'MedicationPats', title: 'Patient medications', group: GROUP,
    summary: 'Medications assigned to patients.', basePath: '/api/medicationpats', pk: 'MedicationPatNum',
    listPath: (p) => qs('/api/medicationpats', p), getSingle: true,
    params: [patParam(), { name: 'includeDiscontinued', label: 'Include discontinued', kind: 'bool', help: 'Default hides medications whose stop date has passed.' }],
    create: [
      pat('PatNum', 'Patient', { required: true }),
      lk('MedicationNum', 'Medication', LK_MED, { required: true }),
      ta('PatNote', 'Patient note'), d('DateStart', 'Start date'), d('DateStop', 'Stop date'),
      n('ProvNum', 'Prescribing provider #', { help: 'Enter an existing provider number; zero means no provider.' }),
    ],
    update: { style: 'id', fields: [ta('PatNote', 'Patient note', { help: 'Overwrites the existing note.' }), d('DateStart', 'Start date'), d('DateStop', 'Stop date'), n('ProvNum', 'Prescribing provider #', { help: 'Zero clears the provider.' })] },
    del: true,
    columns: [col('MedicationPatNum', 'Record #'), col('PatNum', 'Patient #'), col('medName', 'Medication'), col('PatNote', 'Note'), col('DateStart', 'Start'), col('DateStop', 'Stop'), col('ProvNum', 'Provider #')],
    rowLabel: simpleLabel('medName'),
    warning: 'Medication history is clinical data. Prefer setting a stop date over deleting a record.',
  },
  {
    name: 'Medications', title: 'Medication catalog', group: GROUP,
    summary: 'The practice medication list.', basePath: '/api/medications', pk: 'MedicationNum',
    listPath: (p) => qs('/api/medications', p), params: [], getSingle: true,
    create: [
      t('MedName', 'Medication name', { required: true }),
      n('GenericNum', 'Generic medication #', { help: 'Link to the generic. Leave empty if this medication is itself generic.' }),
      t('genericName', 'Generic name', { help: 'Alternative to Generic #. Defaults to the medication name.' }),
      ta('Notes', 'Notes', { help: 'Only allowed when the medication is generic.' }),
    ],
    update: { style: 'id', fields: [ta('Notes', 'Notes', { help: 'Only allowed when the medication is generic.' }), b('IsHidden', 'Hidden from selection lists')] },
    del: true,
    columns: [col('MedicationNum', 'Med #'), col('MedName', 'Medication'), col('genericName', 'Generic name'), col('GenericNum', 'Generic #'), col('Notes', 'Notes'), col('IsHidden', 'Hidden'), col('DateTStamp', 'Last changed')],
    rowLabel: simpleLabel('MedName'),
    warning: 'Medications in use by patients may not be deletable. Hide a medication to retire it.',
  },
  {
    name: 'PatientNotes', title: 'Patient notes', group: GROUP,
    summary: 'Medical, service and treatment notes. The key is the patient number.', basePath: '/api/patientnotes', pk: 'PatNum',
    listPath: (p) => qs('/api/patientnotes', p), params: [], getSingle: true,
    update: {
      style: 'id', fields: [
        ta('Medical', 'Medical history note'), ta('MedicalComp', 'Medical history, complete'), ta('Service', 'Service note'),
        ta('Treatment', 'Treatment (odontogram) note'), ta('FamFinancial', 'Family financial note', { help: 'One note per family, stored on the guarantor and shared by every family member.' }),
        t('ICEName', 'Emergency contact name'), t('ICEPhone', 'Emergency contact phone'),
      ],
    },
    columns: [col('PatNum', 'Patient #'), col('Medical', 'Medical'), col('Service', 'Service'), col('Treatment', 'Treatment'), col('FamFinancial', 'Family financial'), col('ICEName', 'Emergency contact'), col('ICEPhone', 'Emergency phone'), col('SecDateTEdit', 'Edited')],
    rowLabel: simpleLabel('PatNum'),
    warning: 'Any note you submit replaces the existing text. Family financial notes are shared across the whole family.',
  },
  {
    name: 'PatientRaces', title: 'Patient race and ethnicity', group: GROUP,
    summary: 'CDC race and ethnicity codes recorded for a patient.', basePath: '/api/patientraces', pk: 'PatientRaceNum',
    listPath: (p) => qs('/api/patientraces', p), params: [pp('PatNum', 'Patient', { required: true })],
    columns: [col('PatientRaceNum', 'Record #'), col('PatNum', 'Patient #'), col('CdcrecCode', 'CDC code'), col('descripition', 'Description'), col('isEthnicity', 'Is ethnicity'), col('heirarchicalCode', 'Hierarchical code')],
    rowLabel: simpleLabel('descripition'), readOnlyNote: 'Read-only in the Open Dental API.',
  },
  {
    name: 'Patients', title: 'Patients', group: GROUP, summary: 'Patient demographics, contact details and family links.',
    basePath: '/api/patients', pk: 'PatNum', listPath: (p) => qs('/api/patients', p), paged: true, getSingle: true,
    params: [
      { name: 'LName', label: 'Last name contains', kind: 'text' }, { name: 'FName', label: 'First name contains', kind: 'text' },
      { name: 'Birthdate', label: 'Date of birth', kind: 'date' },
      { name: 'hideInactive', label: 'Hide inactive', kind: 'bool' },
      { name: 'Phone', label: 'Phone', kind: 'text' },
      { name: 'Address', label: 'Address', kind: 'text' },
      { name: 'City', label: 'City', kind: 'text' },
      { name: 'State', label: 'State / province', kind: 'text' },
      { name: 'SSN', label: 'Social security number', kind: 'text' },
      { name: 'ChartNumber', label: 'Chart number', kind: 'text' },
      { name: 'guarOnly', label: 'Guarantors only', kind: 'bool' },
      { name: 'showArchived', label: 'Include archived', kind: 'bool' },
      { name: 'SiteNum', label: 'Site #', kind: 'number' },
      { name: 'SubscriberId', label: 'Insurance subscriber ID', kind: 'text' },
      { name: 'Email', label: 'Email', kind: 'text' },
      { name: 'Country', label: 'Country', kind: 'text' },
      { name: 'clinicNums', label: 'Clinic numbers', kind: 'text', help: 'Comma-separated clinic numbers.' },
      { name: 'clinicAbbr', label: 'Clinic abbreviation', kind: 'text' },
      { name: 'invoiceNumber', label: 'Invoice number', kind: 'text' },
    ],
    create: PATIENT_FIELDS,
    update: { style: 'id', fields: PATIENT_FIELDS },
    columns: [col('PatNum', 'Patient #'), col('LName', 'Last name'), col('FName', 'First name'), col('Birthdate', 'Birth date'), col('PatStatus', 'Status'), col('WirelessPhone', 'Mobile'), col('Email', 'Email'), col('City', 'City'), col('Guarantor', 'Guarantor #')],
    rowLabel: simpleLabel('FName', 'LName'),
    warning: 'Edits write directly to the patient chart. Only fields you change are sent.',
  },
  {
    name: 'PatFieldDefs', title: 'Patient field definitions', group: GROUP,
    summary: 'Custom fields staff can fill in on any patient.', basePath: '/api/patfielddefs', pk: 'PatFieldDefNum',
    listPath: (p) => qs('/api/patfielddefs', p), params: [],
    create: [
      t('FieldName', 'Field name', { required: true, help: 'Must be unique.' }),
      s('FieldType', 'Field type', ['Text', 'PickList', 'Date', 'Checkbox', 'Currency', 'CareCreditStatus', 'CareCreditPreApprovalAmt', 'CareCreditAvailableCredit'], { required: true }),
      ta('PickList', 'Pick list items', { help: 'One item per line. Required only when the type is PickList.' }),
      b('IsHidden', 'Hidden'),
    ],
    update: { style: 'id', fields: [
      t('FieldName', 'Field name', { help: 'Cannot change for CareCredit fields in use. Must be unique.' }),
      s('FieldType', 'Field type', ['Text', 'PickList', 'Date', 'Checkbox', 'Currency', 'CareCreditStatus', 'CareCreditPreApprovalAmt', 'CareCreditAvailableCredit']),
      ta('PickList', 'Pick list items', { help: 'Only when changing a field to PickList from another type. Existing lists cannot be edited here.' }),
      b('IsHidden', 'Hidden'),
    ] },
    del: true,
    columns: [col('PatFieldDefNum', 'Def #'), col('FieldName', 'Field name'), col('FieldType', 'Type'), col('PickList', 'Pick list'), col('IsHidden', 'Hidden')],
    rowLabel: simpleLabel('FieldName'),
    warning: 'Deleting a definition can remove the field from patients that use it.',
  },
  {
    name: 'PatFields', title: 'Patient field values', group: GROUP,
    summary: 'Values of custom fields for each patient.', basePath: '/api/patfields', pk: 'PatFieldNum',
    listPath: (p) => qs('/api/patfields', p), getSingle: true,
    params: [patParam(), { name: 'FieldName', label: 'Field name (case sensitive)', kind: 'text' }, { name: 'SecDateTEdit', label: 'Edited since (yyyy-MM-dd HH:mm:ss)', kind: 'text' }],
    create: [
      pat('PatNum', 'Patient', { required: true }),
      lk('FieldName', 'Field', LK_PATFIELDDEF, { required: true, help: 'Field names are case sensitive.' }),
      t('FieldValue', 'Value', { required: true, help: 'Text, pick-list item, yyyy-MM-dd date, "1" for checked checkbox, or a currency amount depending on the field type.' }),
    ],
    update: { style: 'collection', fields: [
      pat('PatNum', 'Patient', { required: true, readOnlyOnUpdate: true }),
      lk('FieldName', 'Field', LK_PATFIELDDEF, { required: true, readOnlyOnUpdate: true }),
      t('FieldValue', 'Value', { required: true, allowEmpty: true, help: 'Clear this value by leaving it blank.' }),
    ] },
    del: true,
    columns: [col('PatFieldNum', 'Value #'), col('PatNum', 'Patient #'), col('FieldName', 'Field'), col('FieldValue', 'Value'), col('SecDateTEdit', 'Edited')],
    rowLabel: simpleLabel('FieldName'),
    warning: 'Updating sends PatNum, FieldName and FieldValue to the collection endpoint, not a record id.',
  },
  {
    name: 'PatPlans', title: 'Patient insurance plans', group: GROUP,
    summary: 'Links a patient to an insurance subscriber, with coverage order.', basePath: '/api/patplans', pk: 'PatPlanNum',
    listPath: (p) => qs('/api/patplans', p),
    params: [patParam(), { name: 'InsSubNum', label: 'Insurance subscriber #', kind: 'number' }],
    create: [
      pat('PatNum', 'Patient', { required: true }),
      n('InsSubNum', 'Insurance subscriber # (InsSubNum)', { required: true, help: 'An existing subscriber. Find it in Family module: insurance.' }),
      n('Ordinal', 'Coverage order', { help: '1 primary, 2 secondary. Setting 1 bumps existing primary to 2.' }),
      s('Relationship', 'Relationship to subscriber', PLAN_REL), t('PatID', 'Patient ID override', { help: 'Overrides subscriber ID on e-claims.' }),
    ],
    update: { style: 'id', fields: [
      n('InsSubNum', 'Insurance subscriber # (change)'), n('Ordinal', 'Coverage order'),
      s('Relationship', 'Relationship to subscriber', PLAN_REL), t('PatID', 'Patient ID override'),
    ] },
    del: true,
    columns: [col('PatPlanNum', 'Plan link #'), col('PatNum', 'Patient #'), col('InsSubNum', 'Subscriber #'), col('Ordinal', 'Order'), col('Relationship', 'Relationship'), col('PatID', 'Patient ID'), col('IsPending', 'Pending')],
    rowLabel: simpleLabel('PatPlanNum'),
    warning: 'Removing a patient plan detaches coverage from the patient and affects claims.',
  },
  {
    name: 'PatRestrictions', title: 'Patient restrictions', group: GROUP,
    summary: 'Restrictions such as the appointment scheduling block.', basePath: '/api/patrestrictions', pk: 'PatRestrictionNum',
    listPath: (p) => qs('/api/patrestrictions', p), getSingle: true, params: [patParam()],
    create: [pat('PatNum', 'Patient', { required: true }), s('PatRestrictType', 'Restriction', ['ApptSchedule'], { required: true, help: 'Blocks scheduling of appointments for this patient.' })],
    del: true,
    columns: [col('PatRestrictionNum', 'Restriction #'), col('PatNum', 'Patient #'), col('PatRestrictType', 'Type')],
    rowLabel: simpleLabel('PatRestrictType'),
    warning: 'Restrictions cannot be edited. Removing one permanently lifts the restriction.',
  },
  {
    name: 'Pharmacies', title: 'Pharmacies', group: GROUP, summary: 'Pharmacies configured for prescriptions.', basePath: '/api/pharmacies', pk: 'PharmacyNum',
    listPath: (p) => qs('/api/pharmacies', p), params: [], getSingle: true,
    columns: [col('PharmacyNum', 'Pharmacy #'), col('StoreName', 'Store'), col('Phone', 'Phone'), col('Fax', 'Fax'), col('Address', 'Address'), col('City', 'City'), col('State', 'State'), col('Zip', 'Zip'), col('Note', 'Note')],
    rowLabel: simpleLabel('StoreName'), readOnlyNote: 'Read-only in the Open Dental API.',
  },
  {
    name: 'Popups', title: 'Popups', group: GROUP,
    summary: 'Alerts shown when staff open a patient, family or super family.', basePath: '/api/popups', pk: 'PopupNum',
    listPath: (p) => qs('/api/popups', p), params: [pp('PatNum', 'Patient', { required: true })],
    create: [
      pat('PatNum', 'Patient', { required: true }), ta('Description', 'Popup text', { required: true, help: 'Popups interrupt staff. Keep them for important notes.' }),
      s('PopupLevel', 'Shown for', ['Patient', 'Family', 'SuperFamily']),
      dt('DateTimeDisabled', 'Disable after', { help: 'Leave empty to never disable.' }),
    ],
    update: { style: 'id', fields: [
      ta('Description', 'Popup text', { help: 'Changing the text archives the old popup and creates a copy.' }), s('PopupLevel', 'Shown for', ['Patient', 'Family', 'SuperFamily']),
      dt('DateTimeDisabled', 'Disable after', { help: 'Use 0001-01-01 00:00:00 to never disable.' }),
    ] },
    columns: [col('PopupNum', 'Popup #'), col('PatNum', 'Patient #'), col('Description', 'Text'), col('PopupLevel', 'Level'), col('DateTimeDisabled', 'Disabled after'), col('IsArchived', 'Archived'), col('DateTimeEntry', 'Created')],
    rowLabel: simpleLabel('Description'),
    readOnlyNote: 'Popups can be created and edited, not deleted. Disable a popup instead.',
  },
  {
    name: 'Recalls', title: 'Recalls', group: GROUP,
    summary: 'Recall due dates, the due list, status communication and recall type switching.', basePath: '/api/recalls', pk: 'RecallNum',
    listPath: (p) => qs('/api/recalls', p), params: [patParam()],
    create: [
      pat('PatNum', 'Patient', { required: true }),
      lk('RecallTypeNum', 'Recall type', LK_RECALLTYPE, { required: true, help: 'A patient may have only one recall per type.' }),
      d('DateDue', 'Due date'), t('RecallInterval', 'Interval', { help: 'Digit plus y, m, w or d, for example 1y6m20d. Defaults to the type interval.' }),
      n('RecallStatus', 'Status definition # (DefNum)', { help: 'A Definition in category 13. 0 means None.' }),
      ta('Note', 'Administrative note'), b('IsDisabled', 'Disabled'), n('DisableUntilBalance', 'Disable until family balance below'),
      d('DisableUntilDate', 'Disabled until'), s('Priority', 'Priority', ['Normal', 'ASAP']),
      t('TimePatternOverride', 'Time pattern override', { help: "Only 'X' and '/' characters, 5 minute increments." }),
    ],
    update: { style: 'id', fields: [
      d('DateDue', 'Due date'), t('RecallInterval', 'Interval', { help: 'For example 1y6m20d.' }),
      n('RecallStatus', 'Status definition # (DefNum)', { help: 'Category 13 definition. 0 clears to None.' }),
      ta('Note', 'Administrative note', { help: 'Overwrites the existing note.' }), b('IsDisabled', 'Disabled'), n('DisableUntilBalance', 'Disable until family balance below'),
      d('DisableUntilDate', 'Disabled until'), s('Priority', 'Priority', ['Normal', 'ASAP']), t('TimePatternOverride', 'Time pattern override'),
    ] },
    columns: [col('RecallNum', 'Recall #'), col('PatNum', 'Patient #'), col('RecallTypeNum', 'Type #'), col('DateDue', 'Due'), col('DatePrevious', 'Previous'), col('DateScheduled', 'Scheduled'), col('RecallInterval', 'Interval'), col('recallStatus', 'Status'), col('Priority', 'Priority'), col('IsDisabled', 'Disabled'), col('Note', 'Note')],
    listMode: [{
      id: 'list', label: 'Due list', path: '/api/recalls/List', paged: true,
      params: [
        d2('DateStart', 'Due from'), d2('DateEnd', 'Due until'), { name: 'ProvNum', label: 'Provider #', kind: 'number' },
        { name: 'ClinicNum', label: 'Clinic # (0 = unassigned)', kind: 'number' }, { name: 'RecallType', label: 'Recall type', kind: 'text', help: 'Typically Prophy or Perio.' },
        { name: 'IncludeReminded', label: 'Include already reminded', kind: 'bool' },
      ],
      columns: [col('DueDate', 'Due'), col('PatNum', 'Patient #'), col('Patient', 'Patient'), col('Age', 'Age'), col('Type', 'Type'), col('Interval', 'Interval'), col('NumRemind', 'Reminders'), col('LastRemind', 'Last reminder'), col('Contact', 'Contact method'), col('Status', 'Status'), col('ClinicNum', 'Clinic #'), col('Note', 'Note')],
    }],
    actions: [
      {
        id: 'status', label: 'Set recall status', method: 'PUT', path: '/api/recalls/Status',
        description: 'Updates the recall communication status for a patient and can log a commlog entry.',
        fields: [
          pat('PatNum', 'Patient', { required: true }), t('recallType', 'Recall type', { required: true, help: 'Typically Prophy or Perio.' }),
          n('RecallStatus', 'Status definition # (DefNum)', { help: 'Category 13 definition. 0 sets None.' }),
          s('commlogMode', 'Commlog mode', ['None', 'Email', 'Mail', 'Phone', 'InPerson', 'Text', 'EmailAndText', 'PhoneAndText']),
          ta('commlogNote', 'Commlog note', { help: 'Replaces the default commlog note.' }),
        ],
        warning: 'This may create a communication log entry in the patient record.',
      },
      {
        id: 'switchtype', label: 'Switch recall type', method: 'PUT', path: '/api/recalls/SwitchType',
        description: 'Switches a patient between recall types (for example Prophy and Perio).',
        fields: [pat('PatNum', 'Patient', { required: true })],
        warning: 'Changes the patient recall type in Open Dental.',
      },
    ],
    rowLabel: simpleLabel('RecallNum'),
  },
  {
    name: 'RecallTypes', title: 'Recall types', group: GROUP, summary: 'Recall type setup: intervals and procedures.', basePath: '/api/recalltypes', pk: 'RecallTypeNum',
    listPath: (p) => qs('/api/recalltypes', p), params: [], getSingle: true,
    columns: [col('RecallTypeNum', 'Type #'), col('Description', 'Description'), col('DefaultInterval', 'Default interval'), col('TimePattern', 'Time pattern'), col('Procedures', 'Procedures'), col('AppendToSpecial', 'Append to special')],
    rowLabel: simpleLabel('Description'), readOnlyNote: 'Read-only in the Open Dental API.',
  },
  {
    name: 'RxPats', title: 'Patient prescriptions', group: GROUP, summary: 'Prescriptions written for patients.', basePath: '/api/rxpats', pk: 'RxNum',
    listPath: (p) => qs('/api/rxpats', p), params: [patParam()], getSingle: true,
    columns: [col('RxNum', 'Rx #'), col('PatNum', 'Patient #'), col('RxDate', 'Date'), col('Drug', 'Drug'), col('Sig', 'Directions'), col('Disp', 'Dispense'), col('Refills', 'Refills'), col('ProvNum', 'Provider #'), col('PharmacyNum', 'Pharmacy #'), col('IsControlled', 'Controlled')],
    rowLabel: simpleLabel('Drug'), readOnlyNote: 'Read-only in the Open Dental API.',
  },
  {
    name: 'Vitalsigns', title: 'Vital signs', group: GROUP, summary: 'Height, weight, blood pressure and pulse readings.', basePath: '/api/vitalsigns', pk: 'VitalsignNum',
    listPath: (p) => qs('/api/vitalsigns', p), params: [patParam()], getSingle: true,
    create: [
      pat('PatNum', 'Patient', { required: true }), n('Height', 'Height (inches)'), n('Weight', 'Weight (pounds)'),
      n('BpSystolic', 'Systolic (mmHg)'), n('BpDiastolic', 'Diastolic (mmHg)'), n('Pulse', 'Pulse (beats per minute)'),
      d('DateTaken', 'Date taken', { help: 'Defaults to today.' }), ta('Documentation', 'Note'),
    ],
    update: { style: 'id', fields: [
      n('Height', 'Height (inches)', { help: 'Zero is allowed.' }), n('Weight', 'Weight (pounds)'), n('BpSystolic', 'Systolic (mmHg)'), n('BpDiastolic', 'Diastolic (mmHg)'),
      n('Pulse', 'Pulse (beats per minute)'), d('DateTaken', 'Date taken'), ta('Documentation', 'Note'),
    ] },
    del: true,
    columns: [col('VitalsignNum', 'Reading #'), col('PatNum', 'Patient #'), col('DateTaken', 'Taken'), col('Height', 'Height (in)'), col('Weight', 'Weight (lb)'), col('BpSystolic', 'Systolic'), col('BpDiastolic', 'Diastolic'), col('Pulse', 'Pulse (BPM)'), col('Documentation', 'Note')],
    rowLabel: simpleLabel('VitalsignNum'),
    warning: 'Vital signs are clinical measurements. Zero is a valid value and is sent as zero.',
  },
]);

function d2(name: string, label: string): ParamMeta { return { name, label, kind: 'date' }; }

export const resourceMap: Record<string, ResourceMeta> = Object.fromEntries(resources.map((r) => [r.name, r]));
export const patientsFamiliesNames = resources.map((r) => r.name);
export const lowerPath = low;

/** Convert a form draft into the exact Open Dental request body. */
export function buildBody(fields: FieldMeta[], draft: Record<string, string>, original?: Record<string, unknown>): Record<string, unknown> {
  const body: Record<string, unknown> = {};
  for (const f of fields) {
    if (f.readOnlyOnUpdate && original) continue;
    const entered = draft[f.name] ?? '';
    const raw = f.kind === 'textarea' || f.name === 'FieldValue' ? entered : entered.trim();
    if (original) {
      const prev = formValue(f, getField(original, f.name));
      if (prev === raw && !f.readOnlyOnUpdate) continue;
      if (raw === '' && prev !== '' && (f.kind === 'text' || f.kind === 'textarea')) { body[f.name] = ''; continue; }
      if (raw === '' && prev !== '' && (f.kind === 'date' || f.kind === 'datetime')) {
        body[f.name] = f.kind === 'datetime' ? '0001-01-01 00:00:00' : '0001-01-01';
        continue;
      }
    }
    if (raw === '') continue;
    if (f.kind === 'list') { body[f.name] = raw.split(/[\s,]+/).filter(Boolean).map(Number); continue; }
    if (f.kind === 'number' || (f.kind === 'lookup' && f.lookup?.valueKey !== 'FieldName' && /^-?\d+(\.\d+)?$/.test(raw))) body[f.name] = Number(raw);
    else if (f.kind === 'patient') body[f.name] = Number(raw);
    else if (f.kind === 'datetime') body[f.name] = raw.replace('T', ' ').replace(/^(\d{4}-\d\d-\d\d \d\d:\d\d)$/, '$1:00');
    else body[f.name] = f.name === 'PickList' ? raw.replace(/\r?\n/g, '\r\n') : raw;
  }
  return body;
}

/** Normalize a returned value into the string held by a form control. */
export function formValue(f: FieldMeta, v: unknown): string {
  if (v === undefined || v === null) return '';
  if (Array.isArray(v)) return v.join(', ');
  if (f.kind === 'bool') return String(v) === 'true' || v === true ? 'true' : String(v) === 'false' || v === false ? 'false' : '';
  const str = String(v);
  if (str.startsWith('0001-01-01')) return '';
  if (f.kind === 'datetime') return str.replace(' ', 'T').slice(0, 16);
  if (f.kind === 'date') return str.slice(0, 10);
  return str;
}

export function missingRequired(fields: FieldMeta[], draft: Record<string, string>, original?: Record<string, unknown>): string[] {
  const missing = fields.filter((f) => f.required && !f.allowEmpty && !(draft[f.name] ?? '').trim()).map((f) => f.label);
  if (draft.FieldType === 'PickList' && (!original || getField(original, 'FieldType') !== 'PickList')
      && fields.some((f) => f.name === 'PickList') && !draft.PickList?.trim()) missing.push('Pick list items');
  return missing;
}

/** Normalizes an arbitrary JSON response into rows. */
export function toRows(data: unknown): Record<string, unknown>[] {
  if (Array.isArray(data)) return data.filter((x): x is Record<string, unknown> => !!x && typeof x === 'object');
  if (data && typeof data === 'object') {
    const arr = Object.values(data as Record<string, unknown>).find(Array.isArray);
    if (arr && Object.keys(data as object).length <= 2) return toRows(arr);
    return [data as Record<string, unknown>];
  }
  return [];
}

export function updatePath(r: ResourceMeta, row: Record<string, unknown>): string {
  if (r.update?.style === 'collection' && !r.keyedUpdates) return r.basePath;
  return `${r.basePath}/${encodeURIComponent(String(getField(row, r.pk) ?? ''))}`;
}

/** Include collection-update identity without turning unchanged edits into writes. */
export function buildUpdateBody(r: ResourceMeta, draft: Record<string, string>, row: Record<string, unknown>): Record<string, unknown> {
  const fields = r.update!.fields;
  const body = buildBody(fields, draft, row);
  if (Object.keys(body).length && r.update?.style === 'collection') {
    fields.filter((f) => f.readOnlyOnUpdate).forEach((f) => {
      const value = formValue(f, getField(row, f.name));
      body[f.name] = f.kind === 'patient' ? Number(value) : value;
    });
  }
  return body;
}

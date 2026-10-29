import type { PreviewField, PreviewResourceDefinition } from './patients-families/types';

const text = (key: string, label: string, required = false): PreviewField =>
  ({ key, label, type: 'text', required });
const number = (key: string, label: string, min = 0, max?: number): PreviewField =>
  ({ key, label, type: 'number', min, max, step: '1' });
const date = (key: string, label: string): PreviewField =>
  ({ key, label, type: 'date' });
const select = (key: string, label: string, options: readonly string[]): PreviewField =>
  ({ key, label, type: 'select', options });
const notes = (): PreviewField => ({ key: 'notes', label: 'Notes', type: 'textarea' });

function definition(
  resource: string,
  title: string,
  singular: string,
  description: string,
  fields: PreviewField[],
  columns: string[],
): PreviewResourceDefinition {
  return { section: 'Clinical Care', resource, title, singular, description, patientScoped: false, fields, columns };
}

const teeth = Array.from({ length: 32 }, (_, index) => String(index + 1));

export const clinicalCarePreviewDefinitions: Record<string, PreviewResourceDefinition> = {
  AutoNoteControls: definition(
    'AutoNoteControls', 'Automatic Note Controls', 'note control',
    'Review rules that control automatic clinical note creation.',
    [
      text('controlName', 'Control name', true),
      select('eventType', 'Trigger event', ['Appointment completed', 'Procedure recorded', 'Chart updated']),
      text('noteTemplate', 'Note template', true),
      { key: 'enabled', label: 'Enabled', type: 'checkbox' },
      notes(),
    ],
    ['controlName', 'eventType', 'noteTemplate', 'enabled'],
  ),
  AutoNotes: definition(
    'AutoNotes', 'Automatic Notes', 'automatic note',
    'Preview reusable note text and its clinical category.',
    [
      text('name', 'Note name', true),
      select('category', 'Category', ['Progress', 'Examination', 'Treatment', 'Follow-up']),
      select('scope', 'Available for', ['All providers', 'Provider-specific', 'Clinic-specific']),
      { key: 'content', label: 'Note text', type: 'textarea', required: true },
      { key: 'active', label: 'Active', type: 'checkbox' },
    ],
    ['name', 'category', 'scope', 'active'],
  ),
  ChartModules: definition(
    'ChartModules', 'Chart Modules', 'chart module',
    'Arrange reusable sections in the clinical chart.',
    [
      text('moduleName', 'Module name', true),
      select('moduleType', 'Module type', ['Exam', 'Periodontal', 'Restorative', 'Other']),
      number('displayOrder', 'Display order'),
      text('color', 'Display color'),
      { key: 'active', label: 'Active', type: 'checkbox' },
      notes(),
    ],
    ['moduleName', 'moduleType', 'displayOrder', 'color', 'active'],
  ),
  CodeGroups: definition(
    'CodeGroups', 'Procedure Code Groups', 'code group',
    'Organize procedure codes into charting and billing groups.',
    [
      text('groupName', 'Group name', true),
      select('category', 'Category', ['Diagnostic', 'Preventive', 'Restorative', 'Surgical', 'Other']),
      text('description', 'Description'),
      number('displayOrder', 'Display order'),
      { key: 'active', label: 'Active', type: 'checkbox' },
      notes(),
    ],
    ['groupName', 'category', 'description', 'displayOrder', 'active'],
  ),
  PerioExams: definition(
    'PerioExams', 'Periodontal Exams', 'periodontal exam',
    'Review periodontal examination summaries and follow-up status.',
    [
      text('patientName', 'Fictional patient name', true),
      date('examDate', 'Examination date'),
      text('provider', 'Provider'),
      select('examType', 'Examination type', ['Comprehensive', 'Limited', 'Re-evaluation']),
      select('status', 'Status', ['In progress', 'Reviewed', 'Complete']),
      notes(),
    ],
    ['examDate', 'patientName', 'provider', 'examType', 'status'],
  ),
  PerioMeasures: definition(
    'PerioMeasures', 'Periodontal Measurements', 'periodontal measurement',
    'Review tooth-site measurements within a periodontal exam.',
    [
      text('examReference', 'Fictional exam reference', true),
      select('toothNumber', 'Tooth number', teeth),
      select('site', 'Site', ['MB', 'B', 'DB', 'ML', 'L', 'DL']),
      number('probingDepthMm', 'Probing depth (mm)', 0, 15),
      number('recessionMm', 'Recession (mm)', 0, 15),
      select('bleeding', 'Bleeding on probing', ['No', 'Yes']),
      notes(),
    ],
    ['examReference', 'toothNumber', 'site', 'probingDepthMm', 'recessionMm', 'bleeding'],
  ),
  ProcedureCodes: definition(
    'ProcedureCodes', 'Procedure Codes', 'procedure code',
    'Review the procedure code directory and display details.',
    [
      text('code', 'Procedure code', true),
      text('description', 'Description', true),
      select('category', 'Category', ['Diagnostic', 'Preventive', 'Restorative', 'Surgical', 'Other']),
      number('standardFee', 'Display fee'),
      { key: 'active', label: 'Active', type: 'checkbox' },
      notes(),
    ],
    ['code', 'description', 'category', 'standardFee', 'active'],
  ),
  ProcedureLogs: definition(
    'ProcedureLogs', 'Completed Procedures', 'procedure log',
    'Review fictional procedure history and completion status.',
    [
      text('patientName', 'Fictional patient name', true),
      date('procedureDate', 'Procedure date'),
      text('provider', 'Provider'),
      select('toothNumber', 'Tooth number', teeth),
      text('procedureCode', 'Procedure code'),
      select('status', 'Status', ['Planned', 'Completed', 'Referred', 'Declined']),
      notes(),
    ],
    ['procedureDate', 'patientName', 'toothNumber', 'procedureCode', 'status'],
  ),
  ProcNotes: definition(
    'ProcNotes', 'Procedure Notes', 'procedure note',
    'Preview procedure-specific notes and review status.',
    [
      text('patientName', 'Fictional patient name', true),
      date('noteDate', 'Note date'),
      text('provider', 'Provider'),
      select('noteType', 'Note type', ['Progress', 'Operative', 'Post-operative', 'Other']),
      { key: 'content', label: 'Note content', type: 'textarea', required: true },
      select('status', 'Status', ['Draft', 'Reviewed', 'Final']),
    ],
    ['noteDate', 'patientName', 'provider', 'noteType', 'status'],
  ),
  ProcTPs: definition(
    'ProcTPs', 'Treatment Plan Procedures', 'planned procedure',
    'Review proposed procedures and their plan status.',
    [
      text('patientName', 'Fictional patient name', true),
      text('planName', 'Plan name', true),
      text('procedureCode', 'Procedure code'),
      select('toothNumber', 'Tooth number', teeth),
      number('estimatedFee', 'Display fee'),
      select('status', 'Status', ['Proposed', 'Accepted', 'Declined', 'Complete']),
      notes(),
    ],
    ['patientName', 'planName', 'procedureCode', 'toothNumber', 'estimatedFee', 'status'],
  ),
  ToothInitials: definition(
    'ToothInitials', 'Tooth Chart Labels', 'tooth label',
    'Review the short labels displayed in tooth charts.',
    [
      select('toothNumber', 'Tooth number', teeth),
      text('label', 'Label', true),
      text('abbreviation', 'Abbreviation'),
      select('chartStatus', 'Chart status', ['Present', 'Missing', 'Planned', 'Completed']),
      number('displayOrder', 'Display order'),
      notes(),
    ],
    ['toothNumber', 'label', 'abbreviation', 'chartStatus', 'displayOrder'],
  ),
  TreatPlanAttaches: definition(
    'TreatPlanAttaches', 'Treatment Plan Attachments', 'plan attachment',
    'Review documents associated with fictional treatment plans.',
    [
      text('patientName', 'Fictional patient name', true),
      text('planName', 'Plan name', true),
      text('documentName', 'Document name', true),
      select('documentType', 'Document type', ['Image', 'Referral', 'Estimate', 'Other']),
      date('attachedOn', 'Added on'),
      { key: 'reviewed', label: 'Reviewed', type: 'checkbox' },
      notes(),
    ],
    ['patientName', 'planName', 'documentName', 'documentType', 'attachedOn', 'reviewed'],
  ),
  TreatPlans: definition(
    'TreatPlans', 'Treatment Plans', 'treatment plan',
    'Review proposed treatment plans and their current status.',
    [
      text('patientName', 'Fictional patient name', true),
      text('planName', 'Plan name', true),
      date('createdOn', 'Created on'),
      select('status', 'Status', ['Proposed', 'Accepted', 'Declined', 'In progress', 'Complete']),
      number('estimatedFee', 'Display fee'),
      date('reviewBy', 'Review by'),
      notes(),
    ],
    ['patientName', 'planName', 'createdOn', 'status', 'estimatedFee', 'reviewBy'],
  ),
};
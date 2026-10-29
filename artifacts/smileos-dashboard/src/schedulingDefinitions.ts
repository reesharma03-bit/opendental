import type { PreviewField, PreviewResourceDefinition } from './patients-families/types';

const text = (key: string, label: string, required = false): PreviewField =>
  ({ key, label, type: 'text', required });
const number = (key: string, label: string, min = 0): PreviewField =>
  ({ key, label, type: 'number', min, step: '1' });
const date = (key: string, label: string): PreviewField =>
  ({ key, label, type: 'date' });
const select = (key: string, label: string, options: readonly string[]): PreviewField =>
  ({ key, label, type: 'select', options });
const notes = (key = 'notes'): PreviewField => ({ key, label: 'Notes', type: 'textarea' });

function definition(
  resource: string,
  title: string,
  singular: string,
  description: string,
  fields: PreviewField[],
  columns: string[],
): PreviewResourceDefinition {
  return { section: 'Scheduling', resource, title, singular, description, patientScoped: false, fields, columns };
}

export const schedulingPreviewDefinitions: Record<string, PreviewResourceDefinition> = {
  Appointments: definition(
    'Appointments', 'Appointment Board', 'appointment',
    'Review appointment times, providers, operatories, and visit status.',
    [
      text('patientName', 'Patient name', true),
      text('provider', 'Provider', true),
      text('operatory', 'Operatory', true),
      date('appointmentDate', 'Appointment date'),
      text('startTime', 'Start time', true),
      number('lengthMinutes', 'Length (minutes)', 5),
      select('appointmentType', 'Appointment type', ['Cleaning', 'Exam', 'Treatment', 'Consultation']),
      select('status', 'Status', ['Scheduled', 'Confirmed', 'Arrived', 'Completed', 'Cancelled']),
      notes(),
    ],
    ['startTime', 'patientName', 'provider', 'operatory', 'appointmentType', 'status'],
  ),
  AppointmentTypes: definition(
    'AppointmentTypes', 'Appointment Types', 'appointment type',
    'Set up visit types, default lengths, and their availability.',
    [
      text('name', 'Type name', true),
      number('defaultLength', 'Default length (minutes)', 5),
      select('category', 'Category', ['Preventive', 'Diagnostic', 'Treatment', 'Consultation']),
      text('displayColor', 'Display color'),
      { key: 'active', label: 'Active', type: 'checkbox' },
      notes(),
    ],
    ['name', 'category', 'defaultLength', 'displayColor', 'active'],
  ),
  ApptFields: definition(
    'ApptFields', 'Appointment Custom Fields', 'custom field',
    'Review custom field values captured on scheduled visits.',
    [
      text('appointmentLabel', 'Appointment', true),
      text('fieldLabel', 'Field label', true),
      select('fieldType', 'Field type', ['Text', 'Date', 'Number', 'Checkbox']),
      text('value', 'Field value'),
      { key: 'required', label: 'Required', type: 'checkbox' },
      notes(),
    ],
    ['appointmentLabel', 'fieldLabel', 'fieldType', 'value', 'required'],
  ),
  ApptFieldDefs: definition(
    'ApptFieldDefs', 'Appointment Field Definitions', 'field definition',
    'Organize the custom fields available to appointment forms.',
    [
      text('name', 'Field name', true),
      select('fieldType', 'Field type', ['Text', 'Date', 'Number', 'Checkbox']),
      number('displayOrder', 'Display order'),
      { key: 'required', label: 'Required', type: 'checkbox' },
      { key: 'active', label: 'Active', type: 'checkbox' },
      notes(),
    ],
    ['name', 'fieldType', 'displayOrder', 'required', 'active'],
  ),
  AsapComms: definition(
    'AsapComms', 'ASAP Appointment Requests', 'ASAP request',
    'Track patients who would like an earlier opening.',
    [
      text('patientName', 'Patient name', true),
      date('requestedOn', 'Request date'),
      select('preferredTime', 'Preferred time', ['Any time', 'Morning', 'Afternoon', 'Evening']),
      text('preferredProvider', 'Preferred provider'),
      select('status', 'Status', ['Waiting', 'Contacted', 'Scheduled', 'Removed']),
      notes(),
    ],
    ['patientName', 'requestedOn', 'preferredTime', 'preferredProvider', 'status'],
  ),
  ClockEvents: definition(
    'ClockEvents', 'Staff Clock Events', 'clock event',
    'Review staff time-clock activity and recorded clock events.',
    [
      text('employeeName', 'Employee name', true),
      date('eventDate', 'Event date'),
      text('clockIn', 'Clock-in time'),
      text('clockOut', 'Clock-out time'),
      select('eventType', 'Event type', ['Regular shift', 'Break', 'Correction']),
      notes(),
    ],
    ['employeeName', 'eventDate', 'clockIn', 'clockOut', 'eventType'],
  ),
  HistAppointments: definition(
    'HistAppointments', 'Appointment History', 'history entry',
    'Review prior visits and their recorded outcomes.',
    [
      text('patientName', 'Patient name', true),
      date('appointmentDate', 'Visit date'),
      text('provider', 'Provider'),
      text('operatory', 'Operatory'),
      select('outcome', 'Outcome', ['Completed', 'Cancelled', 'Missed', 'Rescheduled']),
      notes(),
    ],
    ['appointmentDate', 'patientName', 'provider', 'operatory', 'outcome'],
  ),
  Operatories: definition(
    'Operatories', 'Operatories', 'operatory',
    'Manage the rooms and operatories used for appointments.',
    [
      text('name', 'Operatory name', true),
      text('abbreviation', 'Abbreviation'),
      text('clinic', 'Clinic'),
      text('defaultProvider', 'Default provider'),
      { key: 'active', label: 'Active', type: 'checkbox' },
      notes(),
    ],
    ['name', 'abbreviation', 'clinic', 'defaultProvider', 'active'],
  ),
  ScheduleOps: definition(
    'ScheduleOps', 'Scheduled Operatory Blocks', 'schedule block',
    'Review reserved, blocked, and unavailable operatory time.',
    [
      text('operatory', 'Operatory', true),
      text('provider', 'Provider'),
      date('blockDate', 'Date'),
      text('startTime', 'Start time'),
      text('endTime', 'End time'),
      select('blockType', 'Block type', ['Unavailable', 'Reserved', 'Administrative']),
      notes(),
    ],
    ['blockDate', 'operatory', 'startTime', 'endTime', 'blockType'],
  ),
  Schedules: definition(
    'Schedules', 'Provider Schedules', 'provider schedule',
    'Set the regular days, locations, and hours for providers.',
    [
      text('provider', 'Provider', true),
      text('clinic', 'Clinic'),
      select('dayOfWeek', 'Day of week', ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']),
      text('startTime', 'Start time'),
      text('endTime', 'End time'),
      { key: 'active', label: 'Active', type: 'checkbox' },
      notes(),
    ],
    ['provider', 'clinic', 'dayOfWeek', 'startTime', 'endTime', 'active'],
  ),
};
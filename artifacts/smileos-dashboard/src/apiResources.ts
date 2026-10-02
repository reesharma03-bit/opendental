export const API_SPEC_URL = 'https://www.opendental.com/site/apispecification.html';

const resourceNames = [
  'AccountModules', 'Adjustments', 'Allergies', 'AllergyDefs', 'Appointments', 'AppointmentTypes',
  'ApptFields', 'ApptFieldDefs', 'AsapComms', 'AutoNoteControls', 'AutoNotes', 'Benefits',
  'Carriers', 'ChartModules', 'ClaimForms', 'ClaimPayments', 'ClaimProcs', 'Claims',
  'ClaimTrackings', 'Clinics', 'ClockEvents', 'CodeGroups', 'Commlogs', 'Computers',
  'CovCats', 'CovSpans', 'Deposits', 'Definitions', 'DiscountPlans', 'DiscountPlanSubs',
  'DiseaseDefs', 'Diseases', 'Documents', 'EhrPatients', 'EobAttaches', 'Employees',
  'Employers', 'EtransMessageTexts', 'Etranss', 'FamilyModules', 'Fees', 'FeeScheds',
  'Guardians', 'HistAppointments', 'InsPlans', 'InsSubs', 'InsVerifies', 'LabCases',
  'Laboratories', 'LabTurnarounds', 'MedicationPats', 'Medications', 'Operatories',
  'PatFieldDefs', 'PatFields', 'PatientNotes', 'PatientRaces', 'Patients', 'PatPlans',
  'PatRestrictions', 'Payments', 'PayPlanCharges', 'PayPlanLinks', 'PayPlans', 'PaySplits',
  'PerioExams', 'PerioMeasures', 'Pharmacies', 'Popups', 'Preferences', 'ProcedureCodes',
  'ProcedureLogs', 'ProcNotes', 'ProcTPs', 'Providers', 'Queries', 'QuickPasteCats',
  'QuickPasteNotes', 'Recalls', 'RecallTypes', 'RefAttaches', 'Referrals', 'Reports',
  'RxPats', 'ScheduleOps', 'Schedules', 'SecurityLogs', 'SecurityPerms', 'SheetDefs',
  'Sheets', 'SheetFieldDefs', 'SheetFields', 'Signalods', 'Statements', 'Subscriptions',
  'SubstitutionLinks', 'TaskLists', 'TaskNotes', 'Tasks', 'ToothInitials',
  'TreatPlanAttaches', 'TreatPlans', 'UserGroupAttaches', 'UserGroups', 'Userods', 'Vitalsigns',
] as const;

export type ApiResource = {
  name: string;
  url: string;
};

export const apiResources: ApiResource[] = resourceNames
  .map((name) => ({
    name,
    url: name === 'SecurityPerms'
      ? 'https://www.opendental.com/site/apisecurity.html'
      : `https://www.opendental.com/site/api${name.toLowerCase()}.html`,
  }))
  .sort((a, b) => a.name.localeCompare(b.name));
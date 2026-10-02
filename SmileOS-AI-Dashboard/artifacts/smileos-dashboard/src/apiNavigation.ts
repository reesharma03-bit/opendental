export const apiNavigationGroups = [
  {
    label: 'Patients & Families',
    resources: [
      'Allergies', 'AllergyDefs', 'DiseaseDefs', 'Diseases', 'EhrPatients',
      'FamilyModules', 'Guardians', 'MedicationPats', 'Medications',
      'PatientNotes', 'PatientRaces', 'Patients', 'PatFieldDefs', 'PatFields',
      'PatPlans', 'PatRestrictions', 'Pharmacies', 'Popups', 'Recalls',
      'RecallTypes', 'RxPats', 'Vitalsigns',
    ],
  },
  {
    label: 'Scheduling',
    resources: [
      'Appointments', 'AppointmentTypes', 'ApptFields', 'ApptFieldDefs',
      'AsapComms', 'ClockEvents', 'HistAppointments', 'Operatories',
      'ScheduleOps', 'Schedules',
    ],
  },
  {
    label: 'Clinical Care',
    resources: [
      'AutoNoteControls', 'AutoNotes', 'ChartModules', 'CodeGroups',
      'PerioExams', 'PerioMeasures', 'ProcedureCodes', 'ProcedureLogs',
      'ProcNotes', 'ProcTPs', 'ToothInitials', 'TreatPlanAttaches',
      'TreatPlans',
    ],
  },
  {
    label: 'Insurance & Billing',
    resources: [
      'Adjustments', 'Benefits', 'Carriers', 'ClaimForms', 'ClaimPayments',
      'ClaimProcs', 'Claims', 'ClaimTrackings', 'CovCats', 'CovSpans',
      'Deposits', 'DiscountPlans', 'DiscountPlanSubs', 'EobAttaches', 'Fees',
      'FeeScheds', 'InsPlans', 'InsSubs', 'InsVerifies', 'Payments',
      'PayPlanCharges', 'PayPlanLinks', 'PayPlans', 'PaySplits',
      'Statements', 'SubstitutionLinks',
    ],
  },
  {
    label: 'Communications & Documents',
    resources: [
      'Commlogs', 'Documents', 'EtransMessageTexts', 'Etranss',
      'RefAttaches', 'Referrals', 'Subscriptions',
    ],
  },
  {
    label: 'Office & Practice Setup',
    resources: [
      'AccountModules', 'Clinics', 'Computers', 'Definitions', 'Employees',
      'Employers', 'LabCases', 'Laboratories', 'LabTurnarounds', 'Providers',
    ],
  },
  {
    label: 'Forms & Sheets',
    resources: ['SheetDefs', 'Sheets', 'SheetFieldDefs', 'SheetFields'],
  },
  {
    label: 'Tasks & Quick Notes',
    resources: [
      'QuickPasteCats', 'QuickPasteNotes', 'TaskLists', 'TaskNotes', 'Tasks',
    ],
  },
  {
    label: 'Users & Security',
    resources: [
      'Preferences', 'SecurityLogs', 'SecurityPerms', 'UserGroupAttaches',
      'UserGroups', 'Userods',
    ],
  },
  {
    label: 'Reports & Queries',
    resources: ['Queries', 'Reports'],
  },
  {
    label: 'System & Signals',
    resources: ['Signalods'],
  },
] as const;

export function formatApiResourceName(name: string): string {
  return name.replace(/([a-z])([A-Z])/g, '$1 $2');
}
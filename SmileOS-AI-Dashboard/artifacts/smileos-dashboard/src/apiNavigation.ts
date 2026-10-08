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
    label: 'Communication & Referrals',
    resources: ['Commlogs', 'Employers', 'RefAttaches', 'Referrals'],
  },
  {
    label: 'Scheduling',
    resources: [
      'Appointments', 'AppointmentTypes', 'ApptFields', 'ApptFieldDefs',
      'AsapComms', 'ClockEvents', 'HistAppointments', 'Operatories',
      'Providers', 'ScheduleOps', 'Schedules',
    ],
  },
  {
    label: 'Clinical Care',
    resources: [
      'AutoNoteControls', 'AutoNotes', 'ChartModules', 'CodeGroups',
      'LabCases', 'Laboratories', 'LabTurnarounds',
      'PerioExams', 'PerioMeasures', 'ProcedureCodes', 'ProcedureLogs',
      'ProcNotes', 'ProcTPs', 'ToothInitials', 'TreatPlanAttaches',
      'TreatPlans',
    ],
  },
  {
    label: 'Forms',
    resources: ['Sheets', 'SheetFields', 'SheetDefs', 'SheetFieldDefs'],
  },
  {
    label: 'Insurance & Billing',
    resources: [
      'AccountModules', 'Adjustments', 'Benefits', 'Carriers', 'ClaimForms', 'ClaimPayments',
      'ClaimProcs', 'Claims', 'ClaimTrackings', 'CovCats', 'CovSpans',
      'Deposits', 'DiscountPlans', 'DiscountPlanSubs', 'EobAttaches', 'Etranss',
      'Fees', 'FeeScheds', 'InsPlans', 'InsSubs', 'InsVerifies', 'Payments',
      'PayPlanCharges', 'PayPlanLinks', 'PayPlans', 'PaySplits',
      'Statements', 'SubstitutionLinks',
    ],
  },
  {
    label: 'Users & Security',
    resources: ['Userods', 'UserGroups', 'UserGroupAttaches'],
  },
  {
    label: 'Tasks & Practice Setup',
    resources: [
      'Definitions', 'Employees', 'QuickPasteCats', 'QuickPasteNotes',
      'TaskLists', 'TaskNotes', 'Tasks',
    ],
  },
] as const;

export function formatApiResourceName(name: string): string {
  return name.replace(/([a-z])([A-Z])/g, '$1 $2');
}

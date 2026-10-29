import type { PreviewField, PreviewResourceDefinition } from './patients-families/types';

const text = (key: string, label: string, required = false): PreviewField =>
  ({ key, label, type: 'text', required });
const number = (key: string, label: string): PreviewField =>
  ({ key, label, type: 'number', min: 0, step: '0.01' });
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
  return { section: 'Insurance & Billing', resource, title, singular, description, patientScoped: false, fields, columns };
}

const active = { key: 'active', label: 'Active', type: 'checkbox' } as const;
const planStatus = ['Active', 'Pending', 'Inactive'];

export const billingPreviewDefinitions: Record<string, PreviewResourceDefinition> = {
  Benefits: definition('Benefits', 'Insurance Benefits', 'benefit', 'Review fictional coverage details and benefit limits.', [
    text('planName', 'Plan name', true), text('serviceCategory', 'Service category', true),
    number('coveragePercent', 'Coverage (%)'), number('annualMaximum', 'Annual maximum'),
    number('deductible', 'Deductible'), date('effectiveOn', 'Effective on'),
    select('status', 'Status', planStatus), notes(),
  ], ['planName', 'serviceCategory', 'coveragePercent', 'annualMaximum', 'status']),
  Carriers: definition('Carriers', 'Insurance Carriers', 'carrier', 'Review insurer contacts and payer details.', [
    text('name', 'Carrier name', true), text('payerId', 'Payer ID'),
    text('phone', 'Phone'), { key: 'email', label: 'Email', type: 'email' },
    text('website', 'Website'), select('status', 'Status', planStatus), notes(),
  ], ['name', 'payerId', 'phone', 'email', 'status']),
  ClaimForms: definition('ClaimForms', 'Claim Forms', 'claim form', 'Keep a local preview list of claim form templates.', [
    text('formName', 'Form name', true), text('carrierName', 'Carrier'),
    select('formType', 'Form type', ['Electronic', 'Paper', 'Attachment']),
    text('formCode', 'Form code'), select('status', 'Status', ['Ready', 'Draft', 'Archived']), notes(),
  ], ['formName', 'carrierName', 'formType', 'formCode', 'status']),
  ClaimPayments: definition('ClaimPayments', 'Insurance Claim Payments', 'claim payment', 'Review fictional payments posted to insurance claims.', [
    text('claimReference', 'Fictional claim reference', true), text('carrierName', 'Carrier'),
    date('paymentDate', 'Payment date'), text('referenceNumber', 'Payment reference'),
    number('amount', 'Display amount'), select('status', 'Status', ['Posted', 'Pending', 'Reversed']), notes(),
  ], ['claimReference', 'carrierName', 'paymentDate', 'amount', 'status']),
  ClaimProcs: definition('ClaimProcs', 'Claim Procedures', 'claim procedure', 'Review fictional procedures associated with claims.', [
    text('claimReference', 'Fictional claim reference', true), text('procedureCode', 'Procedure code'),
    date('serviceDate', 'Service date'), text('toothNumber', 'Tooth number'),
    number('submittedAmount', 'Submitted amount'), number('insuranceEstimate', 'Insurance estimate'),
    select('status', 'Status', ['Submitted', 'Paid', 'Denied', 'Pending']), notes(),
  ], ['claimReference', 'procedureCode', 'serviceDate', 'submittedAmount', 'insuranceEstimate', 'status']),
  ClaimTrackings: definition('ClaimTrackings', 'Claim Tracking', 'claim tracking entry', 'Track fictional claim submission and response status.', [
    text('claimReference', 'Fictional claim reference', true), text('carrierName', 'Carrier'),
    select('status', 'Status', ['Created', 'Submitted', 'Received', 'Needs review', 'Resolved']),
    date('submittedOn', 'Submitted on'), date('updatedOn', 'Last updated'),
    text('responseReference', 'Response reference'), notes(),
  ], ['claimReference', 'carrierName', 'status', 'submittedOn', 'updatedOn']),
  Claims: definition('Claims', 'Insurance Claims', 'claim', 'Review fictional claims, service dates, and processing status.', [
    text('claimReference', 'Fictional claim reference', true), text('patientName', 'Fictional patient'),
    text('planName', 'Plan name'), date('serviceDate', 'Service date'),
    number('submittedAmount', 'Submitted amount'),
    select('status', 'Status', ['Draft', 'Submitted', 'Pending', 'Paid', 'Denied']), notes(),
  ], ['claimReference', 'patientName', 'serviceDate', 'submittedAmount', 'status']),
  CovCats: definition('CovCats', 'Coverage Categories', 'coverage category', 'Organize insurance coverage categories for preview.', [
    text('categoryName', 'Category name', true), text('description', 'Description'),
    select('coverageType', 'Coverage type', ['Preventive', 'Basic', 'Major', 'Orthodontic']),
    number('displayOrder', 'Display order'), active, notes(),
  ], ['categoryName', 'coverageType', 'description', 'displayOrder', 'active']),
  CovSpans: definition('CovSpans', 'Coverage Periods', 'coverage period', 'Review fictional plan coverage periods and limits.', [
    text('planName', 'Plan name', true), text('serviceCategory', 'Service category'),
    date('effectiveOn', 'Effective on'), date('endsOn', 'Ends on'),
    number('coveragePercent', 'Coverage (%)'), number('frequencyLimit', 'Frequency limit'),
    select('status', 'Status', planStatus), notes(),
  ], ['planName', 'serviceCategory', 'effectiveOn', 'endsOn', 'coveragePercent', 'status']),
  Deposits: definition('Deposits', 'Deposits', 'deposit', 'Review fictional practice deposits and their sources.', [
    date('depositDate', 'Deposit date'), text('depositReference', 'Fictional reference', true),
    select('source', 'Source', ['Patient payment', 'Insurance', 'Other']),
    select('paymentType', 'Payment type', ['Cash', 'Check', 'Electronic', 'Other']),
    number('amount', 'Display amount'), select('status', 'Status', ['Recorded', 'Pending', 'Reconciled']), notes(),
  ], ['depositDate', 'depositReference', 'source', 'paymentType', 'amount', 'status']),
  DiscountPlans: definition('DiscountPlans', 'Discount Plans', 'discount plan', 'Review fictional self-pay plan terms and availability.', [
    text('planName', 'Plan name', true), text('description', 'Description'),
    number('annualFee', 'Annual fee'), number('discountPercent', 'Discount (%)'),
    date('effectiveOn', 'Effective on'), date('endsOn', 'Ends on'),
    select('status', 'Status', planStatus), notes(),
  ], ['planName', 'annualFee', 'discountPercent', 'effectiveOn', 'status']),
  DiscountPlanSubs: definition('DiscountPlanSubs', 'Discount Plan Enrollments', 'plan enrollment', 'Track fictional enrollments in self-pay plans.', [
    text('patientName', 'Fictional patient', true), text('planName', 'Plan name'),
    date('enrolledOn', 'Enrolled on'), date('endsOn', 'Ends on'),
    number('displayFee', 'Display fee'), select('status', 'Status', ['Active', 'Pending', 'Expired']), notes(),
  ], ['patientName', 'planName', 'enrolledOn', 'endsOn', 'displayFee', 'status']),
  EobAttaches: definition('EobAttaches', 'Explanation of Benefits Attachments', 'EOB attachment', 'Organize fictional explanation-of-benefits attachments.', [
    text('claimReference', 'Fictional claim reference', true), text('documentName', 'Document name', true),
    select('documentType', 'Document type', ['Explanation of benefits', 'Remittance', 'Other']),
    date('receivedOn', 'Received on'), text('carrierName', 'Carrier'),
    { key: 'reviewed', label: 'Reviewed', type: 'checkbox' }, notes(),
  ], ['claimReference', 'documentName', 'documentType', 'receivedOn', 'carrierName', 'reviewed']),
  Fees: definition('Fees', 'Procedure Fees', 'fee entry', 'Review fictional procedure fee amounts and categories.', [
    text('procedureCode', 'Procedure code', true), text('description', 'Description', true),
    text('feeSchedule', 'Fee schedule'), number('displayFee', 'Display fee'),
    date('effectiveOn', 'Effective on'), select('status', 'Status', ['Current', 'Pending', 'Archived']), notes(),
  ], ['procedureCode', 'description', 'feeSchedule', 'displayFee', 'effectiveOn', 'status']),
  FeeScheds: definition('FeeScheds', 'Fee Schedules', 'fee schedule', 'Manage fictional fee schedule summaries.', [
    text('scheduleName', 'Schedule name', true), select('scheduleType', 'Schedule type', ['Standard', 'Insurance', 'Discount']),
    text('clinic', 'Clinic'), date('effectiveOn', 'Effective on'),
    select('status', 'Status', planStatus), notes(),
  ], ['scheduleName', 'scheduleType', 'clinic', 'effectiveOn', 'status']),
  InsPlans: definition('InsPlans', 'Insurance Plans', 'insurance plan', 'Review fictional insurance plan summaries and terms.', [
    text('planName', 'Plan name', true), text('carrierName', 'Carrier'),
    text('groupNumber', 'Fictional group reference'), select('planType', 'Plan type', ['PPO', 'HMO', 'Indemnity', 'Other']),
    date('effectiveOn', 'Effective on'), number('annualMaximum', 'Annual maximum'),
    number('deductible', 'Deductible'), select('status', 'Status', planStatus), notes(),
  ], ['planName', 'carrierName', 'planType', 'effectiveOn', 'annualMaximum', 'status']),
  InsSubs: definition('InsSubs', 'Insurance Subscribers', 'subscriber record', 'Review fictional subscriber-plan associations.', [
    text('subscriberName', 'Fictional subscriber', true), text('planName', 'Plan name'),
    text('memberReference', 'Fictional member reference'),
    select('relationship', 'Relationship', ['Self', 'Spouse', 'Child', 'Other']),
    date('effectiveOn', 'Effective on'), select('status', 'Status', planStatus), notes(),
  ], ['subscriberName', 'planName', 'memberReference', 'relationship', 'effectiveOn', 'status']),
  InsVerifies: definition('InsVerifies', 'Insurance Verifications', 'verification entry', 'Track fictional insurance eligibility review status.', [
    text('patientName', 'Fictional patient', true), text('carrierName', 'Carrier'),
    text('planName', 'Plan name'), date('verifiedOn', 'Reviewed on'),
    select('status', 'Status', ['Not started', 'In review', 'Confirmed', 'Needs follow-up']),
    text('reviewer', 'Reviewer'), notes(),
  ], ['patientName', 'carrierName', 'planName', 'verifiedOn', 'status']),
  Payments: definition('Payments', 'Payments', 'payment', 'Review fictional patient and insurance payment entries.', [
    text('paymentReference', 'Fictional payment reference', true), text('patientName', 'Fictional patient'),
    date('postedOn', 'Posted on'), select('paymentType', 'Payment type', ['Patient', 'Insurance', 'Other']),
    select('method', 'Method', ['Cash', 'Check', 'Card', 'Electronic']),
    number('amount', 'Display amount'), select('status', 'Status', ['Posted', 'Pending', 'Reversed']), notes(),
  ], ['paymentReference', 'patientName', 'postedOn', 'paymentType', 'amount', 'status']),
  PayPlanCharges: definition('PayPlanCharges', 'Payment Plan Charges', 'plan charge', 'Review fictional amounts due on payment plans.', [
    text('planReference', 'Fictional plan reference', true), text('patientName', 'Fictional patient'),
    date('dueOn', 'Due on'), number('amount', 'Display amount'),
    select('status', 'Status', ['Due', 'Paid', 'Overdue', 'Waived']), notes(),
  ], ['planReference', 'patientName', 'dueOn', 'amount', 'status']),
  PayPlanLinks: definition('PayPlanLinks', 'Payment Plan Links', 'plan link', 'Review fictional links between payment plans and balances.', [
    text('planReference', 'Fictional plan reference', true), text('balanceReference', 'Fictional balance reference'),
    date('linkedOn', 'Linked on'), number('linkedAmount', 'Display amount'),
    select('status', 'Status', ['Active', 'Closed', 'Pending']), notes(),
  ], ['planReference', 'balanceReference', 'linkedOn', 'linkedAmount', 'status']),
  PayPlans: definition('PayPlans', 'Payment Plans', 'payment plan', 'Review fictional installment plan terms and progress.', [
    text('patientName', 'Fictional patient', true), text('planName', 'Plan name'),
    number('totalAmount', 'Display total'), number('installmentAmount', 'Display installment'),
    date('nextDueOn', 'Next due on'),
    select('status', 'Status', ['Active', 'Completed', 'Paused', 'Past due']), notes(),
  ], ['patientName', 'planName', 'totalAmount', 'installmentAmount', 'nextDueOn', 'status']),
  PaySplits: definition('PaySplits', 'Payment Splits', 'payment split', 'Review fictional allocations across account balances.', [
    text('paymentReference', 'Fictional payment reference', true), text('accountReference', 'Fictional account reference'),
    date('postedOn', 'Posted on'), number('patientAmount', 'Display patient amount'),
    number('insuranceAmount', 'Display insurance amount'),
    select('status', 'Status', ['Allocated', 'Pending', 'Reversed']), notes(),
  ], ['paymentReference', 'accountReference', 'postedOn', 'patientAmount', 'insuranceAmount', 'status']),
  Statements: definition('Statements', 'Statements', 'statement', 'Review fictional statement delivery and balance details.', [
    text('statementReference', 'Fictional statement reference', true), text('patientName', 'Fictional patient'),
    date('statementDate', 'Statement date'), date('dueOn', 'Due on'),
    number('displayBalance', 'Display balance'),
    select('deliveryStatus', 'Delivery status', ['Draft', 'Ready', 'Sent', 'Viewed']),
    select('status', 'Status', ['Open', 'Paid', 'Past due']), notes(),
  ], ['statementReference', 'patientName', 'statementDate', 'dueOn', 'displayBalance', 'status']),
  SubstitutionLinks: definition('SubstitutionLinks', 'Procedure Substitution Links', 'substitution link', 'Review fictional equivalent procedure code mappings.', [
    text('originalCode', 'Original code', true), text('substituteCode', 'Substitute code', true),
    text('description', 'Description'),
    select('rule', 'Rule', ['Equivalent', 'Plan-specific', 'Manual review']),
    date('effectiveOn', 'Effective on'), select('status', 'Status', ['Active', 'Pending', 'Inactive']), notes(),
  ], ['originalCode', 'substituteCode', 'description', 'rule', 'effectiveOn', 'status']),
};
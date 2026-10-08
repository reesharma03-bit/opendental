package com.clinic.opendental.service.Impl;

import java.util.Map;
import java.util.TreeMap;

/**
 * What Open Dental accepts on POST / PUT / DELETE for each resource the dashboard copies
 * into od_resource_records, from https://www.opendental.com/site/api&lt;resource&gt;.html
 * (read 2026-10-08). Syntax: see {@link WriteSpec}. Only plain create / update-by-key /
 * delete-by-key are here; special actions (e.g. POST /treatplans/Saved, PUT
 * /appointments/{AptNum}/Break) are not. Patients, appointments and procedure logs have
 * their own tables and services. A resource that isn't listed is read-only.
 *
 * <p>Kinds: number, date (yyyy-MM-dd), datetime (yyyy-MM-dd HH:mm:ss), bool ("true"/"false"),
 * patient (a PatNum), select (listed values), list (array of numbers), text, textarea.</p>
 */
public final class OdWriteSpecs {

    private static final String PATREL = "Self|Spouse|Child|Employee|HandicapDep|SignifOther|InjuredPlaintiff|LifePartner|Dependent";
    private static final String GUARDIAN_REL = "Mother|Stepfather|Stepmother|Grandfather|Grandmother|Father|Brother|CareGiver|FosterChild|"
            + "Guardian|Grandparent|Other|Parent|Stepchild|Self|Sibling|Sister|Spouse|Child|LifePartner|Friend|Grandchild|Sitter";
    private static final String TREAT_AREA = "None|Surf|Tooth|Mouth|Quad|Sextant|Arch|ToothRange";
    private static final String BENEFIT_FIELDS = "CovCatNum, Percent, MonetaryAmt, TimePeriod(None|ServiceYear|CalendarYear|Lifetime|Years|NumberInLast12Months), "
            + "QuantityQualifier(None|NumberOfServices|AgeLimit|Visits|Years|Months), Quantity:number, CodeNum, procCode, CodeGroupNum, TreatArea(" + TREAT_AREA + "), ToothRange";
    private static final String PROC_CODE_FIELDS = "ProcTime, TreatArea(" + TREAT_AREA + "), NoBillIns, IsProsth, DefaultNote, IsHygiene, AlternateCode1, MedicalCode, "
            + "IsTaxed, PaintType, LaymanTerm, IsCanadianLab, BaseUnits:number, SubstitutionCode, SubstOnlyIf(Always|Molar|SecondMolar|Never|Posterior), DrugNDC, "
            + "RevenueCodeDefault, ProvNumDefault, CanadaTimeUnits:number, IsRadiology, DefaultClaimNote, DefaultTPNote, PaintText, AreaAlsoToothRange, DiagnosticCodes";
    private static final String LAB_FIELDS = "Phone, Notes, Slip, Address, City, State, Zip, Email, WirelessPhone, IsHidden";
    private static final String LABCASE_FIELDS = "AptNum, PlannedAptNum, DateTimeDue, DateTimeCreated, DateTimeSent, DateTimeRecd, DateTimeChecked, Instructions, LabFee, InvoiceNum:text";
    private static final String EMPLOYEE_FIELDS = "MiddleI, IsHidden, PayrollID, WirelessPhone, EmailWork, EmailPersonal, IsFurloughed, ReportsTo:number";
    private static final String REFERRAL_FIELDS = "FName, MName, SSN, UsingTIN, Specialty:number, ST, Telephone, Address, Address2, City, Zip, Note, Phone2, "
            + "NotPerson, Title, EMail, IsDoctor, BusinessName, DisplayNote";
    private static final String REFATTACH_FIELDS = "RefDate, ReferralType(RefTo|RefFrom|RefCustom), RefToStatus(None|Declined|Scheduled|Consulted|InTreatment|Complete), "
            + "Note, IsTransitionOfCare, ProcNum, DateProcComplete, ProvNum";
    private static final String INSPLAN_FIELDS = "GroupName, GroupNum:text, PlanNote, FeeSched:number, PlanType(|p|f|c), ClaimFormNum, ClaimsUseUCR, CopayFeeSched:number, EmployerNum, "
            + "AllowedFeeSched:number, IsMedical, FilingCode:number, ShowBaseUnits, CodeSubstNone, IsHidden, MonthRenew:number, FilingCodeSubtype:number, "
            + "CobRule(Basic|Standard|CarveOut|SecondaryMedicaid), BillingType:number";
    private static final String CLAIMPAYMENT_FIELDS = "CheckDate, CheckNum:text, BankBranch, Note, ClinicNum, CarrierName, DateIssued, PayType:number, PayGroup:number";
    private static final String DISCOUNT_PLAN_FIELDS = "IsHidden, PlanNote, ExamFreqLimit, XrayFreqLimit, ProphyFreqLimit, FluorideFreqLimit, PerioFreqLimit, "
            + "LimitedExamFreqLimit, PAFreqLimit, AnnualMax:number";
    private static final String PERIO_VALUES = "ToothValue:number, MBvalue:number, Bvalue:number, DBvalue:number, MLvalue:number, Lvalue:number, DLvalue:number";

    static final Map<String, WriteSpec> SPECS = specs(
            // Patients & families
            "allergies", "C: PatNum*, AllergyDefNum, defDescription, Reaction:textarea, StatusIsActive, DateAdverseReaction | U: Reaction:textarea, DateAdverseReaction, StatusIsActive | D",
            "allergydefs", "C: Description* | U: Description, IsHidden",
            "commlogs", "C: PatNum*, Note*, CommDateTime, CommType:number, Mode_(None|Email|Mail|Phone|In Person|Text|Email and Text|Phone and Text), SentOrReceived(Neither|Sent|Received) | U: Note*",
            "diseasedefs", "C: DiseaseName*",
            "diseases", "C: PatNum*, diseaseDefName, DiseaseDefNum, DateStart, DateStop, ProbStatus(Active|Resolved|Inactive), PatNote | U: DateStart, DateStop, ProbStatus(Active|Resolved|Inactive), PatNote | D",
            "ehrpatients", "U: DischargeDate, MedicaidState",
            "employers", "C: EmpName* | U: EmpName* | D",
            "guardians", "C: PatNumChild*, PatNumGuardian*, Relationship*(" + GUARDIAN_REL + "), IsGuardian | U: Relationship(" + GUARDIAN_REL + "), IsGuardian | D",
            "medicationpats", "C: PatNum*, MedicationNum*, PatNote, DateStart, DateStop, ProvNum | U: PatNote, DateStart, DateStop, ProvNum | D",
            "medications", "C: MedName*, GenericNum, genericName, Notes | U: Notes, IsHidden | D",
            "patientnotes", "U: FamFinancial, Medical, Service, MedicalComp, Treatment, ICEName, ICEPhone",
            "patfielddefs", "C: FieldName*, FieldType*(Text|PickList|Date|Checkbox|Currency|CareCreditStatus|CareCreditPreApprovalAmt|CareCreditAvailableCredit), PickList:textarea, IsHidden"
                    + " | U: FieldName, FieldType(Text|PickList|Date|Checkbox|Currency|CareCreditStatus|CareCreditPreApprovalAmt|CareCreditAvailableCredit), PickList:textarea, IsHidden | D",
            "patfields", "C: PatNum*, FieldName*, FieldValue* | U: PatNum*, FieldName*, FieldValue* | D",
            "patplans", "C: PatNum*, InsSubNum*, Ordinal, Relationship(" + PATREL + "), PatID | U: InsSubNum, Ordinal, Relationship(" + PATREL + "), PatID | D",
            "patrestrictions", "C: PatNum*, PatRestrictType*(ApptSchedule) | D",
            "popups", "C: PatNum*, Description*:textarea, PopupLevel(Patient|Family|SuperFamily), DateTimeDisabled | U: Description:textarea, PopupLevel(Patient|Family|SuperFamily), DateTimeDisabled",
            "recalls", "C: PatNum*, RecallTypeNum*, DateDue, RecallInterval, RecallStatus:number, Note, IsDisabled, DisableUntilBalance:number, DisableUntilDate, Priority(Normal|ASAP), TimePatternOverride"
                    + " | U: DateDue, RecallInterval, RecallStatus:number, Note, IsDisabled, DisableUntilBalance:number, DisableUntilDate, Priority(Normal|ASAP), TimePatternOverride",
            "refattaches", "C: PatNum*, ReferralNum, referralName, " + REFATTACH_FIELDS + " | U: ReferralNum, " + REFATTACH_FIELDS + " | D",
            "referrals", "C: LName*, PatNum, " + REFERRAL_FIELDS + " | U: LName, " + REFERRAL_FIELDS,
            "vitalsigns", "C: PatNum*, Height, Weight, BpSystolic:number, BpDiastolic:number, DateTaken, Documentation:textarea, Pulse"
                    + " | U: Height, Weight, BpSystolic:number, BpDiastolic:number, DateTaken, Documentation:textarea, Pulse | D",
            // Scheduling
            "appointmenttypes", "C: AppointmentTypeName*, appointmentTypeColor, IsHidden, Pattern, CodeStr, CodeStrRequired, RequiredProcCodesNeeded(None|AtLeastOne|All), BlockoutTypes"
                    + " | U: AppointmentTypeName, appointmentTypeColor, IsHidden, Pattern, CodeStr, CodeStrRequired, RequiredProcCodesNeeded(None|AtLeastOne|All), BlockoutTypes | D",
            "apptfields", "C: AptNum*, FieldName*, FieldValue* | U: FieldValue*, AptNum, FieldName | D",
            "apptfielddefs", "C: FieldName*, FieldType(Text|PickList), PickList:textarea | U: FieldName, FieldType(Text|PickList), PickList:textarea",
            "asapcomms", "C: op*:number, dateTimeStart*:datetime, aptNum:number, recallNum:number",
            // Clinical
            "autonotecontrols", "C: Descript*, ControlType*(Text|OneResponse|MultiResponse), ControlLabel*, ControlOptions:textarea | U: Descript, ControlType(Text|OneResponse|MultiResponse), ControlLabel, ControlOptions:textarea",
            "autonotes", "C: AutoNoteName*, MainText*:textarea, Category:number | U: AutoNoteName, MainText:textarea, Category:number",
            "codegroups", "C: GroupName*, ProcCodes, CodeGroupFixed(None|BW|PanoFMX|Exam|Perio|Prophy|SRP|FMDebride|Fluoride|Sealant), IsHidden, ShowInAgeLimit, ShowInFrequency, ShowInOther, ShowInHistory, HistProcCode, IsPerioFourQuads"
                    + " | U: GroupName, ItemOrder, ProcCodes, CodeGroupFixed(None|BW|PanoFMX|Exam|Perio|Prophy|SRP|FMDebride|Fluoride|Sealant), IsHidden, ShowInAgeLimit, ShowInFrequency, ShowInOther, ShowInHistory, HistProcCode, IsPerioFourQuads | D",
            "labcases", "C: PatNum*, LaboratoryNum*, ProvNum*, " + LABCASE_FIELDS + " | U: LaboratoryNum, ProvNum, " + LABCASE_FIELDS + " | D",
            "laboratories", "C: Description*, " + LAB_FIELDS + " | U: Description, " + LAB_FIELDS,
            "labturnarounds", "C: LaboratoryNum*, Description*, DaysActual*, DaysPublished | U: Description, DaysPublished, DaysActual",
            "perioexams", "C: PatNum*, ExamDate, ProvNum, Note | U: ExamDate, ProvNum, Note | D",
            "periomeasures", "C: PerioExamNum*, SequenceType*(Mobility|Furcation|GingMargin|MGJ|Probing|SkipTooth|BleedSupPlaqCalc), IntTooth*:number, " + PERIO_VALUES
                    + " | U: " + PERIO_VALUES + " | D",
            "procedurecodes", "C: ProcCode*, Descript*, AbbrDesc*, ProcCat*:number, " + PROC_CODE_FIELDS + " | U: Descript, AbbrDesc, ProcCat:number, " + PROC_CODE_FIELDS,
            "procnotes", "C: PatNum*, ProcNum*, Note*, isSigned, doAppendNote",
            "proctps", "U: Priority:number, ToothNumTP:text, Surf, ProcCode, Descript, FeeAmt:number, PriInsAmt:number, SecInsAmt:number, PatAmt:number, Discount:number, Prognosis, Dx, ProcAbbr, FeeAllowed:number | D",
            "sheets", "C: SheetDefNum*, PatNum*, InternalNote",
            "sheetfields", "U: FieldValue*",
            "toothinitials", "C: PatNum*, ToothNum*:text, InitialType*(Missing|Hidden|Primary|ShiftM|ShiftO|ShiftB|Rotate|TipM|TipB), Movement:number | D",
            "treatplanattaches", "C: TreatPlanNum*, ProcNum*, Priority:number | U: Priority:number",
            "treatplans", "C: PatNum*, Heading, Note, TPType(Insurance|Discount)"
                    + " | U: DateTP, Heading, Note, ResponsParty:number, TPType(Insurance|Discount), SignatureText, SignaturePracticeText, isSigned, isSignedPractice | D",
            // Insurance & billing
            "adjustments", "C: PatNum*, AdjType*:number, AdjAmt*:number, AdjDate*, ProvNum, ProcNum, ClinicNum, ProcDate, AdjNote | U: AdjDate, AdjAmt:number, AdjType:number, ProvNum, AdjNote, ProcNum, ClinicNum",
            "benefits", "C: PlanNum, PatPlanNum, BenefitType*(ActiveCoverage|CoInsurance|Deductible|CoPayment|Exclusions|Limitations|WaitingPeriod), CoverageLevel*(None|Individual|Family), " + BENEFIT_FIELDS
                    + " | U: BenefitType(ActiveCoverage|CoInsurance|Deductible|CoPayment|Exclusions|Limitations|WaitingPeriod), CoverageLevel(None|Individual|Family), " + BENEFIT_FIELDS + " | D",
            "carriers", "C: CarrierName*, Address, Address2, City, State, Zip, Phone, ElectID, NoSendElect(SendElect|NoSendElect|NoSendSecondaryElect), IsHidden"
                    + " | U: CarrierName, Address, Address2, City, State, Zip, Phone, ElectID, NoSendElect(SendElect|NoSendElect|NoSendSecondaryElect), IsHidden",
            "claimpayments", "C: claimNum*:number, CheckAmt*:number, " + CLAIMPAYMENT_FIELDS
                    + " | U: CheckAmt:number, CheckNum:text, BankBranch, Note, CarrierName, PayType:number, PayGroup:number | D",
            "claimprocs", "U: ProvNum, FeeBilled:number, DedApplied:number, Status(NotReceived|Received|Preauth|Supplemental|Estimate), InsPayAmt:number, Remarks, ClaimPaymentNum, WriteOff:number, "
                    + "CodeSent, PercentOverride:number, NoBillIns:number, CopayOverride:number, DedEstOverride:number, InsEstTotalOverride:number, PaidOtherInsOverride:number, WriteOffEstOverride:number, ClaimPaymentTracking:number | D",
            "claims", "C: PatNum*, procNums*:list, ClaimType*(P|S|PreAuth|Other), InsSubNum, PatRelat(" + PATREL + "), InsSubNum2, PatRelat2(" + PATREL + "), DateService, DateSent, ClaimForm:number, ProvTreat:number, ProvBill:number"
                    + " | U: ClaimStatus(U|H|W|S|R), DateSent, DateReceived, ProvTreat:number, IsProsthesis(N|I|R), PriorDate, ClaimNote, ReasonUnderPaid, ProvBill:number, PlaceService, "
                    + "AccidentRelated(No|A|E|O), AccidentDate, AccidentST, IsOrtho, OrthoRemainM:number, OrthoDate, PatRelat(" + PATREL + "), PatRelat2(" + PATREL + "), ClaimForm:number, "
                    + "InsSubNum2, PriorAuthorizationNumber, MedType(Dental|Medical|Institutional), OrthoTotalM:number | D",
            "claimtrackings", "C: ClaimNum*, Note, TrackingDefNum, TrackingErrorDefNum | U: Note, TrackingDefNum, TrackingErrorDefNum",
            "covcats", "C: Description*, DefaultPercent:number, IsHidden, EbenefitCat(None|General|Diagnostic|Periodontics|Restorative|Endodontics|MaxillofacialProsth|Crowns|Accident|Orthodontics|Prosthodontics|OralSurgery|RoutinePreventive|DiagnosticXRay|Adjunctive)"
                    + " | U: Description, DefaultPercent:number, CovOrder:number, IsHidden, EbenefitCat(None|General|Diagnostic|Periodontics|Restorative|Endodontics|MaxillofacialProsth|Crowns|Accident|Orthodontics|Prosthodontics|OralSurgery|RoutinePreventive|DiagnosticXRay|Adjunctive)",
            "covspans", "C: CovCatNum*, FromCode*, ToCode* | U: FromCode, ToCode | D",
            "deposits", "C: payNums:list, claimPaymentNums:list, DateDeposit, BankAccountInfo, Memo, Batch | U: DateDeposit, BankAccountInfo, Memo, Batch | D",
            "discountplans", "C: Description*, FeeSchedNum*, DefNum*, " + DISCOUNT_PLAN_FIELDS + " | U: Description, FeeSchedNum, DefNum, " + DISCOUNT_PLAN_FIELDS,
            "discountplansubs", "C: DiscountPlanNum*, PatNum*, DateEffective, DateTerm, SubNote | U: PatNum*, DateEffective, DateTerm, SubNote | D",
            "eobattaches", "D",
            "fees", "C: Amount*:number, FeeSched*:number, CodeNum*, ClinicNum, ProvNum | U: Amount*:number | D",
            "feescheds", "C: Description*, FeeSchedType*(Normal|CoPay|OutNetwork|FixedBenefit|ManualBlueBook), IsHidden, IsGlobal | U: Description, IsHidden, IsGlobal",
            "insplans", "C: CarrierNum*, " + INSPLAN_FIELDS + " | U: CarrierNum, " + INSPLAN_FIELDS,
            "inssubs", "C: PlanNum*, Subscriber*, SubscriberID*, DateEffective, DateTerm, BenefitNotes, ReleaseInfo, AssignBen, SubscNote"
                    + " | U: PlanNum, Subscriber, SubscriberID, DateEffective, DateTerm, BenefitNotes, ReleaseInfo, AssignBen, SubscNote | D",
            "insverifies", "U: VerifyType*(PatientEnrollment|InsuranceBenefit), FKey*:number, DateLastVerified, DefNum, Note",
            "payments", "C: PayAmt*:number, PatNum*, PayType:number, PayDate, CheckNum:text, PayNote, BankBranch, ClinicNum, isPatientPreferred, isPrepayment, isUnallocatedPrepayment, procNums:list, payPlanNum, MerchantFee:number"
                    + " | U: PayType:number, CheckNum:text, BankBranch, PayNote, ProcessStatus(OnlineProcessed|OnlinePending)",
            "payplancharges", "C: PayPlanNum*, ChargeDate*, Principal*:number, FKey*:number, LinkType*(Adjustment|Procedure|OrthoCase), Interest:number, Note | U: ChargeDate, Principal:number, Interest:number, Note | D",
            "payplanlinks", "C: PayPlanNum*, LinkType*(Procedure|Adjustment), FKey*:number, AmountOverride:number | U: AmountOverride:number | D",
            "paysplits", "U: ProvNum, ClinicNum",
            "statements", "C: PatNum*, DateSent, Note, DocNum | D",
            "substitutionlinks", "C: PlanNum*, CodeNum*, SubstitutionCode*, SubstOnlyIf*(Always|Molar|SecondMolar|Never|Posterior) | U: SubstitutionCode, SubstOnlyIf(Always|Molar|SecondMolar|Never|Posterior) | D",
            // Practice setup, tasks and staff
            "definitions", "C: category*, ItemName*, ItemValue, ItemColor, IsHidden, Supp:number | U: ItemOrder:number, ItemColor, IsHidden",
            "employees", "C: LName, FName, " + EMPLOYEE_FIELDS + " | U: LName, FName, " + EMPLOYEE_FIELDS,
            "providers", "C: Abbr*, LName, FName, MI, Suffix, FeeSched:number, Specialty:number, SSN, StateLicense, IsSecondary, IsHidden, UsingTIN, SigOnFile, NationalProvID, IsNotPerson, IsHiddenReport, BirthDate, SchedNote, PreferredName"
                    + " | U: Abbr, FName, LName, MI, Suffix, PreferredName, Specialty:number, SigOnFile, NationalProvId, StateLicense, SSN, UsingTIN",
            "tasknotes", "C: TaskNum*, UserNum*, Note* | U: DateTimeNote, Note",
            "tasks", "C: TaskListNum*, Descript*:textarea, UserNum*, KeyNum, ObjectType(None|Patient|Appointment), DateTimeEntry, PriorityDefNum, DescriptOverride, Category:number"
                    + " | U: Descript:textarea, TaskStatus(New|Viewed|Done), KeyNum, ObjectType(None|Patient|Appointment), DateTimeEntry, PriorityDefNum, DescriptOverride, Category:number",
            "subscriptions", "C: EndPointUrl*, Workstation, WatchTable, PollingSeconds:number, UiEventType(PatientSelected), DateTimeStart:datetime, DateTimeStop:datetime, Note"
                    + " | U: EndPointUrl, Workstation, PollingSeconds:number, DateTimeStart:datetime, DateTimeStop:datetime, Note | D",
            "userods", "C: UserName*, UserGroupNum*, Password*, IsPasswordResetRequired | U: userGroupNums:list, EmployeeNum, ProviderNum, ClinicNum, IsHidden, IsPasswordResetRequired");

    private OdWriteSpecs() {
    }

    static WriteSpec of(String resource) {
        return SPECS.get(resource);
    }

    private static Map<String, WriteSpec> specs(String... pairs) {
        Map<String, WriteSpec> map = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (map.put(pairs[i], WriteSpec.parse(pairs[i + 1])) != null) {
                throw new IllegalStateException("Duplicate write spec: " + pairs[i]);
            }
        }
        return Map.copyOf(map);
    }
}

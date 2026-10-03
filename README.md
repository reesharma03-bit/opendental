# Java API with Docker

A minimal Spring Boot Java REST API project with Docker support.

## Build

```bash
mvn package
```

## Run locally

```bash
mvn spring-boot:run
```

## Docker

Build image:

```bash
docker build -t java-api .
```

Run container:

```bash
docker run -p 8080:8080 java-api
```

## Available placeholder endpoints

- `GET /api/appointments`
- `GET /api/appointments/{aptNum}`
- `POST /api/appointments`
- `PUT /api/appointments/{aptNum}`
- `GET /api/patients`
- `GET /api/patients/Simple`
- `GET /api/patients/{patNum}`
- `POST /api/patients`
- `PUT /api/patients/{patNum}`
- `GET /api/documents`
- `GET /api/documents/{docNum}`
- `POST /api/documents/Upload`
- `POST /api/documents/UploadSftp`
- `POST /api/documents/DownloadSftp`
- `POST /api/documents/Thumbnails`
- `POST /api/documents/DownloadMount`
- `POST /api/documents/SetByUrl`
- `PUT /api/documents/{docNum}`
- `DELETE /api/documents/{docNum}`
- `POST /api/queries`
- `PUT /api/queries/ShortQuery`
- `GET /api/procedurelogs`
- `GET /api/procedurelogs/{procNum}`
- `POST /api/procedurelogs`
- `PUT /api/procedurelogs/{procNum}`
- `DELETE /api/procedurelogs/{procNum}`
- `GET /api/insurance`
- `GET /api/insurance/Simple`
- `GET /api/insurance/{insSubNum}`
- `POST /api/insurance`
- `PUT /api/insurance/{insSubNum}`
- `GET /api/comm`
- `GET /api/comm/{commNum}`
- `POST /api/comm`
- `PUT /api/comm/{commNum}`
- `DELETE /api/comm/{commNum}`
- `GET /api/readall`
- `GET /api/allothers`
- `GET /api/setup`
- `GET /api/enterprise`

## Scheduling resources

Implemented following the Open Dental API specification
(https://www.opendental.com/site/apispecification.html). Requests are validated
and forwarded to the configured Open Dental upstream (`opendental.base-url`).
JSON field names are passed through unchanged. `Appointments` is served by its
dedicated typed controller at `/api/appointments`.

- `GET /api/appointmenttypes`, `GET /api/appointmenttypes/{appointmentTypeNum}`
- `POST /api/appointmenttypes`, `PUT /api/appointmenttypes/{appointmentTypeNum}`, `DELETE /api/appointmenttypes/{appointmentTypeNum}`
- `GET /api/apptfields`, `GET /api/apptfields/{apptFieldNum}`
- `POST /api/apptfields`, `PUT /api/apptfields`, `PUT /api/apptfields/{apptFieldNum}`, `DELETE /api/apptfields/{apptFieldNum}`
- `GET /api/apptfielddefs`, `GET /api/apptfielddefs/{apptFieldDefNum}`, `POST /api/apptfielddefs`, `PUT /api/apptfielddefs/{apptFieldDefNum}`
- `GET /api/asapcomms`, `GET /api/asapcomms/{asapCommNum}`, `POST /api/asapcomms`
- `GET /api/clockevents`, `GET /api/clockevents/{clockEventNum}` (read-only)
- `GET /api/histappointments` (read-only)
- `GET /api/operatories`, `GET /api/operatories/{operatoryNum}` (read-only)
- `GET /api/scheduleops` (read-only)
- `GET /api/schedules`, `GET /api/schedules/{scheduleNum}` (read-only)

Read-only resources reject writes with `405 Method Not Allowed`; unsupported
query parameters and invalid identifiers are rejected with `400 Bad Request`
before any upstream call is made. Connectivity failures return `502 Bad Gateway`.

## Clinical Care resources

Implemented following the Open Dental API specification
(https://www.opendental.com/site/apispecification.html). Requests are validated
and forwarded to the configured Open Dental upstream (`opendental.base-url`).
JSON field names are passed through unchanged. `ProcedureLogs` is served by its
dedicated typed controller at `/api/procedurelogs`.

- `GET /api/autonotecontrols`, `POST /api/autonotecontrols`, `PUT /api/autonotecontrols/{autoNoteControlNum}`
- `GET /api/autonotes`, `POST /api/autonotes`, `PUT /api/autonotes/{autoNoteNum}`
- `GET /api/chartmodules/{patNum}/ProgNotes`, `GET /api/chartmodules/{patNum}/PatientInfo`, `GET /api/chartmodules/{patNum}/PlannedAppts` (read-only; `Offset` supported on `ProgNotes`)
- `GET /api/codegroups`, `GET /api/codegroups/{codeGroupNum}`, `POST /api/codegroups`, `PUT /api/codegroups/{codeGroupNum}`, `DELETE /api/codegroups/{codeGroupNum}`
- `GET /api/perioexams`, `GET /api/perioexams/{perioExamNum}`, `POST /api/perioexams`, `PUT /api/perioexams/{perioExamNum}`, `DELETE /api/perioexams/{perioExamNum}`
- `GET /api/periomeasures`, `POST /api/periomeasures`, `PUT /api/periomeasures/{perioMeasureNum}`, `DELETE /api/periomeasures/{perioMeasureNum}`
- `GET /api/procedurecodes`, `GET /api/procedurecodes/{codeNum}`, `POST /api/procedurecodes`, `PUT /api/procedurecodes/{codeNum}`
- `GET /api/procnotes`, `POST /api/procnotes`
- `GET /api/proctps` (requires `TreatPlanNum`), `PUT /api/proctps/{procTPNum}`, `DELETE /api/proctps/{procTPNum}`
- `GET /api/toothinitials`, `POST /api/toothinitials`, `PUT /api/toothinitials/ClearMovements`, `DELETE /api/toothinitials/{toothInitialNum}`
- `GET /api/treatplanattaches` (requires `TreatPlanNum`), `POST /api/treatplanattaches`, `PUT /api/treatplanattaches/{treatPlanAttachNum}`
- `GET /api/treatplans`, `POST /api/treatplans`, `PUT /api/treatplans/{treatPlanNum}`, `DELETE /api/treatplans/{treatPlanNum}`

Each resource exposes only the methods Open Dental documents for it. Undocumented
combinations return `405 Method Not Allowed`, undocumented paths return `404`, and
invalid identifiers, query parameters or request bodies are rejected with
`400 Bad Request` before any upstream call is made. Required create fields and
documented enumerations are enforced. Connectivity failures return `502 Bad Gateway`.

## Insurance & Billing resources

Implemented following the Open Dental API specification
(https://www.opendental.com/site/apispecification.html). Requests are validated
and forwarded to the configured Open Dental upstream (`opendental.base-url`).
JSON field names are passed through unchanged.

- `GET /api/benefits`, `GET /api/benefits/{benefitNum}`, `POST /api/benefits`, `PUT /api/benefits/{benefitNum}`, `DELETE /api/benefits/{benefitNum}`
- `GET /api/carriers`, `GET /api/carriers/{carrierNum}`, `POST /api/carriers`, `PUT /api/carriers/{carrierNum}`
- `GET /api/claimforms`, `GET /api/claimforms/{claimFormNum}` (read-only)
- `GET /api/claimpayments`, `GET /api/claimpayments/{claimPaymentNum}`, `POST /api/claimpayments`, `POST /api/claimpayments/Batch`, `PUT /api/claimpayments/{claimPaymentNum}`, `DELETE /api/claimpayments/{claimPaymentNum}`
- `GET /api/claimprocs`, `GET /api/claimprocs/{claimProcNum}`, `POST /api/claimprocs/Supplemental`, `POST /api/claimprocs/PendingSupplemental`, `DELETE /api/claimprocs/{claimProcNum}`
- `GET /api/claims`, `GET /api/claims/{claimNum}`, `POST /api/claims`, `PUT /api/claims/{claimNum}`, `PUT /api/claims/{claimNum}/Status`, `PUT /api/claims/{claimNum}/Split`, `DELETE /api/claims/{claimNum}`
- `GET /api/claimtrackings`, `POST /api/claimtrackings`, `PUT /api/claimtrackings/{claimTrackingNum}`
- `GET /api/covcats`, `GET /api/covcats/{covCatNum}`, `POST /api/covcats`, `PUT /api/covcats/{covCatNum}`
- `GET /api/covspans`, `GET /api/covspans/{covSpanNum}`, `POST /api/covspans`, `PUT /api/covspans/{covSpanNum}`, `DELETE /api/covspans/{covSpanNum}`
- `GET /api/deposits`, `GET /api/deposits/{depositNum}`, `POST /api/deposits`, `PUT /api/deposits/{depositNum}`, `DELETE /api/deposits/{depositNum}`
- `GET /api/discountplans`, `GET /api/discountplans/{discountPlanNum}`, `POST /api/discountplans`, `PUT /api/discountplans/{discountPlanNum}`
- `GET /api/discountplansubs` (requires `PatNum`), `POST /api/discountplansubs`, `PUT /api/discountplansubs/{discountSubNum}`, `DELETE /api/discountplansubs/{discountSubNum}`
- `GET /api/eobattaches` (requires `ClaimPaymentNum`), `POST /api/eobattaches/DownloadSftp`, `POST /api/eobattaches/UploadSftp`, `DELETE /api/eobattaches/{eobAttachNum}`
- `GET /api/fees`, `GET /api/fees/{feeNum}`, `POST /api/fees`, `PUT /api/fees/{feeNum}`, `DELETE /api/fees/{feeNum}`
- `GET /api/feescheds`, `POST /api/feescheds`, `PUT /api/feescheds/{feeSchedNum}`
- `GET /api/insplans`, `GET /api/insplans/{planNum}`, `POST /api/insplans`, `PUT /api/insplans/{planNum}`
- `GET /api/inssubs`, `GET /api/inssubs/{insSubNum}`, `POST /api/inssubs`, `PUT /api/inssubs/{insSubNum}`, `DELETE /api/inssubs/{insSubNum}`
- `GET /api/insverifies`, `GET /api/insverifies/{insVerifyNum}`, `PUT /api/insverifies`
- `GET /api/payments`, `POST /api/payments`, `PUT /api/payments/{payNum}`, `PUT /api/payments/{payNum}/Partial`
- `GET /api/payplancharges` (requires `PayPlanNum`), `POST /api/payplancharges`, `PUT /api/payplancharges/{payPlanChargeNum}`, `DELETE /api/payplancharges/{payPlanChargeNum}`
- `GET /api/payplanlinks`, `GET /api/payplanlinks/{payPlanLinkNum}`, `POST /api/payplanlinks`, `PUT /api/payplanlinks/{payPlanLinkNum}`, `DELETE /api/payplanlinks/{payPlanLinkNum}`
- `GET /api/payplans` (requires `PatNum` or `Guarantor`), `GET /api/payplans/{payPlanNum}`, `POST /api/payplans/Dynamic`, `PUT /api/payplans/{payPlanNum}/Close`, `PUT /api/payplans/{payPlanNum}/Dynamic`
- `GET /api/paysplits`, `PUT /api/paysplits/{splitNum}`
- `GET /api/statements`, `GET /api/statements/{statementNum}`, `POST /api/statements`, `DELETE /api/statements/{statementNum}`
- `GET /api/substitutionlinks` (requires `PlanNum`), `POST /api/substitutionlinks`, `PUT /api/substitutionlinks/{substitutionLinkNum}`, `DELETE /api/substitutionlinks/{substitutionLinkNum}`

Each resource exposes only the methods Open Dental documents for it. Undocumented
combinations return `405 Method Not Allowed`, undocumented paths return `404`, and
invalid identifiers, query parameters or request bodies are rejected with
`400 Bad Request` before any upstream call is made. Required create fields and
documented enumerations are enforced. `PUT /api/payplans/{payPlanNum}/Close` is
documented as taking no body. Connectivity failures return `502 Bad Gateway`.

## Notes

This project currently implements placeholder endpoints matching the requested Open Dental API categories. The API routes are ready for business logic and data integration.

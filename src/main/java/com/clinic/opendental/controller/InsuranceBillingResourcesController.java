package com.clinic.opendental.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Narrow, allowlisted adapter for the documented Insurance &amp; Billing API routes
 * that do not have a dedicated typed controller. It forwards Open Dental JSON field
 * names unchanged and deliberately exposes no arbitrary proxy operation.
 *
 * Resources covered here (see https://www.opendental.com/site/apispecification.html):
 * Benefits, Carriers, ClaimForms, ClaimPayments, ClaimProcs, Claims, ClaimTrackings,
 * CovCats, CovSpans, Deposits, DiscountPlans, DiscountPlanSubs, EobAttaches, Fees,
 * FeeScheds, InsPlans, InsSubs, InsVerifies, Payments, PayPlanCharges, PayPlanLinks,
 * PayPlans, PaySplits, Statements and SubstitutionLinks.
 *
 * The literal paths below are more specific than the {@code /api/{resource}}
 * pattern used by {@link PatientFamilyResourcesController}, so Spring routes
 * each request to the correct controller without an ambiguous mapping.
 *
 * A single {@code dispatch} method handles every covered route for this group.
 */
@RestController
@RequestMapping({
        "/api/benefits", "/api/benefits/**",
        "/api/carriers", "/api/carriers/**",
        "/api/claimforms", "/api/claimforms/**",
        "/api/claimpayments", "/api/claimpayments/**",
        "/api/claimprocs", "/api/claimprocs/**",
        "/api/claims", "/api/claims/**",
        "/api/claimtrackings", "/api/claimtrackings/**",
        "/api/covcats", "/api/covcats/**",
        "/api/covspans", "/api/covspans/**",
        "/api/deposits", "/api/deposits/**",
        "/api/discountplans", "/api/discountplans/**",
        "/api/discountplansubs", "/api/discountplansubs/**",
        "/api/eobattaches", "/api/eobattaches/**",
        "/api/fees", "/api/fees/**",
        "/api/feescheds", "/api/feescheds/**",
        "/api/insplans", "/api/insplans/**",
        "/api/inssubs", "/api/inssubs/**",
        "/api/insverifies", "/api/insverifies/**",
        "/api/payments", "/api/payments/**",
        "/api/payplancharges", "/api/payplancharges/**",
        "/api/payplanlinks", "/api/payplanlinks/**",
        "/api/payplans", "/api/payplans/**",
        "/api/paysplits", "/api/paysplits/**",
        "/api/statements", "/api/statements/**",
        "/api/substitutionlinks", "/api/substitutionlinks/**"})
public class InsuranceBillingResourcesController {

    private static final Set<String> RESOURCES = Set.of(
            "benefits", "carriers", "claimforms", "claimpayments", "claimprocs", "claims",
            "claimtrackings", "covcats", "covspans", "deposits", "discountplans",
            "discountplansubs", "eobattaches", "fees", "feescheds", "insplans", "inssubs",
            "insverifies", "payments", "payplancharges", "payplanlinks", "payplans",
            "paysplits", "statements", "substitutionlinks");

    private static final Map<String, Set<String>> COLLECTION_METHODS = Map.ofEntries(
            Map.entry("benefits", Set.of("GET", "POST")),
            Map.entry("carriers", Set.of("GET", "POST")),
            Map.entry("claimforms", Set.of("GET")),
            Map.entry("claimpayments", Set.of("GET", "POST")),
            Map.entry("claimprocs", Set.of("GET")),
            Map.entry("claims", Set.of("GET", "POST")),
            Map.entry("claimtrackings", Set.of("GET", "POST")),
            Map.entry("covcats", Set.of("GET", "POST")),
            Map.entry("covspans", Set.of("GET", "POST")),
            Map.entry("deposits", Set.of("GET", "POST")),
            Map.entry("discountplans", Set.of("GET", "POST")),
            Map.entry("discountplansubs", Set.of("GET", "POST")),
            Map.entry("eobattaches", Set.of("GET")),
            Map.entry("fees", Set.of("GET", "POST")),
            Map.entry("feescheds", Set.of("GET", "POST")),
            Map.entry("insplans", Set.of("GET", "POST")),
            Map.entry("inssubs", Set.of("GET", "POST")),
            Map.entry("insverifies", Set.of("GET", "PUT")),
            Map.entry("payments", Set.of("GET", "POST")),
            Map.entry("payplancharges", Set.of("GET", "POST")),
            Map.entry("payplanlinks", Set.of("GET", "POST")),
            Map.entry("payplans", Set.of("GET")),
            Map.entry("paysplits", Set.of("GET")),
            Map.entry("statements", Set.of("GET", "POST")),
            Map.entry("substitutionlinks", Set.of("GET", "POST")));

    private static final Map<String, Set<String>> ID_METHODS = Map.ofEntries(
            Map.entry("benefits", Set.of("GET", "PUT", "DELETE")),
            Map.entry("carriers", Set.of("GET", "PUT")),
            Map.entry("claimforms", Set.of("GET")),
            Map.entry("claimpayments", Set.of("GET", "PUT", "DELETE")),
            Map.entry("claimprocs", Set.of("GET", "DELETE")),
            Map.entry("claims", Set.of("GET", "PUT", "DELETE")),
            Map.entry("claimtrackings", Set.of("PUT")),
            Map.entry("covcats", Set.of("GET", "PUT")),
            Map.entry("covspans", Set.of("GET", "PUT", "DELETE")),
            Map.entry("deposits", Set.of("GET", "PUT", "DELETE")),
            Map.entry("discountplans", Set.of("GET", "PUT")),
            Map.entry("discountplansubs", Set.of("PUT", "DELETE")),
            Map.entry("eobattaches", Set.of("DELETE")),
            Map.entry("fees", Set.of("GET", "PUT", "DELETE")),
            Map.entry("feescheds", Set.of("PUT")),
            Map.entry("insplans", Set.of("GET", "PUT")),
            Map.entry("inssubs", Set.of("GET", "PUT", "DELETE")),
            Map.entry("insverifies", Set.of("GET")),
            Map.entry("payments", Set.of("PUT")),
            Map.entry("payplancharges", Set.of("PUT", "DELETE")),
            Map.entry("payplanlinks", Set.of("GET", "PUT", "DELETE")),
            Map.entry("payplans", Set.of("GET")),
            Map.entry("paysplits", Set.of("PUT")),
            Map.entry("statements", Set.of("GET", "DELETE")),
            Map.entry("substitutionlinks", Set.of("PUT", "DELETE")));

    /** Named sub-routes hanging directly off the collection, e.g. POST /claimpayments/Batch. */
    private static final Map<String, Map<String, Set<String>>> COLLECTION_NAMED = Map.ofEntries(
            Map.entry("claimpayments", Map.of("Batch", Set.of("POST"))),
            Map.entry("claimprocs", Map.of(
                    "Supplemental", Set.of("POST"),
                    "PendingSupplemental", Set.of("POST"))),
            Map.entry("eobattaches", Map.of(
                    "DownloadSftp", Set.of("POST"),
                    "UploadSftp", Set.of("POST"))),
            Map.entry("payplans", Map.of("Dynamic", Set.of("POST"))));

    /** Named sub-routes hanging off an id, e.g. PUT /claims/26/Status. */
    private static final Map<String, Map<String, Set<String>>> ID_NAMED = Map.ofEntries(
            Map.entry("claims", Map.of(
                    "Status", Set.of("PUT"),
                    "Split", Set.of("PUT"))),
            Map.entry("payments", Map.of("Partial", Set.of("PUT"))),
            Map.entry("payplans", Map.of(
                    "Close", Set.of("PUT"),
                    "Dynamic", Set.of("PUT"))));

    private static final Map<String, Set<String>> QUERY_FIELDS = Map.ofEntries(
            Map.entry("benefits", Set.of("PlanNum", "PatPlanNum")),
            Map.entry("carriers", Set.of()),
            Map.entry("claimforms", Set.of()),
            Map.entry("claimpayments", Set.of("SecDateTEdit")),
            Map.entry("claimprocs", Set.of("ProcNum", "ClaimNum", "PatNum", "Status",
                    "ClaimPaymentNum")),
            Map.entry("claims", Set.of("PatNum", "ClaimStatus", "ClaimType", "PlanNum",
                    "PlanNum2", "ClaimIdentifier", "SecDateTEdit")),
            Map.entry("claimtrackings", Set.of("ClaimNum")),
            Map.entry("covcats", Set.of()),
            Map.entry("covspans", Set.of("CovCatNum")),
            Map.entry("deposits", Set.of("DateDeposit")),
            Map.entry("discountplans", Set.of()),
            Map.entry("discountplansubs", Set.of("PatNum")),
            Map.entry("eobattaches", Set.of("ClaimPaymentNum")),
            Map.entry("fees", Set.of("FeeSched", "CodeNum", "ClinicNum", "ProvNum")),
            Map.entry("feescheds", Set.of()),
            Map.entry("insplans", Set.of("PlanType", "CarrierNum")),
            Map.entry("inssubs", Set.of("PlanNum", "Subscriber", "SecDateTEdit")),
            Map.entry("insverifies", Set.of("VerifyType", "FKey", "SecDateTEdit")),
            Map.entry("payments", Set.of("PayType", "PatNum", "DateEntry")),
            Map.entry("payplancharges", Set.of("PayPlanNum", "includeProjected")),
            Map.entry("payplanlinks", Set.of("PayPlanNum")),
            Map.entry("payplans", Set.of("PatNum", "Guarantor")),
            Map.entry("paysplits", Set.of("PatNum", "PayNum", "ProcNum")),
            Map.entry("statements", Set.of("PatNum")),
            Map.entry("substitutionlinks", Set.of("PlanNum")));

    /** Query parameters Open Dental documents as always required. */
    private static final Map<String, Set<String>> REQUIRED_QUERY = Map.ofEntries(
            Map.entry("discountplansubs", Set.of("PatNum")),
            Map.entry("eobattaches", Set.of("ClaimPaymentNum")),
            Map.entry("payplancharges", Set.of("PayPlanNum")),
            Map.entry("substitutionlinks", Set.of("PlanNum")));

    private static final Set<String> BENEFIT_TYPES = Set.of("ActiveCoverage", "CoInsurance",
            "Deductible", "CoPayment", "Exclusions", "Limitations", "WaitingPeriod");
    private static final Set<String> COVERAGE_LEVELS = Set.of("None", "Individual", "Family");
    private static final Set<String> TIME_PERIODS = Set.of("None", "ServiceYear", "CalendarYear",
            "Lifetime", "Years", "NumberInLast12Months");
    private static final Set<String> QUANTITY_QUALIFIERS = Set.of("None", "NumberOfServices",
            "AgeLimit", "Visits", "Years", "Months");
    private static final Set<String> TREAT_AREAS = Set.of("None", "Surf", "Tooth", "Mouth",
            "Quad", "Sextant", "Arch", "ToothRange");
    private static final Set<String> NO_SEND_ELECT = Set.of("SendElect", "NoSendElect",
            "NoSendSecondaryElect");
    private static final Set<String> EBENEFIT_CATS = Set.of("None", "General", "Diagnostic",
            "Periodontics", "Restorative", "Endodontics", "MaxillofacialProsth", "Crowns",
            "Accident", "Orthodontics", "Prosthodontics", "OralSurgery", "RoutinePreventive",
            "DiagnosticXRay", "Adjunctive");
    private static final Set<String> CLAIM_PROC_STATUSES = Set.of("NotReceived", "Received",
            "Preauth", "Adjustment", "Supplemental", "CapClaim", "Estimate", "CapComplete",
            "CapEstimate", "InsHist");
    private static final Set<String> CLAIM_STATUSES = Set.of("U", "H", "W", "S", "R", "I");
    private static final Set<String> CLAIM_TYPES = Set.of("P", "S", "PreAuth", "Cap", "Other");
    private static final Set<String> FEE_SCHED_TYPES = Set.of("Normal", "CoPay", "OutNetwork",
            "FixedBenefit", "ManualBlueBook");
    private static final Set<String> SUBST_ONLY_IF = Set.of("Always", "Molar", "SecondMolar",
            "Never", "Posterior");
    private static final Set<String> PLAN_TYPES = Set.of("percentage", "p", "f", "c");
    private static final Set<String> VERIFY_TYPES = Set.of("PatientEnrollment",
            "InsuranceBenefit");
    private static final Set<String> PROCESS_STATUSES = Set.of("OnlineProcessed",
            "OnlinePending");
    private static final Set<String> LINK_TYPES = Set.of("Procedure", "Adjustment");
    private static final Set<String> CHARGE_FREQUENCIES = Set.of("Weekly", "EveryOtherWeek",
            "Monthly", "Quarterly", "OrdinalWeekday");
    private static final Set<String> ORTHO_TYPES = Set.of("InitialClaimOnly",
            "InitialPlusVisit", "InitialPlusPeriodic");
    private static final Set<String> ORTHO_PROC_FREQS = Set.of("Monthly", "Quarterly",
            "SemiAnnual", "Annual");

    private static final List<String> NUMERIC_BODY_FIELDS = List.of("PlanNum", "PatPlanNum",
            "CovCatNum", "CodeNum", "CodeGroupNum", "Quantity", "CarrierNum", "FeeSched",
            "ClinicNum", "ProvNum", "ClaimNum", "ClaimPaymentNum", "ProcNum", "PatNum",
            "Subscriber", "FKey", "DefNum", "TrackingDefNum", "TrackingErrorDefNum", "UserNum",
            "ClaimFormNum", "ClaimForm", "CopayFeeSched", "AllowedFeeSched", "EmployerNum",
            "FilingCode", "FilingCodeSubtype", "MonthRenew", "BillingType", "PayType",
            "PayGroup", "PayNum", "DepositNum", "FeeSchedNum", "DefNum", "PayPlanNum",
            "DiscountPlanNum", "DiscountSubNum", "Guarantor", "NumberOfPayments",
            "PlanCategory", "SheetDefNum", "ProcNum", "PayPlanChargeNum", "AdjNum",
            "ClaimProcNum", "ProvTreat", "ProvBill", "InsSubNum", "InsSubNum2", "PlanNum2",
            "DocNum", "DocumentNum", "SplitNum", "StatementNum", "PayPlanChargeNum",
            "OrthoAutoProcCodeNumOverride", "OrthoAutoClaimDaysWait", "UnearnedType",
            "CustomTracking", "OrthoRemainM", "OrthoTotalM");

    private static final List<String> BOOLEAN_BODY_FIELDS = List.of("IsHidden", "IsGlobal",
            "IsMedical", "ShowBaseUnits", "CodeSubstNone", "ClaimsUseUCR", "IsPartial",
            "IsSent", "IsSentToQuickBooksOnline", "ReleaseInfo", "AssignBen", "isPatientPreferred",
            "isPrepayment", "isUnallocatedPrepayment", "IsRecurringCC", "IsCcCompleted",
            "IsProsthesis", "IsOrtho", "HasPpoSubstWriteoffs", "IsBlueBookEnabled",
            "includeProjected", "toSupplemental", "IsDownPayment", "IsOffset");

    private static final List<String> DATE_BODY_FIELDS = List.of("DateEffective", "DateTerm",
            "DateDeposit", "ChargeDate", "DateLastVerified", "DateLastAssigned", "DateSent",
            "DateEntry", "DateService", "DateReceived", "DateCP", "ProcDate", "PayDate",
            "DatePay", "DateEntry", "CheckDate", "DateIssued", "DatePayPlanStart",
            "DateInterestStart", "PayPlanDate", "SecDateEntry", "DateRangeFrom", "DateRangeTo",
            "DateOrigProsth", "DateOriginalProsth");

    private static final List<String> DATE_TIME_BODY_FIELDS = List.of("SecDateTEdit",
            "SecDateTEntry", "DateTStamp");

    @Value("${opendental.base-url}")
    private String baseUrl;
    private final RestTemplate restTemplate;

    public InsuranceBillingResourcesController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @RequestMapping
    public ResponseEntity<?> dispatch(HttpServletRequest request,
                                      @RequestBody(required = false) Map<String, Object> body) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        if (!uri.startsWith("/api/")) return error(HttpStatus.NOT_FOUND, "Resource is not available.");

        String rest = uri.substring("/api/".length());
        int slash = rest.indexOf('/');
        String name = (slash < 0 ? rest : rest.substring(0, slash)).toLowerCase(Locale.ROOT);
        if (!RESOURCES.contains(name)) return error(HttpStatus.NOT_FOUND, "Resource is not available.");

        String tail = slash < 0 ? "" : rest.substring(slash + 1);
        while (tail.endsWith("/")) tail = tail.substring(0, tail.length() - 1);
        String route = tail.isEmpty() ? "collection" : tail;
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        Route resolved = resolve(name, route, method);
        if (resolved == null) {
            if (isKnownRoute(name, route)) {
                return error(HttpStatus.METHOD_NOT_ALLOWED, "Method is not supported for this route.");
            }
            return error(HttpStatus.NOT_FOUND, "Route is not documented for this resource.");
        }
        if (resolved.id != null && !positive(resolved.id)) {
            return error(HttpStatus.BAD_REQUEST, "Resource ID must be a positive integer.");
        }

        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values.length > 0) params.put(key, values[0]);
        });
        String validation = validateQuery(name, resolved, params);
        if (validation != null) return error(HttpStatus.BAD_REQUEST, validation);
        validation = validateBody(name, resolved, body);
        if (validation != null) return error(HttpStatus.BAD_REQUEST, validation);

        UriComponentsBuilder target = UriComponentsBuilder.fromHttpUrl(
                baseUrl.replaceAll("/+$", "") + resolved.upstreamPath);
        params.forEach(target::queryParam);
        HttpHeaders headers = new HttpHeaders();
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<Object> upstream = restTemplate.exchange(
                    target.build().encode().toUri(),
                    method,
                    new HttpEntity<>(body, headers),
                    Object.class);
            ResponseEntity.BodyBuilder response = ResponseEntity.status(upstream.getStatusCode());
            return upstream.getBody() == null ? response.build() : response.body(upstream.getBody());
        } catch (HttpStatusCodeException ex) {
            ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.getStatusCode());
            MediaType contentType = ex.getResponseHeaders() == null
                    ? null : ex.getResponseHeaders().getContentType();
            if (contentType != null) response.contentType(contentType);
            byte[] raw = ex.getResponseBodyAsByteArray();
            if (raw.length > 0) {
                return response.body(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
            }
            return response.build();
        } catch (ResourceAccessException ex) {
            return error(HttpStatus.BAD_GATEWAY, "Open Dental connectivity failure.");
        }
    }

    private Route resolve(String resource, String route, HttpMethod method) {
        if (route.equals("collection")) {
            if (!COLLECTION_METHODS.get(resource).contains(method.name())) return null;
            return new Route("collection", "/" + resource, null, method.name(), null);
        }
        if (route.matches("[0-9]+")) {
            if (!ID_METHODS.get(resource).contains(method.name())) return null;
            return new Route("id", "/" + resource + "/" + route, route, method.name(), null);
        }
        Map<String, Set<String>> collectionNamed = COLLECTION_NAMED.getOrDefault(resource, Map.of());
        for (Map.Entry<String, Set<String>> entry : collectionNamed.entrySet()) {
            if (route.equalsIgnoreCase(entry.getKey()) && entry.getValue().contains(method.name())) {
                return new Route("action", "/" + resource + "/" + entry.getKey(), null,
                        method.name(), entry.getKey());
            }
        }
        int sep = route.indexOf('/');
        if (sep > 0) {
            String id = route.substring(0, sep);
            String named = route.substring(sep + 1);
            Map<String, Set<String>> idNamed = ID_NAMED.getOrDefault(resource, Map.of());
            for (Map.Entry<String, Set<String>> entry : idNamed.entrySet()) {
                if (id.matches("[0-9]+") && named.equalsIgnoreCase(entry.getKey())
                        && entry.getValue().contains(method.name())) {
                    return new Route("action", "/" + resource + "/" + id + "/" + entry.getKey(),
                            id, method.name(), entry.getKey());
                }
            }
        }
        return null;
    }

    private boolean isKnownRoute(String resource, String route) {
        if (route.equals("collection") || route.matches("[0-9]+")) return true;
        for (String named : COLLECTION_NAMED.getOrDefault(resource, Map.of()).keySet()) {
            if (route.equalsIgnoreCase(named)) return true;
        }
        int sep = route.indexOf('/');
        if (sep <= 0) return false;
        if (!route.substring(0, sep).matches("[0-9]+")) return false;
        for (String named : ID_NAMED.getOrDefault(resource, Map.of()).keySet()) {
            if (route.substring(sep + 1).equalsIgnoreCase(named)) return true;
        }
        return false;
    }

    private String validateQuery(String resource, Route route, Map<String, String> params) {
        Set<String> allowed = QUERY_FIELDS.get(resource);
        for (String key : params.keySet()) {
            if (!allowed.contains(key)) return "Unsupported query parameter: " + key;
            if (!route.kind.equals("collection")) {
                return "Query parameters are not supported for this route.";
            }
        }
        if (route.kind.equals("collection")) {
            for (String key : REQUIRED_QUERY.getOrDefault(resource, Set.of())) {
                if (!params.containsKey(key)) return key + " is required.";
            }
            if (resource.equals("payplans") && !params.containsKey("PatNum")
                    && !params.containsKey("Guarantor")) {
                return "PatNum or Guarantor is required.";
            }
            if (resource.equals("insverifies") && params.containsKey("FKey")
                    && !params.containsKey("VerifyType")) {
                return "VerifyType is required when FKey is specified.";
            }
        }
        for (String key : List.of("PlanNum", "PatPlanNum", "ClaimNum", "PatNum", "ProcNum",
                "ClaimPaymentNum", "CovCatNum", "FeeSched", "CodeNum", "ClinicNum", "ProvNum",
                "CarrierNum", "PlanNum2", "Subscriber", "FKey", "PayType", "PayNum",
                "PayPlanNum", "Guarantor", "DocNum", "UnearnedType")) {
            if (params.containsKey(key) && !nonNegative(params.get(key))) {
                return key + " must be a non-negative integer.";
            }
        }
        for (String key : List.of("SecDateTEdit", "DateEntry")) {
            if (params.containsKey(key) && !validDateTime(params.get(key))) {
                return key + " must use yyyy-MM-dd HH:mm:ss format.";
            }
        }
        if (params.containsKey("DateDeposit") && !validDate(params.get("DateDeposit"))) {
            return "DateDeposit must use yyyy-MM-dd format.";
        }
        if (params.containsKey("Status") && !CLAIM_PROC_STATUSES.contains(params.get("Status"))) {
            return "Status is not a documented claimproc status.";
        }
        if (params.containsKey("ClaimStatus") && !CLAIM_STATUSES.contains(params.get("ClaimStatus"))) {
            return "ClaimStatus must be U, H, W, S, R, or I.";
        }
        if (params.containsKey("ClaimType") && !CLAIM_TYPES.contains(params.get("ClaimType"))) {
            return "ClaimType must be P, S, PreAuth, Cap, or Other.";
        }
        if (params.containsKey("PlanType") && !PLAN_TYPES.contains(params.get("PlanType"))) {
            return "PlanType must be percentage, p, f, or c.";
        }
        if (params.containsKey("VerifyType") && !VERIFY_TYPES.contains(params.get("VerifyType"))) {
            return "VerifyType must be PatientEnrollment or InsuranceBenefit.";
        }
        if (params.containsKey("includeProjected") && !bool(params.get("includeProjected"))) {
            return "includeProjected must be true or false.";
        }
        return null;
    }

    private String validateBody(String resource, Route route, Map<String, Object> body) {
        boolean write = route.method.equals("POST")
                || (route.method.equals("PUT") && !isBodylessAction(route));
        if (write && (body == null || body.isEmpty())) {
            return "A JSON request body is required.";
        }
        if (body == null) return null;

        if (route.method.equals("POST")
                && (route.kind.equals("collection") || route.kind.equals("action"))) {
            String required = requiredOnCreate(resource, route.action, body);
            if (required != null) return required;
        }

        String enumError = validateEnums(body);
        if (enumError != null) return enumError;

        if (body.containsKey("FromCode") && body.containsKey("ToCode")
                && String.valueOf(body.get("FromCode"))
                .compareTo(String.valueOf(body.get("ToCode"))) >= 0) {
            return "FromCode must be alphabetically less than ToCode.";
        }
        if (body.containsKey("Batch") && String.valueOf(body.get("Batch")).length() > 25) {
            return "Batch cannot exceed 25 characters.";
        }
        for (String key : NUMERIC_BODY_FIELDS) {
            if (body.containsKey(key) && !nonNegative(String.valueOf(body.get(key)))) {
                return key + " must be a non-negative integer.";
            }
        }
        for (String key : BOOLEAN_BODY_FIELDS) {
            if (body.containsKey(key) && !bool(String.valueOf(body.get(key)))) {
                return key + " must be true or false.";
            }
        }
        for (String key : DATE_BODY_FIELDS) {
            if (body.containsKey(key) && !validDate(String.valueOf(body.get(key)))) {
                return key + " must use yyyy-MM-dd format.";
            }
        }
        for (String key : DATE_TIME_BODY_FIELDS) {
            if (body.containsKey(key) && !validDateTime(String.valueOf(body.get(key)))) {
                return key + " must use yyyy-MM-dd HH:mm:ss format.";
            }
        }
        return null;
    }

    /** PUT actions that Open Dental documents as taking no request body at all. */
    private boolean isBodylessAction(Route route) {
        return route.kind.equals("action") && "Close".equals(route.action);
    }

    private String validateEnums(Map<String, Object> body) {
        if (body.containsKey("BenefitType") && !BENEFIT_TYPES.contains(body.get("BenefitType"))) {
            return "BenefitType is not a documented benefit type.";
        }
        if (body.containsKey("CoverageLevel") && !COVERAGE_LEVELS.contains(body.get("CoverageLevel"))) {
            return "CoverageLevel must be None, Individual, or Family.";
        }
        if (body.containsKey("TimePeriod") && !TIME_PERIODS.contains(body.get("TimePeriod"))) {
            return "TimePeriod is not a documented time period.";
        }
        if (body.containsKey("QuantityQualifier")
                && !QUANTITY_QUALIFIERS.contains(body.get("QuantityQualifier"))) {
            return "QuantityQualifier must be None, NumberOfServices, AgeLimit, Visits, Years, or Months.";
        }
        if ("WaitingPeriod".equals(body.get("BenefitType")) && body.containsKey("QuantityQualifier")
                && !Set.of("Months", "Years").contains(body.get("QuantityQualifier"))) {
            return "QuantityQualifier must be Months or Years for a WaitingPeriod benefit.";
        }
        if ("CoInsurance".equals(body.get("BenefitType")) && body.containsKey("Percent")
                && !percentage(body.get("Percent"))) {
            return "Percent must be between 0 and 100 for a CoInsurance benefit.";
        }
        if (body.containsKey("TreatArea") && !TREAT_AREAS.contains(body.get("TreatArea"))) {
            return "TreatArea is not a documented treat area.";
        }
        if (body.containsKey("NoSendElect") && !NO_SEND_ELECT.contains(body.get("NoSendElect"))) {
            return "NoSendElect must be SendElect, NoSendElect, or NoSendSecondaryElect.";
        }
        if (body.containsKey("EbenefitCat") && !EBENEFIT_CATS.contains(body.get("EbenefitCat"))) {
            return "EbenefitCat is not a documented electronic benefit category.";
        }
        if (body.containsKey("Status") && !CLAIM_PROC_STATUSES.contains(body.get("Status"))) {
            return "Status is not a documented claimproc status.";
        }
        if (body.containsKey("ClaimStatus") && !CLAIM_STATUSES.contains(body.get("ClaimStatus"))) {
            return "ClaimStatus must be U, H, W, S, R, or I.";
        }
        if (body.containsKey("ClaimType") && !CLAIM_TYPES.contains(body.get("ClaimType"))) {
            return "ClaimType must be P, S, PreAuth, Cap, or Other.";
        }
        if (body.containsKey("FeeSchedType") && !FEE_SCHED_TYPES.contains(body.get("FeeSchedType"))) {
            return "FeeSchedType must be Normal, CoPay, OutNetwork, FixedBenefit, or ManualBlueBook.";
        }
        if (body.containsKey("SubstOnlyIf") && !SUBST_ONLY_IF.contains(body.get("SubstOnlyIf"))) {
            return "SubstOnlyIf must be Always, Molar, SecondMolar, Never, or Posterior.";
        }
        if (body.containsKey("VerifyType") && !VERIFY_TYPES.contains(body.get("VerifyType"))) {
            return "VerifyType must be PatientEnrollment or InsuranceBenefit.";
        }
        if (body.containsKey("ProcessStatus") && !PROCESS_STATUSES.contains(body.get("ProcessStatus"))) {
            return "ProcessStatus must be OnlineProcessed or OnlinePending.";
        }
        if (body.containsKey("LinkType") && !LINK_TYPES.contains(body.get("LinkType"))) {
            return "LinkType must be Procedure or Adjustment.";
        }
        if (body.containsKey("ChargeFrequency")
                && !CHARGE_FREQUENCIES.contains(body.get("ChargeFrequency"))) {
            return "ChargeFrequency must be Weekly, EveryOtherWeek, Monthly, Quarterly, or OrdinalWeekday.";
        }
        if (body.containsKey("OrthoType") && !ORTHO_TYPES.contains(body.get("OrthoType"))) {
            return "OrthoType must be InitialClaimOnly, InitialPlusVisit, or InitialPlusPeriodic.";
        }
        if (body.containsKey("OrthoAutoProcFreq")
                && !ORTHO_PROC_FREQS.contains(body.get("OrthoAutoProcFreq"))) {
            return "OrthoAutoProcFreq must be Monthly, Quarterly, SemiAnnual, or Annual.";
        }
        return null;
    }

    private String requiredOnCreate(String resource, String action, Map<String, Object> body) {
        if (action != null) {
            switch (resource + "/" + action) {
                case "claimpayments/Batch" -> {
                    if (!(body.get("claimNums") instanceof List<?> claimNums) || claimNums.isEmpty()) {
                        return "claimNums is required.";
                    }
                    if (body.get("CheckAmt") == null) return "CheckAmt is required.";
                }
                case "claimprocs/Supplemental" -> {
                    if (!body.containsKey("ClaimProcNum")) return "ClaimProcNum is required.";
                }
                case "claimprocs/PendingSupplemental" -> {
                    if (!body.containsKey("ClaimProcNum")) return "ClaimProcNum is required.";
                    if (body.get("insOverpay") == null && body.get("insUnderpay") == null) {
                        return "insOverpay or insUnderpay is required.";
                    }
                }
                case "eobattaches/DownloadSftp" -> {
                    String error = requireNonBlank(body, "EobAttachNum", "SftpAddress",
                            "SftpUsername", "SftpPassword");
                    if (error != null) return error;
                }
                case "eobattaches/UploadSftp" -> {
                    String error = requireNonBlank(body, "ClaimPaymentNum", "SftpAddress",
                            "SftpUsername", "SftpPassword");
                    if (error != null) return error;
                }
                default -> {
                    return null;
                }
            }
            return null;
        }
        switch (resource) {
            case "benefits" -> {
                if (!body.containsKey("PlanNum") && !body.containsKey("PatPlanNum")) {
                    return "PlanNum or PatPlanNum is required.";
                }
                if (!body.containsKey("BenefitType")) return "BenefitType is required.";
                if (!body.containsKey("CoverageLevel")) return "CoverageLevel is required.";
            }
            case "carriers" -> {
                String error = requireNonBlank(body, "CarrierName");
                if (error != null) return error;
            }
            case "claimpayments" -> {
                if (!body.containsKey("claimNum")) return "claimNum is required.";
                if (body.get("CheckAmt") == null) return "CheckAmt is required.";
            }
            case "claimtrackings" -> {
                if (!body.containsKey("ClaimNum")) return "ClaimNum is required.";
            }
            case "claims" -> {
                String error = requireNonBlank(body, "PatNum");
                if (error != null) return error;
            }
            case "covcats" -> {
                String error = requireNonBlank(body, "Description");
                if (error != null) return error;
            }
            case "covspans" -> {
                if (!body.containsKey("CovCatNum")) return "CovCatNum is required.";
                String error = requireNonBlank(body, "FromCode", "ToCode");
                if (error != null) return error;
            }
            case "deposits" -> {
                boolean hasPay = body.get("payNums") instanceof List<?> pay && !pay.isEmpty();
                boolean hasClaim = body.get("claimPaymentNums") instanceof List<?> claims
                        && !claims.isEmpty();
                if (!hasPay && !hasClaim) return "payNums or claimPaymentNums is required.";
            }
            case "discountplans" -> {
                if (!body.containsKey("Description")) return "Description is required.";
                if (!body.containsKey("FeeSchedNum")) return "FeeSchedNum is required.";
                if (!body.containsKey("DefNum")) return "DefNum is required.";
            }
            case "discountplansubs" -> {
                if (!body.containsKey("DiscountPlanNum")) return "DiscountPlanNum is required.";
                if (!body.containsKey("PatNum")) return "PatNum is required.";
            }
            case "fees" -> {
                if (body.get("Amount") == null) return "Amount is required.";
                if (!body.containsKey("FeeSched")) return "FeeSched is required.";
                if (!body.containsKey("CodeNum")) return "CodeNum is required.";
            }
            case "feescheds" -> {
                String error = requireNonBlank(body, "Description", "FeeSchedType");
                if (error != null) return error;
            }
            case "inssubs" -> {
                if (!body.containsKey("PlanNum")) return "PlanNum is required.";
                if (!body.containsKey("Subscriber")) return "Subscriber is required.";
                String error = requireNonBlank(body, "SubscriberID");
                if (error != null) return error;
            }
            case "payments" -> {
                if (body.get("PayAmt") == null) return "PayAmt is required.";
                if (!body.containsKey("PatNum")) return "PatNum is required.";
            }
            case "payplancharges" -> {
                if (!body.containsKey("PayPlanNum")) return "PayPlanNum is required.";
                if (!body.containsKey("ChargeDate")) return "ChargeDate is required.";
                if (body.get("Principal") == null) return "Principal is required.";
                if (!body.containsKey("FKey")) return "FKey is required.";
                if (!body.containsKey("LinkType")) return "LinkType is required.";
            }
            case "payplanlinks" -> {
                if (!body.containsKey("PayPlanNum")) return "PayPlanNum is required.";
                if (!body.containsKey("LinkType")) return "LinkType is required.";
                if (!body.containsKey("FKey")) return "FKey is required.";
            }
            case "statements" -> {
                if (!body.containsKey("PatNum")) return "PatNum is required.";
            }
            case "substitutionlinks" -> {
                if (!body.containsKey("PlanNum")) return "PlanNum is required.";
                if (!body.containsKey("CodeNum")) return "CodeNum is required.";
                String error = requireNonBlank(body, "SubstitutionCode", "SubstOnlyIf");
                if (error != null) return error;
            }
            default -> {
                return null;
            }
        }
        return null;
    }

    private String requireNonBlank(Map<String, Object> body, String... fields) {
        for (String field : fields) {
            Object value = body.get(field);
            if (value == null || blank(String.valueOf(value))) return field + " is required.";
        }
        return null;
    }

    private boolean percentage(Object value) {
        try {
            double parsed = Double.parseDouble(value.toString());
            return parsed >= 0 && parsed <= 100;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean positive(Object value) {
        if (value == null) return false;
        try { return Long.parseLong(value.toString()) > 0; }
        catch (NumberFormatException ignored) { return false; }
    }

    private boolean nonNegative(String value) {
        try { return Long.parseLong(value) >= 0; }
        catch (Exception ignored) { return false; }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private boolean bool(String value) { return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value); }

    private boolean validDate(String value) {
        try { LocalDate.parse(value); return value.matches("\\d{4}-\\d{2}-\\d{2}"); }
        catch (DateTimeParseException ignored) { return false; }
    }

    private boolean validDateTime(String value) {
        try {
            LocalDateTime.parse(value.replace(' ', 'T'));
            return value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        } catch (DateTimeParseException ignored) { return false; }
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    private record Route(String kind, String upstreamPath, String id, String method, String action) {}
}
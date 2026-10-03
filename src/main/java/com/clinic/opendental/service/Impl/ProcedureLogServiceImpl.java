package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.procedurelog.*;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.model.ProcedureLog;
import com.clinic.opendental.model.ProcedureLogId;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.repository.ProcedureLogRepository;
import com.clinic.opendental.service.ProcedureLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcedureLogServiceImpl implements ProcedureLogService {

    private final OpenDentalClient client;
    private final ProcedureLogRepository procedureLogRepository;
    private final ClinicRepository clinicRepository;
    private final OdSyncService odSync;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional(readOnly = true)
    public ProcedureLogResponse getProcedureLog(Long procNum) {
        try {
            ProcedureLogResponse apiResponse = client.getProcedureLog(procNum);
            saveProcedureLogToDb(apiResponse);
            return apiResponse;
        } catch (Exception e) {
            log.warn("OpenDental API unavailable for procedurelog {}, falling back to database: {}", procNum, e.getMessage());
            UUID clinicId = resolveClinicId();
            ProcedureLog procedureLog = procedureLogRepository.findById(new ProcedureLogId(clinicId, procNum))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "ProcedureLog not found with ProcNum: " + procNum));
            return toProcedureLogResponse(procedureLog);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcedureLogResponse> getProcedureLogs(Map<String, String> params) {
        try {
            List<ProcedureLogResponse> apiResponses = client.getProcedureLogs(params);
            syncProcedureLogsToDb(apiResponses);
            return apiResponses;
        } catch (Exception e) {
            log.warn("OpenDental API unavailable for procedurelogs, falling back to database: {}", e.getMessage());
            return procedureLogRepository.findAll().stream()
                    .map(this::toProcedureLogResponse)
                    .collect(Collectors.toList());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcedureLogResponse> getProcedureLogsFromDatabase(Long patNum) {
        return procedureLogRepository.findByIdClinicId(resolveClinicId()).stream()
                .filter(p -> patNum == null || patNum.equals(p.getPatNum()))
                .sorted(java.util.Comparator.comparing(p -> p.getId().getProcNum()))
                .map(this::toProcedureLogResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InsuranceHistoryResponse> getInsuranceHistory(Long patNum, Long insSubNum) {
        return client.getInsuranceHistory(patNum, insSubNum);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupNoteResponse> getGroupNotes(Long patNum) {
        return client.getGroupNotes(patNum);
    }

    // Create, insurance history, update and delete go to our database first and are then
    // pushed to Open Dental (right away when it is reachable, otherwise from the retry
    // queue). A new procedure has a temporary negative ProcNum until Open Dental assigns one.

    @Override
    public ProcedureLogResponse createProcedureLog(CreateProcedureLogRequest request) {
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.PATIENT, request.getPatNum());
        Long aptNum = request.getAptNum() != null && request.getAptNum() != 0L ? request.getAptNum() : null;
        if (aptNum != null) {
            odSync.ensureStored(clinicId, OdSyncService.APPOINTMENT, aptNum);
        }
        long taskId = odSync.recordCreate(clinicId, OdSyncService.PROCEDURE_LOG, OdSyncService.CREATE, request,
                procNum -> {
                    ProcedureLog procedureLog = ProcedureLog.builder()
                            .id(new ProcedureLogId(clinicId, procNum))
                            .patNum(request.getPatNum())
                            .dxName(request.getDxName())
                            .build();
                    applyRequest(procedureLog, OdSyncService.convert(request, UpdateProcedureLogRequest.class));
                    procedureLogRepository.save(procedureLog);
                });
        return loadSavedProcedureLog(clinicId, odSync.pushNow(taskId));
    }

    @Override
    @Transactional
    public GroupNoteResponse createGroupNote(GroupNoteRequest request) {
        try {
            return client.createGroupNote(request);
        } catch (Exception e) {
            log.error("Failed to create group note: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create group note: " + e.getMessage());
        }
    }

    @Override
    public ProcedureLogResponse createInsuranceHistory(InsuranceHistoryRequest request) {
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.PATIENT, request.getPatNum());
        long taskId = odSync.recordCreate(clinicId, OdSyncService.PROCEDURE_LOG, OdSyncService.INSURANCE_HISTORY,
                request, procNum -> procedureLogRepository.save(ProcedureLog.builder()
                        .id(new ProcedureLogId(clinicId, procNum))
                        .patNum(request.getPatNum())
                        .procDate(LocalValues.date(request.getProcDate()))
                        .procStatus("EO")
                        .build()));
        return loadSavedProcedureLog(clinicId, odSync.pushNow(taskId));
    }

    @Override
    public ProcedureLogResponse updateProcedureLog(Long procNum, UpdateProcedureLogRequest request) {
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.PROCEDURE_LOG, procNum);
        if (request.getAptNum() != null && request.getAptNum() != 0L) {
            odSync.ensureStored(clinicId, OdSyncService.APPOINTMENT, request.getAptNum());
        }
        long taskId = odSync.recordChange(clinicId, OdSyncService.PROCEDURE_LOG, OdSyncService.UPDATE, procNum,
                request, () -> {
                    ProcedureLog procedureLog = procedureLogRepository.findById(new ProcedureLogId(clinicId, procNum))
                            .orElseThrow(() -> procedureLogNotFound(procNum));
                    applyRequest(procedureLog, request);
                    procedureLogRepository.save(procedureLog);
                });
        return loadSavedProcedureLog(clinicId, odSync.pushNow(taskId));
    }

    @Override
    @Transactional
    public GroupNoteResponse updateGroupNote(Long procNum, UpdateGroupNoteRequest request) {
        try {
            return client.updateGroupNote(procNum, request);
        } catch (Exception e) {
            log.error("Failed to update group note: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update group note: " + e.getMessage());
        }
    }

    @Override
    public void deleteProcedureLog(Long procNum) {
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.PROCEDURE_LOG, procNum);
        Long taskId = odSync.recordDelete(clinicId, OdSyncService.PROCEDURE_LOG, procNum,
                () -> procedureLogRepository.deleteById(new ProcedureLogId(clinicId, procNum)));
        if (taskId != null) {
            odSync.pushNow(taskId);
        }
    }

    private static void applyRequest(ProcedureLog p, UpdateProcedureLogRequest r) {
        if (r.getAptNum() != null) p.setAptNum(r.getAptNum() != 0L ? r.getAptNum() : null);
        if (r.getProcDate() != null) p.setProcDate(LocalValues.date(r.getProcDate()));
        if (r.getProcFee() != null) p.setProcFee(LocalValues.decimal(r.getProcFee()));
        if (r.getPriority() != null) p.setPriority(r.getPriority());
        if (r.getProcStatus() != null) p.setProcStatus(r.getProcStatus());
        if (r.getProvNum() != null) p.setProvNum(r.getProvNum());
        if (r.getDx() != null) p.setDx(r.getDx());
        if (r.getPlannedAptNum() != null) p.setPlannedAptNum(r.getPlannedAptNum());
        if (r.getPlaceService() != null) p.setPlaceService(r.getPlaceService());
        if (r.getProsthesis() != null) p.setProsthesis(r.getProsthesis());
        if (r.getDateOriginalProsth() != null) p.setDateOriginalProsth(LocalValues.date(r.getDateOriginalProsth()));
        if (r.getClaimNote() != null) p.setClaimNote(r.getClaimNote());
        if (r.getClinicNum() != null) p.setClinicNum(r.getClinicNum());
        if (r.getDiagnosticCode() != null) p.setDiagnosticCode(r.getDiagnosticCode());
        if (r.getIsPrincDiag() != null) p.setIsPrincDiag(r.getIsPrincDiag());
        if (r.getCodeNum() != null) p.setCodeNum(r.getCodeNum());
        if (r.getProcCode() != null) p.setProcCode(r.getProcCode());
        if (r.getDateTP() != null) p.setDateTP(LocalValues.date(r.getDateTP()));
        if (r.getSiteNum() != null) p.setSiteNum(r.getSiteNum());
        if (r.getProcTime() != null) p.setProcTime(r.getProcTime());
        if (r.getProcTimeEnd() != null) p.setProcTimeEnd(r.getProcTimeEnd());
        if (r.getPrognosis() != null) p.setPrognosis(r.getPrognosis());
        if (r.getToothNum() != null) p.setToothNum(r.getToothNum());
        if (r.getSurf() != null) p.setSurf(r.getSurf());
        if (r.getToothRange() != null) p.setToothRange(r.getToothRange());
        if (r.getBillingNote() != null) p.setBillingNote(r.getBillingNote());
        if (r.getSnomedBodySite() != null) p.setSnomedBodySite(r.getSnomedBodySite());
        if (r.getDiagnosticCode2() != null) p.setDiagnosticCode2(r.getDiagnosticCode2());
        if (r.getDiagnosticCode3() != null) p.setDiagnosticCode3(r.getDiagnosticCode3());
        if (r.getDiagnosticCode4() != null) p.setDiagnosticCode4(r.getDiagnosticCode4());
        if (r.getDiscount() != null) p.setDiscount(BigDecimal.valueOf(r.getDiscount()));
        if (r.getIsDateProsthEst() != null) p.setIsDateProsthEst(r.getIsDateProsthEst());
        if (r.getIcdVersion() != null) p.setIcdVersion(r.getIcdVersion());
    }

    private ProcedureLogResponse loadSavedProcedureLog(UUID clinicId, long procNum) {
        return procedureLogRepository.findById(new ProcedureLogId(clinicId, procNum))
                .map(this::toProcedureLogResponse)
                .orElseThrow(() -> procedureLogNotFound(procNum));
    }

    private static ApiException procedureLogNotFound(Long procNum) {
        return new ApiException(HttpStatus.NOT_FOUND, "ProcedureLog not found with ProcNum: " + procNum);
    }

    @Override
    @Transactional
    public void deleteGroupNote(Long procNum) {
        try {
            client.deleteGroupNote(procNum);
        } catch (Exception e) {
            log.error("Failed to delete group note: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete group note: " + e.getMessage());
        }
    }

    // ========== Database sync helpers ==========

    @Transactional
    protected void syncProcedureLogsToDb(List<ProcedureLogResponse> apiResponses) {
        for (ProcedureLogResponse dto : apiResponses) {
            saveProcedureLogToDb(dto);
        }
    }

    @Transactional
    protected void saveProcedureLogToDb(ProcedureLogResponse dto) {
        try {
            if (odSync.hasQueuedChanges(resolveClinicId(), OdSyncService.PROCEDURE_LOG, dto.getProcNum())) {
                return; // our newer copy has not reached Open Dental yet
            }
            ProcedureLog procedureLog = toProcedureLogEntity(dto);
            procedureLogRepository.save(procedureLog);
        } catch (Exception e) {
            log.error("Failed to sync procedurelog {} to database: {}", dto.getProcNum(), e.getMessage());
        }
    }

    private UUID resolveClinicId() {
        List<Clinic> clinics = clinicRepository.findByIsActiveTrue();
        if (clinics.isEmpty()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No active clinic configured. Please register a clinic in the clinics table.");
        }
        return clinics.get(0).getId();
    }

    private ProcedureLog toProcedureLogEntity(ProcedureLogResponse dto) {
        UUID clinicId = resolveClinicId();

        ProcedureLog.ProcedureLogBuilder builder = ProcedureLog.builder()
                .id(new ProcedureLogId(clinicId, dto.getProcNum()))
                .patNum(dto.getPatNum())
                .aptNum(dto.getAptNum() != null && dto.getAptNum() != 0L ? dto.getAptNum() : null)
                .surf(dto.getSurf())
                .toothNum(dto.getToothNum())
                .toothRange(dto.getToothRange())
                .priority(dto.getPriority())
                .procStatus(dto.getProcStatus())
                .provNum(dto.getProvNum())
                .provAbbr(dto.getProvAbbr())
                .dx(dto.getDx())
                .dxName(dto.getDxName())
                .plannedAptNum(dto.getPlannedAptNum())
                .placeService(dto.getPlaceService())
                .prosthesis(dto.getProsthesis())
                .claimNote(dto.getClaimNote())
                .clinicNum(dto.getClinicNum())
                .diagnosticCode(dto.getDiagnosticCode())
                .isPrincDiag(dto.getIsPrincDiag())
                .codeNum(dto.getCodeNum())
                .procCode(dto.getProcCode())
                .descript(dto.getDescript())
                .unitQty(dto.getUnitQty())
                .baseUnits(dto.getBaseUnits())
                .siteNum(dto.getSiteNum())
                .hideGraphics(dto.getHideGraphics())
                .canadianTypeCodes(dto.getCanadianTypeCodes())
                .procTime(dto.getProcTime())
                .procTimeEnd(dto.getProcTimeEnd())
                .prognosis(dto.getPrognosis())
                .isLocked(dto.getIsLocked())
                .billingNote(dto.getBillingNote())
                .snomedBodySite(dto.getSnomedBodySite())
                .diagnosticCode2(dto.getDiagnosticCode2())
                .diagnosticCode3(dto.getDiagnosticCode3())
                .diagnosticCode4(dto.getDiagnosticCode4())
                .isDateProsthEst(dto.getIsDateProsthEst())
                .icdVersion(dto.getIcdVersion());

        if (dto.getProcFee() != null && !dto.getProcFee().isEmpty()) {
            builder.procFee(new BigDecimal(dto.getProcFee()));
        }
        if (dto.getDiscount() != null) {
            builder.discount(BigDecimal.valueOf(dto.getDiscount()));
        }
        if (dto.getDiscountPlanAmt() != null) {
            builder.discountPlanAmt(BigDecimal.valueOf(dto.getDiscountPlanAmt()));
        }

        if (dto.getProcDate() != null && !dto.getProcDate().isEmpty() && !dto.getProcDate().equals("0001-01-01")) {
            builder.procDate(LocalDate.parse(dto.getProcDate(), DATE_FORMAT));
        }
        if (dto.getDateOriginalProsth() != null && !dto.getDateOriginalProsth().isEmpty() && !dto.getDateOriginalProsth().equals("0001-01-01")) {
            builder.dateOriginalProsth(LocalDate.parse(dto.getDateOriginalProsth(), DATE_FORMAT));
        }
        if (dto.getDateEntryC() != null && !dto.getDateEntryC().isEmpty() && !dto.getDateEntryC().equals("0001-01-01")) {
            builder.dateEntryC(LocalDate.parse(dto.getDateEntryC(), DATE_FORMAT));
        }
        if (dto.getDateTP() != null && !dto.getDateTP().isEmpty() && !dto.getDateTP().equals("0001-01-01")) {
            builder.dateTP(LocalDate.parse(dto.getDateTP(), DATE_FORMAT));
        }
        if (dto.getDateTStamp() != null && !dto.getDateTStamp().isEmpty() && !dto.getDateTStamp().equals("0001-01-01 00:00:00")) {
            builder.dateTStamp(LocalDateTime.parse(dto.getDateTStamp(), DATETIME_FORMAT));
        }
        if (dto.getSecDateEntry() != null && !dto.getSecDateEntry().isEmpty() && !dto.getSecDateEntry().equals("0001-01-01 00:00:00")) {
            builder.secDateEntry(LocalDateTime.parse(dto.getSecDateEntry(), DATETIME_FORMAT));
        }

        return builder.build();
    }

    private ProcedureLogResponse toProcedureLogResponse(ProcedureLog entity) {
        ProcedureLogResponse.ProcedureLogResponseBuilder builder = ProcedureLogResponse.builder()
                .ProcNum(entity.getId().getProcNum())
                .AptNum(entity.getAptNum())
                .Surf(entity.getSurf())
                .ToothNum(entity.getToothNum())
                .ToothRange(entity.getToothRange())
                .Priority(entity.getPriority())
                .ProcStatus(entity.getProcStatus())
                .ProvNum(entity.getProvNum())
                .provAbbr(entity.getProvAbbr())
                .Dx(entity.getDx())
                .dxName(entity.getDxName())
                .PlannedAptNum(entity.getPlannedAptNum())
                .PlaceService(entity.getPlaceService())
                .Prosthesis(entity.getProsthesis())
                .ClaimNote(entity.getClaimNote())
                .ClinicNum(entity.getClinicNum())
                .DiagnosticCode(entity.getDiagnosticCode())
                .IsPrincDiag(entity.getIsPrincDiag())
                .CodeNum(entity.getCodeNum())
                .procCode(entity.getProcCode())
                .descript(entity.getDescript())
                .UnitQty(entity.getUnitQty())
                .BaseUnits(entity.getBaseUnits())
                .SiteNum(entity.getSiteNum())
                .HideGraphics(entity.getHideGraphics())
                .CanadianTypeCodes(entity.getCanadianTypeCodes())
                .ProcTime(entity.getProcTime())
                .ProcTimeEnd(entity.getProcTimeEnd())
                .Prognosis(entity.getPrognosis())
                .IsLocked(entity.getIsLocked())
                .BillingNote(entity.getBillingNote())
                .SnomedBodySite(entity.getSnomedBodySite())
                .DiagnosticCode2(entity.getDiagnosticCode2())
                .DiagnosticCode3(entity.getDiagnosticCode3())
                .DiagnosticCode4(entity.getDiagnosticCode4())
                .IsDateProsthEst(entity.getIsDateProsthEst())
                .IcdVersion(entity.getIcdVersion())
                .Discount(entity.getDiscount() != null ? entity.getDiscount().doubleValue() : 0.0)
                .DiscountPlanAmt(entity.getDiscountPlanAmt() != null ? entity.getDiscountPlanAmt().doubleValue() : 0.0);

        if (entity.getProcFee() != null) {
            builder.ProcFee(entity.getProcFee().toString());
        }
        if (entity.getProcDate() != null) {
            builder.ProcDate(entity.getProcDate().format(DATE_FORMAT));
        }
        if (entity.getDateOriginalProsth() != null) {
            builder.DateOriginalProsth(entity.getDateOriginalProsth().format(DATE_FORMAT));
        }
        if (entity.getDateEntryC() != null) {
            builder.DateEntryC(entity.getDateEntryC().format(DATE_FORMAT));
        }
        if (entity.getDateTP() != null) {
            builder.DateTP(entity.getDateTP().format(DATE_FORMAT));
        }
        if (entity.getDateTStamp() != null) {
            builder.DateTStamp(entity.getDateTStamp().format(DATETIME_FORMAT));
        }
        if (entity.getSecDateEntry() != null) {
            builder.SecDateEntry(entity.getSecDateEntry().format(DATETIME_FORMAT));
        }

        return builder.build();
    }
}
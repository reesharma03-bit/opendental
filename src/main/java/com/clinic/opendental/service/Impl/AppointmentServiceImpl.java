package com.clinic.opendental.service.Impl;

import com.clinic.opendental.client.OpenDentalClient;
import com.clinic.opendental.dto.appointment.*;
import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Appointment;
import com.clinic.opendental.model.AppointmentId;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.AppointmentRepository;
import com.clinic.opendental.repository.ClinicRepository;
import com.clinic.opendental.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentServiceImpl implements AppointmentService {

    private final OpenDentalClient client;
    private final AppointmentRepository appointmentRepository;
    private final ClinicRepository clinicRepository;
    private final OdSyncService odSync;

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointment(Long aptNum) {
        log.info("Fetching appointment with AptNum: {}", aptNum);
        try {
            AppointmentResponse apiResponse = client.getAppointment(aptNum);
            log.info("Appointment {} fetched from OpenDental API", aptNum);
            saveAppointmentToDb(apiResponse);
            return apiResponse;
        } catch (Exception e) {
            log.warn("OpenDental API unavailable for appointment {}, falling back to database: {}", aptNum, e.getMessage());
            UUID clinicId = resolveClinicId();
            Appointment appointment = appointmentRepository.findById(new AppointmentId(clinicId, aptNum))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "Appointment not found with AptNum: " + aptNum));
            return toAppointmentResponse(appointment);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointments(Map<String, String> params) {
        log.info("Fetching appointments with {} filter(s)", params == null ? 0 : params.size());
        try {
            List<AppointmentResponse> apiResponses = client.getAppointments(params);
            log.info("Fetched {} appointment(s) from OpenDental API", apiResponses.size());
            syncAppointmentsToDb(apiResponses);
            return apiResponses;
        } catch (Exception e) {
            log.warn("OpenDental API unavailable for appointments, falling back to database: {}", e.getMessage());
            return appointmentRepository.findAll().stream()
                    .map(this::toAppointmentResponse)
                    .collect(Collectors.toList());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsFromDatabase(Map<String, String> params) {
        Map<String, String> filters = params == null ? Map.of() : params;
        Long patNum = filters.containsKey("PatNum") ? Long.valueOf(filters.get("PatNum")) : null;
        String aptStatus = filters.get("AptStatus");
        java.time.LocalDate dateStart = LocalValues.date(filters.get("dateStart"));
        java.time.LocalDate dateEnd = LocalValues.date(filters.get("dateEnd"));
        return appointmentRepository.findByIdClinicId(resolveClinicId()).stream()
                .filter(a -> patNum == null || patNum.equals(a.getPatNum()))
                .filter(a -> aptStatus == null || aptStatus.equalsIgnoreCase(a.getAptStatus()))
                .filter(a -> dateStart == null || (a.getAptDateTime() != null && !a.getAptDateTime().toLocalDate().isBefore(dateStart)))
                .filter(a -> dateEnd == null || (a.getAptDateTime() != null && !a.getAptDateTime().toLocalDate().isAfter(dateEnd)))
                .sorted(java.util.Comparator.comparing(Appointment::getAptDateTime,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(this::toAppointmentResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getASAPAppointments(Map<String, String> params) {
        log.info("Fetching ASAP appointments with {} filter(s)", params == null ? 0 : params.size());
        try {
            List<AppointmentResponse> responses = client.getASAPAppointments(params);
            log.info("Fetched {} ASAP appointment(s)", responses.size());
            return responses;
        } catch (Exception e) {
            log.warn("OpenDental API unavailable for ASAP appointments: {}", e.getMessage());
            return appointmentRepository.findByAptStatus("Scheduled").stream()
                    .filter(a -> "ASAP".equals(a.getPriority()))
                    .map(this::toAppointmentResponse)
                    .collect(Collectors.toList());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> getSlots(Map<String, String> params) {
        log.info("Fetching available slots with {} filter(s)", params == null ? 0 : params.size());
        List<SlotResponse> slots = client.getSlots(params);
        log.info("Fetched {} available slot(s)", slots.size());
        return slots;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> getSlotsWebSched(Map<String, String> params) {
        log.info("Fetching WebSched slots with {} filter(s)", params == null ? 0 : params.size());
        List<SlotResponse> slots = client.getSlotsWebSched(params);
        log.info("Fetched {} WebSched slot(s)", slots.size());
        return slots;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getWebSchedAppointments(Map<String, String> params) {
        log.info("Fetching WebSched appointments with {} filter(s)", params == null ? 0 : params.size());
        try {
            List<AppointmentResponse> responses = client.getWebSchedAppointments(params);
            log.info("Fetched {} WebSched appointment(s)", responses.size());
            return responses;
        } catch (Exception e) {
            log.warn("OpenDental API unavailable for WebSched appointments: {}", e.getMessage());
            return appointmentRepository.findAll().stream()
                    .map(this::toAppointmentResponse)
                    .collect(Collectors.toList());
        }
    }

    // Writes go to our database first and are then pushed to Open Dental (right away
    // when it is reachable, otherwise from the retry queue). A new appointment has a
    // temporary negative AptNum until Open Dental assigns one.

    @Override
    public AppointmentResponse createAppointment(CreateAppointmentRequest request) {
        log.info("Creating new appointment for patient {}", request.getPatNum());
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.PATIENT, request.getPatNum());
        long taskId = odSync.recordCreate(clinicId, OdSyncService.APPOINTMENT, OdSyncService.CREATE, request,
                aptNum -> appointmentRepository.save(Appointment.builder()
                        .id(new AppointmentId(clinicId, aptNum))
                        .patNum(request.getPatNum())
                        .aptStatus(request.getAptStatus() != null ? request.getAptStatus() : "Scheduled")
                        .pattern(request.getPattern())
                        .confirmed(request.getConfirmed())
                        .op(request.getOp())
                        .note(request.getNote())
                        .provNum(request.getProvNum())
                        .provHyg(request.getProvHyg())
                        .aptDateTime(LocalValues.dateTime(request.getAptDateTime()))
                        .assistant(request.getAssistant())
                        .clinicNum(request.getClinicNum())
                        .isHygiene(request.getIsHygiene())
                        .dateTimeArrived(LocalValues.dateTime(request.getDateTimeArrived()))
                        .dateTimeSeated(LocalValues.dateTime(request.getDateTimeSeated()))
                        .dateTimeDismissed(LocalValues.dateTime(request.getDateTimeDismissed()))
                        .isNewPatient(request.getIsNewPatient())
                        .priority(request.getPriority())
                        .appointmentTypeNum(request.getAppointmentTypeNum())
                        .secUserNumEntry(request.getSecUserNumEntry())
                        .colorOverride(request.getColorOverride())
                        .patternSecondary(request.getPatternSecondary())
                        .isMirrored(request.getIsMirrored())
                        .build()));
        return loadSavedAppointment(clinicId, odSync.pushNow(taskId));
    }

    @Override
    public AppointmentResponse createPlannedAppointment(PlannedAppointmentRequest request) {
        log.info("Creating planned appointment for patient {}", request.getPatNum());
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.PATIENT, request.getPatNum());
        long taskId = odSync.recordCreate(clinicId, OdSyncService.APPOINTMENT, OdSyncService.PLANNED, request,
                aptNum -> appointmentRepository.save(Appointment.builder()
                        .id(new AppointmentId(clinicId, aptNum))
                        .patNum(request.getPatNum())
                        .aptStatus("Planned")
                        .appointmentTypeNum(request.getAppointmentTypeNum())
                        .pattern(request.getPattern())
                        .confirmed(request.getConfirmed())
                        .note(request.getNote())
                        .provNum(request.getProvNum())
                        .provHyg(request.getProvHyg())
                        .clinicNum(request.getClinicNum())
                        .isHygiene(request.getIsHygiene())
                        .isNewPatient(request.getIsNewPatient())
                        .priority(request.getPriority())
                        .patternSecondary(request.getPatternSecondary())
                        .build()));
        return loadSavedAppointment(clinicId, odSync.pushNow(taskId));
    }

    @Override
    public AppointmentResponse schedulePlannedAppointment(SchedulePlannedRequest request) {
        log.info("Scheduling planned appointment {}", request.getAptNum());
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.APPOINTMENT, request.getAptNum());
        Appointment planned = appointmentRepository.findById(new AppointmentId(clinicId, request.getAptNum()))
                .orElseThrow(() -> appointmentNotFound(request.getAptNum()));
        long taskId = odSync.recordCreate(clinicId, OdSyncService.APPOINTMENT, OdSyncService.SCHEDULE_PLANNED,
                request, aptNum -> appointmentRepository.save(Appointment.builder()
                        .id(new AppointmentId(clinicId, aptNum))
                        .patNum(planned.getPatNum())
                        .aptStatus("Scheduled")
                        .aptDateTime(LocalValues.dateTime(request.getAptDateTime()))
                        .provNum(request.getProvNum())
                        .op(request.getOp())
                        .confirmed(request.getConfirmed() != null ? request.getConfirmed() : planned.getConfirmed())
                        .note(request.getNote() != null ? request.getNote() : planned.getNote())
                        .pattern(planned.getPattern())
                        .provHyg(planned.getProvHyg())
                        .clinicNum(planned.getClinicNum())
                        .isHygiene(planned.getIsHygiene())
                        .isNewPatient(planned.getIsNewPatient())
                        .priority(planned.getPriority())
                        .appointmentTypeNum(planned.getAppointmentTypeNum())
                        .patternSecondary(planned.getPatternSecondary())
                        .build()));
        return loadSavedAppointment(clinicId, odSync.pushNow(taskId));
    }

    @Override
    public AppointmentResponse createWebSchedAppointment(WebSchedRequest request) {
        log.info("Creating WebSched appointment for patient {}", request.getPatNum());
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.PATIENT, request.getPatNum());
        long taskId = odSync.recordCreate(clinicId, OdSyncService.APPOINTMENT, OdSyncService.WEBSCHED, request,
                aptNum -> appointmentRepository.save(Appointment.builder()
                        .id(new AppointmentId(clinicId, aptNum))
                        .patNum(request.getPatNum())
                        .aptStatus("Scheduled")
                        .aptDateTime(LocalValues.dateTime(request.getDateTimeStart()))
                        .provNum(request.getProvNum())
                        .op(request.getOpNum())
                        .build()));
        return loadSavedAppointment(clinicId, odSync.pushNow(taskId));
    }

    @Override
    public AppointmentResponse updateAppointment(Long aptNum, UpdateAppointmentRequest request) {
        log.info("Updating appointment {}", aptNum);
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.APPOINTMENT, aptNum);
        long taskId = odSync.recordChange(clinicId, OdSyncService.APPOINTMENT, OdSyncService.UPDATE, aptNum, request,
                () -> changeAppointment(clinicId, aptNum, a -> {
                    if (request.getAptStatus() != null) a.setAptStatus(request.getAptStatus());
                    if (request.getPattern() != null) a.setPattern(request.getPattern());
                    if (request.getConfirmed() != null) a.setConfirmed(request.getConfirmed());
                    if (request.getOp() != null) a.setOp(request.getOp());
                    if (request.getNote() != null) a.setNote(request.getNote());
                    if (request.getProvNum() != null) a.setProvNum(request.getProvNum());
                    if (request.getProvHyg() != null) a.setProvHyg(request.getProvHyg());
                    if (request.getAptDateTime() != null) a.setAptDateTime(LocalValues.dateTime(request.getAptDateTime()));
                    if (request.getAssistant() != null) a.setAssistant(request.getAssistant());
                    if (request.getClinicNum() != null) a.setClinicNum(request.getClinicNum());
                    if (request.getIsHygiene() != null) a.setIsHygiene(request.getIsHygiene());
                    if (request.getDateTimeArrived() != null) a.setDateTimeArrived(LocalValues.dateTime(request.getDateTimeArrived()));
                    if (request.getDateTimeSeated() != null) a.setDateTimeSeated(LocalValues.dateTime(request.getDateTimeSeated()));
                    if (request.getDateTimeDismissed() != null) a.setDateTimeDismissed(LocalValues.dateTime(request.getDateTimeDismissed()));
                    if (request.getIsNewPatient() != null) a.setIsNewPatient(request.getIsNewPatient());
                    if (request.getPriority() != null) a.setPriority(request.getPriority());
                    if (request.getAppointmentTypeNum() != null) a.setAppointmentTypeNum(request.getAppointmentTypeNum());
                    if (request.getUnschedStatus() != null) a.setUnschedStatus(request.getUnschedStatus());
                    if (request.getColorOverride() != null) a.setColorOverride(request.getColorOverride());
                    if (request.getPatternSecondary() != null) a.setPatternSecondary(request.getPatternSecondary());
                    if (request.getIsMirrored() != null) a.setIsMirrored(request.getIsMirrored());
                }));
        return loadSavedAppointment(clinicId, odSync.pushNow(taskId));
    }

    @Override
    public void breakAppointment(Long aptNum, BreakAppointmentRequest request) {
        log.info("Breaking appointment {}", aptNum);
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.APPOINTMENT, aptNum);
        long taskId = odSync.recordChange(clinicId, OdSyncService.APPOINTMENT, OdSyncService.BREAK, aptNum, request,
                () -> changeAppointment(clinicId, aptNum, a -> a.setAptStatus(
                        "true".equalsIgnoreCase(request.getSendToUnscheduledList()) ? "UnschedList" : "Broken")));
        odSync.pushNow(taskId);
    }

    @Override
    public void appendNote(Long aptNum, NoteRequest request) {
        log.info("Appending note to appointment {}", aptNum);
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.APPOINTMENT, aptNum);
        long taskId = odSync.recordChange(clinicId, OdSyncService.APPOINTMENT, OdSyncService.NOTE, aptNum, request,
                () -> changeAppointment(clinicId, aptNum, a -> a.setNote(
                        a.getNote() == null || a.getNote().isEmpty() ? request.getNote() : a.getNote() + "\n" + request.getNote())));
        odSync.pushNow(taskId);
    }

    @Override
    public void confirmAppointment(Long aptNum, ConfirmAppointmentRequest request) {
        log.info("Confirming appointment {}", aptNum);
        UUID clinicId = resolveClinicId();
        odSync.ensureStored(clinicId, OdSyncService.APPOINTMENT, aptNum);
        long taskId = odSync.recordChange(clinicId, OdSyncService.APPOINTMENT, OdSyncService.CONFIRM, aptNum, request,
                () -> changeAppointment(clinicId, aptNum, a -> {
                    // confirmVal is a name only Open Dental can map; its copy arrives with the next sync.
                    if (request.getDefNum() != null) a.setConfirmed(request.getDefNum());
                }));
        odSync.pushNow(taskId);
    }

    private void changeAppointment(UUID clinicId, Long aptNum, java.util.function.Consumer<Appointment> change) {
        Appointment appointment = appointmentRepository.findById(new AppointmentId(clinicId, aptNum))
                .orElseThrow(() -> appointmentNotFound(aptNum));
        change.accept(appointment);
        appointmentRepository.save(appointment);
    }

    private AppointmentResponse loadSavedAppointment(UUID clinicId, long aptNum) {
        return appointmentRepository.findById(new AppointmentId(clinicId, aptNum))
                .map(this::toAppointmentResponse)
                .orElseThrow(() -> appointmentNotFound(aptNum));
    }

    private static ApiException appointmentNotFound(Long aptNum) {
        return new ApiException(HttpStatus.NOT_FOUND, "Appointment not found with AptNum: " + aptNum);
    }

    // ========== Database sync helpers ==========

    @Transactional
    protected void syncAppointmentsToDb(List<AppointmentResponse> apiResponses) {
        for (AppointmentResponse dto : apiResponses) {
            saveAppointmentToDb(dto);
        }
    }

    @Transactional
    protected void saveAppointmentToDb(AppointmentResponse dto) {
        try {
            if (odSync.hasQueuedChanges(resolveClinicId(), OdSyncService.APPOINTMENT, dto.getAptNum())) {
                return; // our newer copy has not reached Open Dental yet
            }
            Appointment appointment = toAppointmentEntity(dto);
            appointmentRepository.save(appointment);
            log.info("Appointment {} synced to database", dto.getAptNum());
        } catch (Exception e) {
            log.error("Failed to sync appointment {} to database: {}", dto.getAptNum(), e.getMessage());
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

    private Appointment toAppointmentEntity(AppointmentResponse dto) {
        UUID clinicId = resolveClinicId();

        Appointment.AppointmentBuilder builder = Appointment.builder()
                .id(new AppointmentId(clinicId, dto.getAptNum()))
                .patNum(dto.getPatNum())
                .aptStatus(dto.getAptStatus())
                .pattern(dto.getPattern())
                .confirmed(dto.getConfirmed())
                .timeLocked(dto.getTimeLocked())
                .op(dto.getOp())
                .note(dto.getNote())
                .provNum(dto.getProvNum())
                .provAbbr(dto.getProvAbbr())
                .provHyg(dto.getProvHyg())
                .nextAptNum(dto.getNextAptNum())
                .unschedStatus(dto.getUnschedStatus())
                .isNewPatient(dto.getIsNewPatient())
                .procDescript(dto.getProcDescript())
                .assistant(dto.getAssistant())
                .clinicNum(dto.getClinicNum())
                .isHygiene(dto.getIsHygiene())
                .insPlan1(dto.getInsPlan1())
                .insPlan2(dto.getInsPlan2())
                .colorOverride(dto.getColorOverride())
                .appointmentTypeNum(dto.getAppointmentTypeNum())
                .secUserNumEntry(dto.getSecUserNumEntry())
                .priority(dto.getPriority())
                .patternSecondary(dto.getPatternSecondary())
                .itemOrderPlanned(dto.getItemOrderPlanned())
                .isMirrored(dto.getIsMirrored())
                .eServiceLogType(dto.getEServiceLogType());

        if (dto.getAptDateTime() != null && !dto.getAptDateTime().isEmpty() && !dto.getAptDateTime().equals("0001-01-01 00:00:00")) {
            builder.aptDateTime(LocalDateTime.parse(dto.getAptDateTime(), DATETIME_FORMAT));
        }
        if (dto.getDateTStamp() != null && !dto.getDateTStamp().isEmpty() && !dto.getDateTStamp().equals("0001-01-01 00:00:00")) {
            builder.dateTStamp(LocalDateTime.parse(dto.getDateTStamp(), DATETIME_FORMAT));
        }
        if (dto.getDateTimeArrived() != null && !dto.getDateTimeArrived().isEmpty() && !dto.getDateTimeArrived().equals("0001-01-01 00:00:00")) {
            builder.dateTimeArrived(LocalDateTime.parse(dto.getDateTimeArrived(), DATETIME_FORMAT));
        }
        if (dto.getDateTimeSeated() != null && !dto.getDateTimeSeated().isEmpty() && !dto.getDateTimeSeated().equals("0001-01-01 00:00:00")) {
            builder.dateTimeSeated(LocalDateTime.parse(dto.getDateTimeSeated(), DATETIME_FORMAT));
        }
        if (dto.getDateTimeDismissed() != null && !dto.getDateTimeDismissed().isEmpty() && !dto.getDateTimeDismissed().equals("0001-01-01 00:00:00")) {
            builder.dateTimeDismissed(LocalDateTime.parse(dto.getDateTimeDismissed(), DATETIME_FORMAT));
        }
        if (dto.getDateTimeAskedToArrive() != null && !dto.getDateTimeAskedToArrive().isEmpty() && !dto.getDateTimeAskedToArrive().equals("0001-01-01 00:00:00")) {
            builder.dateTimeAskedToArrive(LocalDateTime.parse(dto.getDateTimeAskedToArrive(), DATETIME_FORMAT));
        }
        if (dto.getSecDateTEntry() != null && !dto.getSecDateTEntry().isEmpty() && !dto.getSecDateTEntry().equals("0001-01-01 00:00:00")) {
            builder.secDateTEntry(LocalDateTime.parse(dto.getSecDateTEntry(), DATETIME_FORMAT));
        }

        return builder.build();
    }

    private AppointmentResponse toAppointmentResponse(Appointment entity) {
        AppointmentResponse.AppointmentResponseBuilder builder = AppointmentResponse.builder()
                .AptNum(entity.getId().getAptNum())
                .PatNum(entity.getPatNum())
                .AptStatus(entity.getAptStatus())
                .Pattern(entity.getPattern())
                .Confirmed(entity.getConfirmed())
                .TimeLocked(entity.getTimeLocked())
                .Op(entity.getOp())
                .Note(entity.getNote())
                .ProvNum(entity.getProvNum())
                .provAbbr(entity.getProvAbbr())
                .ProvHyg(entity.getProvHyg())
                .NextAptNum(entity.getNextAptNum())
                .UnschedStatus(entity.getUnschedStatus())
                .IsNewPatient(entity.getIsNewPatient())
                .ProcDescript(entity.getProcDescript())
                .Assistant(entity.getAssistant())
                .ClinicNum(entity.getClinicNum())
                .IsHygiene(entity.getIsHygiene())
                .InsPlan1(entity.getInsPlan1())
                .InsPlan2(entity.getInsPlan2())
                .colorOverride(entity.getColorOverride())
                .AppointmentTypeNum(entity.getAppointmentTypeNum())
                .SecUserNumEntry(entity.getSecUserNumEntry())
                .Priority(entity.getPriority())
                .PatternSecondary(entity.getPatternSecondary())
                .ItemOrderPlanned(entity.getItemOrderPlanned())
                .IsMirrored(entity.getIsMirrored())
                .eServiceLogType(entity.getEServiceLogType());

        if (entity.getAptDateTime() != null) {
            builder.AptDateTime(entity.getAptDateTime().format(DATETIME_FORMAT));
        }
        if (entity.getDateTStamp() != null) {
            builder.DateTStamp(entity.getDateTStamp().format(DATETIME_FORMAT));
        }
        if (entity.getDateTimeArrived() != null) {
            builder.DateTimeArrived(entity.getDateTimeArrived().format(DATETIME_FORMAT));
        }
        if (entity.getDateTimeSeated() != null) {
            builder.DateTimeSeated(entity.getDateTimeSeated().format(DATETIME_FORMAT));
        }
        if (entity.getDateTimeDismissed() != null) {
            builder.DateTimeDismissed(entity.getDateTimeDismissed().format(DATETIME_FORMAT));
        }
        if (entity.getDateTimeAskedToArrive() != null) {
            builder.DateTimeAskedToArrive(entity.getDateTimeAskedToArrive().format(DATETIME_FORMAT));
        }
        if (entity.getSecDateTEntry() != null) {
            builder.SecDateTEntry(entity.getSecDateTEntry().format(DATETIME_FORMAT));
        }

        return builder.build();
    }
}
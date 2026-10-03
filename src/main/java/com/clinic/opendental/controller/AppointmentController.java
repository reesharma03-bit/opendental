package com.clinic.opendental.controller;

import com.clinic.opendental.dto.appointment.*;
import com.clinic.opendental.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    // ========================================================================
    // GET Endpoints
    // ========================================================================

    /**
     * GET /appointments/{aptNum} - Get a single appointment
     */
    @GetMapping("/{aptNum}")
    public ResponseEntity<AppointmentResponse> getAppointment(
            @PathVariable Long aptNum) {

        return ResponseEntity.ok(
                appointmentService.getAppointment(aptNum));
    }

    /**
     * GET /appointments - Get multiple appointments with optional filters
     */
    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> getAppointments(
            @RequestParam Map<String, String> params) {

        return ResponseEntity.ok(
                appointmentService.getAppointments(params));
    }

    /**
     * GET /appointments/database - Appointments from our database (no Open Dental call).
     * Optional filters: PatNum, AptStatus, dateStart, dateEnd (yyyy-MM-dd).
     */
    @GetMapping("/database")
    public ResponseEntity<List<AppointmentResponse>> getAppointmentsFromDatabase(
            @RequestParam Map<String, String> params) {

        return ResponseEntity.ok(appointmentService.getAppointmentsFromDatabase(params));
    }

    /**
     * GET /appointments/ASAP - Get ASAP list
     */
    @GetMapping("/ASAP")
    public ResponseEntity<List<AppointmentResponse>> getASAPAppointments(
            @RequestParam Map<String, String> params) {

        return ResponseEntity.ok(
                appointmentService.getASAPAppointments(params));
    }

    /**
     * GET /appointments/Slots - Get available time slots
     */
    @GetMapping("/Slots")
    public ResponseEntity<List<SlotResponse>> getSlots(
            @RequestParam Map<String, String> params) {

        return ResponseEntity.ok(
                appointmentService.getSlots(params));
    }

    /**
     * GET /appointments/SlotsWebSched - Get WebSched time slots
     */
    @GetMapping("/SlotsWebSched")
    public ResponseEntity<List<SlotResponse>> getSlotsWebSched(
            @RequestParam Map<String, String> params) {

        return ResponseEntity.ok(
                appointmentService.getSlotsWebSched(params));
    }

    /**
     * GET /appointments/WebSched - Get WebSched appointments
     */
    @GetMapping("/WebSched")
    public ResponseEntity<List<AppointmentResponse>> getWebSchedAppointments(
            @RequestParam Map<String, String> params) {

        return ResponseEntity.ok(
                appointmentService.getWebSchedAppointments(params));
    }

    // ========================================================================
    // POST Endpoints
    // ========================================================================

    /**
     * POST /appointments - Create a new appointment
     */
    @PostMapping
    public ResponseEntity<AppointmentResponse> createAppointment(
            @Valid @RequestBody CreateAppointmentRequest request) {

        AppointmentResponse response = appointmentService.createAppointment(request);

        return ResponseEntity
                .created(Objects.requireNonNull(URI.create("/api/appointments/" + response.getAptNum())))
                .body(response);
    }

    /**
     * POST /appointments/Planned - Create a planned appointment
     */
    @PostMapping("/Planned")
    public ResponseEntity<AppointmentResponse> createPlannedAppointment(
            @Valid @RequestBody PlannedAppointmentRequest request) {

        AppointmentResponse response = appointmentService.createPlannedAppointment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * POST /appointments/SchedulePlanned - Schedule a planned appointment
     */
    @PostMapping("/SchedulePlanned")
    public ResponseEntity<AppointmentResponse> schedulePlannedAppointment(
            @Valid @RequestBody SchedulePlannedRequest request) {

        AppointmentResponse response = appointmentService.schedulePlannedAppointment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * POST /appointments/WebSched - Create a WebSched appointment
     */
    @PostMapping("/WebSched")
    public ResponseEntity<AppointmentResponse> createWebSchedAppointment(
            @Valid @RequestBody WebSchedRequest request) {

        AppointmentResponse response = appointmentService.createWebSchedAppointment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // ========================================================================
    // PUT Endpoints
    // ========================================================================

    /**
     * PUT /appointments/{aptNum} - Update an appointment
     */
    @PutMapping("/{aptNum}")
    public ResponseEntity<AppointmentResponse> updateAppointment(
            @PathVariable Long aptNum,
            @Valid @RequestBody UpdateAppointmentRequest request) {

        return ResponseEntity.ok(
                appointmentService.updateAppointment(aptNum, request));
    }

    /**
     * PUT /appointments/{aptNum}/Break - Break an appointment
     */
    @PutMapping("/{aptNum}/Break")
    public ResponseEntity<Void> breakAppointment(
            @PathVariable Long aptNum,
            @Valid @RequestBody BreakAppointmentRequest request) {

        appointmentService.breakAppointment(aptNum, request);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /appointments/{aptNum}/Note - Append a note to an appointment
     */
    @PutMapping("/{aptNum}/Note")
    public ResponseEntity<Void> appendNote(
            @PathVariable Long aptNum,
            @Valid @RequestBody NoteRequest request) {

        appointmentService.appendNote(aptNum, request);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /appointments/{aptNum}/Confirm - Confirm an appointment
     */
    @PutMapping("/{aptNum}/Confirm")
    public ResponseEntity<Void> confirmAppointment(
            @PathVariable Long aptNum,
            @Valid @RequestBody ConfirmAppointmentRequest request) {

        appointmentService.confirmAppointment(aptNum, request);
        return ResponseEntity.ok().build();
    }
}
package com.clinic.opendental.service;

import com.clinic.opendental.dto.appointment.*;

import java.util.List;
import java.util.Map;

public interface AppointmentService {

    // GET single
    AppointmentResponse getAppointment(Long aptNum);

    // GET multiple
    List<AppointmentResponse> getAppointments(Map<String, String> params);

    // GET ASAP
    List<AppointmentResponse> getASAPAppointments(Map<String, String> params);

    // GET Slots
    List<SlotResponse> getSlots(Map<String, String> params);

    // GET SlotsWebSched
    List<SlotResponse> getSlotsWebSched(Map<String, String> params);

    // GET WebSched
    List<AppointmentResponse> getWebSchedAppointments(Map<String, String> params);

    // POST create
    AppointmentResponse createAppointment(CreateAppointmentRequest request);

    // POST Planned
    AppointmentResponse createPlannedAppointment(PlannedAppointmentRequest request);

    // POST SchedulePlanned
    AppointmentResponse schedulePlannedAppointment(SchedulePlannedRequest request);

    // POST WebSched
    AppointmentResponse createWebSchedAppointment(WebSchedRequest request);

    // PUT update
    AppointmentResponse updateAppointment(Long aptNum, UpdateAppointmentRequest request);

    // PUT Break
    void breakAppointment(Long aptNum, BreakAppointmentRequest request);

    // PUT Note
    void appendNote(Long aptNum, NoteRequest request);

    // PUT Confirm
    void confirmAppointment(Long aptNum, ConfirmAppointmentRequest request);
}
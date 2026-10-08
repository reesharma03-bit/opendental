package com.clinic.opendental.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.clinic.opendental.dto.appointment.AppointmentResponse;
import com.clinic.opendental.dto.appointmentdeleted.AppointmentDeletedResponse;
import com.clinic.opendental.dto.operatory.OperatoryResponse;
import com.clinic.opendental.dto.patfield.PatFieldResponse;
import com.clinic.opendental.dto.patfielddeleted.PatFieldDeletedResponse;
import com.clinic.opendental.dto.patient.PatientResponse;
import com.clinic.opendental.dto.provider.ProviderResponse;
import com.clinic.opendental.dto.schedule.ScheduleResponse;
import com.clinic.opendental.dto.scheduledeleted.ScheduleDeletedResponse;
import com.clinic.opendental.dto.toothinitial.ToothInitialResponse;
import com.clinic.opendental.dto.toothinitialdeleted.ToothInitialDeletedResponse;
import com.clinic.opendental.dto.labcase.LabCaseResponse;
import com.clinic.opendental.dto.labcasedeleted.LabCaseDeletedResponse;
import com.clinic.opendental.dto.medicationpat.MedicationPatResponse;
import com.clinic.opendental.dto.medicationpatdeleted.MedicationPatDeletedResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final SyncService syncService;

    /** Which practice each webhook belongs to (from the API key Open Dental sends with it). */
    private final WebhookClinics webhookClinics;

    // ========================================================================
    // Patient webhook
    // ========================================================================

    /**
     * Process a Patient webhook event from Open Dental.
     *
     * Open Dental sends the full patient records in the webhook payload as a JSON
     * array. We save the received payload directly to Supabase — no API fetch needed.
     *
     * Flow:
     *   Patient Created/Updated in Open Dental
     *       → Webhook received with full patient JSON array
     *       → Parse payload into List<PatientResponse>
     *       → Save/Update each patient in Supabase directly
     */
    public void processPatientWebhook(List<PatientResponse> patients) {
        if (patients == null || patients.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping patient save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (PatientResponse dto : patients) {
            try {
                //log.info(">>> Patient record from webhook (all properties): {}", dto);
                SyncService.SyncResult result = syncService.savePatientFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save patient {} from webhook: {}", dto.getPatNum(), e.getMessage());
            }
        }

        log.info("Patient webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // Appointment webhook
    // ========================================================================

    /**
     * Process an Appointment webhook event from Open Dental.
     *
     * Open Dental sends the full appointment records in the webhook payload as a JSON
     * array. We save the received payload directly to Supabase — no API fetch needed.
     */
    public void processAppointmentWebhook(List<AppointmentResponse> appointments) {
        if (appointments == null || appointments.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping appointment save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (AppointmentResponse dto : appointments) {
            try {
                log.info("Saving appointment {} (pat {}) from webhook to database",
                        dto.getAptNum(), dto.getPatNum());
                SyncService.SyncResult result = syncService.saveAppointmentFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                    log.warn("Appointment {} failed to save from webhook: {}", dto.getAptNum(), result.message());
                } else {
                    saved++;
                    log.info("Appointment {} saved successfully from webhook", dto.getAptNum());
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save appointment {} from webhook: {}", dto.getAptNum(), e.getMessage());
            }
        }

        log.info("Appointment webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // AppointmentDeleted webhook

    public void processAppointmentDeletedWebhook(List<AppointmentDeletedResponse> records) {
        if (records == null || records.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping appointmentDeleted save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (AppointmentDeletedResponse dto : records) {
            try {
                SyncService.SyncResult result = syncService.saveAppointmentDeletedFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save appointmentDeleted record from webhook: {}", e.getMessage());
            }
        }

        log.info("AppointmentDeleted webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // Operatory webhook
    // ========================================================================

    public void processOperatoryWebhook(List<OperatoryResponse> records) {
        if (records == null || records.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping operatory save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (OperatoryResponse dto : records) {
            try {
                SyncService.SyncResult result = syncService.saveOperatoryFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save operatory record from webhook: {}", e.getMessage());
            }
        }

        log.info("Operatory webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // PatField webhook
    // ========================================================================

    public void processPatFieldWebhook(List<PatFieldResponse> records) {
        if (records == null || records.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping patField save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (PatFieldResponse dto : records) {
            try {
                SyncService.SyncResult result = syncService.savePatFieldFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save patField record from webhook: {}", e.getMessage());
            }
        }

        log.info("PatField webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // PatFieldDeleted webhook
    // ========================================================================

    public void processPatFieldDeletedWebhook(List<PatFieldDeletedResponse> records) {
        if (records == null || records.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping patFieldDeleted save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (PatFieldDeletedResponse dto : records) {
            try {
                SyncService.SyncResult result = syncService.savePatFieldDeletedFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save patFieldDeleted record from webhook: {}", e.getMessage());
            }
        }

        log.info("PatFieldDeleted webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // Provider webhook
    // ========================================================================

    public void processProviderWebhook(List<ProviderResponse> records) {
        if (records == null || records.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping provider save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (ProviderResponse dto : records) {
            try {
                SyncService.SyncResult result = syncService.saveProviderFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save provider record from webhook: {}", e.getMessage());
            }
        }

        log.info("Provider webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // Schedule webhook
    // ========================================================================

    public void processScheduleWebhook(List<ScheduleResponse> records) {
        if (records == null || records.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping schedule save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (ScheduleResponse dto : records) {
            try {
                SyncService.SyncResult result = syncService.saveScheduleFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save schedule record from webhook: {}", e.getMessage());
            }
        }

        log.info("Schedule webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // ScheduleDeleted webhook
    // ========================================================================

    public void processScheduleDeletedWebhook(List<ScheduleDeletedResponse> records) {
        if (records == null || records.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping scheduleDeleted save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (ScheduleDeletedResponse dto : records) {
            try {
                SyncService.SyncResult result = syncService.saveScheduleDeletedFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save scheduleDeleted record from webhook: {}", e.getMessage());
            }
        }

        log.info("ScheduleDeleted webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // ToothInitial webhook
    // ========================================================================

    public void processToothInitialWebhook(List<ToothInitialResponse> records) {
        if (records == null || records.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping toothInitial save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (ToothInitialResponse dto : records) {
            try {
                SyncService.SyncResult result = syncService.saveToothInitialFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save toothInitial record from webhook: {}", e.getMessage());
            }
        }

        log.info("ToothInitial webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // ToothInitialDeleted webhook
    // ========================================================================

    public void processToothInitialDeletedWebhook(List<ToothInitialDeletedResponse> records) {
        if (records == null || records.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping toothInitialDeleted save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (ToothInitialDeletedResponse dto : records) {
            try {
                SyncService.SyncResult result = syncService.saveToothInitialDeletedFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save toothInitialDeleted record from webhook: {}", e.getMessage());
            }
        }

        log.info("ToothInitialDeleted webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // LabCase webhook
    // ========================================================================

    public void processLabCaseWebhook(List<LabCaseResponse> records) {
        if (records == null || records.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping labCase save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (LabCaseResponse dto : records) {
            try {
                SyncService.SyncResult result = syncService.saveLabCaseFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save labCase record from webhook: {}", e.getMessage());
            }
        }

        log.info("LabCase webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // LabCaseDeleted webhook
    // ========================================================================

    public void processLabCaseDeletedWebhook(List<LabCaseDeletedResponse> records) {
        if (records == null || records.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping labCaseDeleted save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (LabCaseDeletedResponse dto : records) {
            try {
                SyncService.SyncResult result = syncService.saveLabCaseDeletedFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save labCaseDeleted record from webhook: {}", e.getMessage());
            }
        }

        log.info("LabCaseDeleted webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // MedicationPat webhook
    // ========================================================================

    public void processMedicationPatWebhook(List<MedicationPatResponse> records) {
        if (records == null || records.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping medicationPat save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (MedicationPatResponse dto : records) {
            try {
                SyncService.SyncResult result = syncService.saveMedicationPatFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save medicationPat record from webhook: {}", e.getMessage());
            }
        }

        log.info("MedicationPat webhook complete: {} saved, {} failed", saved, failed);
    }

    // ========================================================================
    // MedicationPatDeleted webhook
    // ========================================================================

    public void processMedicationPatDeletedWebhook(List<MedicationPatDeletedResponse> records) {
        if (records == null || records.isEmpty()) {
            log.warn("Webhook payload is empty. Skipping medicationPatDeleted save.");
            return;
        }

        int saved = 0;
        int failed = 0;

        for (MedicationPatDeletedResponse dto : records) {
            try {
                SyncService.SyncResult result = syncService.saveMedicationPatDeletedFromDto(dto, webhookClinics.clinicCode());
                if (result.failedCount() > 0) {
                    failed++;
                } else {
                    saved++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to save medicationPatDeleted record from webhook: {}", e.getMessage());
            }
        }

        log.info("MedicationPatDeleted webhook complete: {} saved, {} failed", saved, failed);
    }
}

package com.clinic.opendental.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * A number Open Dental sometimes sends as text: an appointment's UnschedStatus comes as its
 * definition id (5) on some versions and as its name ("Appointment Scheduled") on others. A
 * name can't be stored as the id, so it reads as empty instead of failing the whole page.
 */
public class LenientLong extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return p.getLongValue();
        }
        String text = p.getValueAsString();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

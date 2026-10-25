package com.clinic.opendental.dto.procedurelog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Open Dental sends both {@code "Priority": 266} (the definition number) and
 * {@code "priority": "Wait"} (its name). They differ only in case, so the name used to
 * land in the Long and fail the whole page of procedure logs.
 */
class ProcedureLogResponseDeserializationTest {

    private static final String SAMPLE = """
            {"ProcNum": 7, "PatNum": 3, "Priority": 266, "priority": "Wait", "ProcStatus": "TP"}
            """;

    /** The Open Dental client's RestTemplate (plain Jackson defaults). */
    private static final ObjectMapper CLIENT_MAPPER = Jackson2ObjectMapperBuilder.json().build();

    /** The application's global mapper (spring.jackson.property-naming-strategy: SNAKE_CASE). */
    private static final ObjectMapper APP_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void thePriorityNameDoesNotFailTheRecord() throws Exception {
        for (ObjectMapper mapper : new ObjectMapper[] {CLIENT_MAPPER, APP_MAPPER}) {
            ProcedureLogResponse log = mapper.readValue(SAMPLE, ProcedureLogResponse.class);

            assertThat(log.getProcNum()).isEqualTo(7L);
            assertThat(log.getPatNum()).isEqualTo(3L);
            assertThat(log.getPriority()).isEqualTo(266L);
            assertThat(log.getProcStatus()).isEqualTo("TP");
        }
    }
}

package com.clinic.opendental.service.Impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Edits made in the dashboard are copied onto our stored copy of the record. */
class ResourceRecordStoreTest {

    private static final UUID CLINIC = UUID.randomUUID();

    @Test
    @SuppressWarnings("unchecked")
    void editReplacesAFieldWhateverItsCasing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(startsWith("SELECT data FROM od_resource_records"), any(RowMapper.class), any(), any(), any()))
                .thenAnswer(inv -> {
                    RowMapper<Object> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getString(1)).thenReturn(
                            "{\"AllergyNum\":501,\"PatNum\":48,\"statusIsActive\":\"true\",\"Reaction\":\"Rash\"}");
                    return List.of(mapper.mapRow(rs, 0));
                });
        ResourceRecordStore store = new ResourceRecordStore(jdbc, mock(org.springframework.transaction.PlatformTransactionManager.class));

        store.merge(CLINIC, "allergies", "501", Map.of("StatusIsActive", "false"));

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(startsWith("INSERT INTO od_resource_records"), args.capture(), args.capture(),
                args.capture(), args.capture(), args.capture());
        String saved = (String) args.getAllValues().get(4);
        assertThat(saved).contains("\"StatusIsActive\":\"false\"").doesNotContain("statusIsActive");
        assertThat(saved).contains("\"Reaction\":\"Rash\"");
        assertThat(args.getAllValues().get(3)).isEqualTo(48L);
    }
}

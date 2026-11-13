package edu.iitp.landstack.it;

import com.fasterxml.jackson.databind.JsonNode;
import edu.iitp.landstack.quality.QualityEngine;
import edu.iitp.landstack.users.AppUser;
import edu.iitp.landstack.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A failure in the middle of confirmation must leave no confirmed records behind. */
class RollbackIT extends AbstractIT {
    @SpyBean
    QualityEngine quality;

    @Test
    void failedConfirmationRollsBackEverything() throws Exception {
        AppUser op = user(Role.DATA_OPERATOR);
        JsonNode job = json(upload(op, "a.csv", WorkflowIT.FILE_B.getBytes(StandardCharsets.UTF_8)));
        String id = job.get("id").asText();
        awaitStatus(op, id, "AWAITING_MAPPING");
        mvc.perform(put("/api/v1/imports/" + id + "/mapping").with(as(op)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(WorkflowIT.MAPPING)).andExpect(status().isOk());

        doThrow(new IllegalStateException("simulated failure")).when(quality).evaluateDuplicates(any());
        mvc.perform(post("/api/v1/imports/" + id + "/confirm").with(as(op)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmationKey\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select count(*) from land_record", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from land_record_source_value", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select status from import_job where id = ?::uuid", String.class, id)).isEqualTo("PREVIEW_READY");
        assertThat(jdbc.queryForObject("select status_code from import_job where id = ?::uuid", String.class, id)).isEqualTo("CONFIRMATION_ROLLED_BACK");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'IMPORT_CONFIRM_FAILED'", Integer.class)).isEqualTo(1);

        org.mockito.Mockito.reset(quality);
        mvc.perform(post("/api/v1/imports/" + id + "/confirm").with(as(op)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmationKey\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from land_record", Integer.class)).isEqualTo(1);
    }
}

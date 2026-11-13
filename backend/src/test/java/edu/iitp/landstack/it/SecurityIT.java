package edu.iitp.landstack.it;

import com.fasterxml.jackson.databind.JsonNode;
import edu.iitp.landstack.users.AppUser;
import edu.iitp.landstack.users.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityIT extends AbstractIT {

    @Test
    void flywayMigratedAllVersions() {
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isEqualTo(8);
    }

    @Test
    void healthIsPublicButApiIsNot() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/land-records")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env").with(as(user(Role.DATA_OPERATOR)))).andExpect(status().isForbidden());
    }

    @Test
    void errorsUseSafeEnvelope() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/land-records/" + UUID.randomUUID()).with(as(user(Role.READ_ONLY_REVIEWER))))
                .andExpect(status().isNotFound()).andReturn();
        String body = r.getResponse().getContentAsString();
        assertThat(body).contains("\"code\":\"NOT_FOUND\"").doesNotContain("Exception").doesNotContain("at edu.");
        mvc.perform(get("/api/v1/land-records").param("sort", "ownerName;drop table").with(as(user(Role.READ_ONLY_REVIEWER))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void loginRequiresCsrfAndNeverReturnsHash() throws Exception {
        AppUser u = user(Role.DATA_OPERATOR);
        String body = "{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}";
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        MvcResult csrfRes = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie xsrf = csrfRes.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        MvcResult ok = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)
                .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())).andExpect(status().isOk()).andReturn();
        assertThat(ok.getResponse().getContentAsString()).doesNotContain("password").contains("DATA_OPERATOR");
        Cookie session = ok.getResponse().getCookie("LANDSTACK_SESSION");
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(u.getEmail()));
    }

    @Test
    void invalidCredentialsDoNotRevealWhetherAccountExists() throws Exception {
        AppUser u = user(Role.DATA_OPERATOR);
        String wrongPw = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"wrong-password-1\"}")).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.local\",\"password\":\"wrong-password-1\"}")).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(om.readTree(wrongPw).get("message").asText()).isEqualTo(om.readTree(unknown).get("message").asText());
    }

    @Test
    void repeatedFailuresAreRateLimited() throws Exception {
        AppUser u = user(Role.READ_ONLY_REVIEWER);
        String bad = "{\"email\":\"" + u.getEmail() + "\",\"password\":\"wrong-password-1\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bad));
        }
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isTooManyRequests());
    }

    @Test
    void deactivatedUserLosesAccessImmediately() throws Exception {
        AppUser admin = user(Role.ADMINISTRATOR);
        AppUser op = user(Role.DATA_OPERATOR);
        mvc.perform(get("/api/v1/auth/me").with(as(op))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/users/" + op.getId() + "/deactivate").with(as(admin)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").with(as(op))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + op.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdministratorsManageUsersAndNobodyEscalatesThemselves() throws Exception {
        AppUser admin = user(Role.ADMINISTRATOR);
        AppUser op = user(Role.DATA_OPERATOR);
        AppUser rev = user(Role.READ_ONLY_REVIEWER);
        String create = "{\"email\":\"new@test.local\",\"displayName\":\"New\",\"password\":\"Another-Pass1\",\"role\":\"READ_ONLY_REVIEWER\"}";
        mvc.perform(get("/api/v1/users").with(as(op))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users").with(as(op)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/" + op.getId() + "/role").with(as(op)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMINISTRATOR\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users").with(as(rev)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isForbidden());
        JsonNode created = json(mvc.perform(post("/api/v1/users").with(as(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(create)).andExpect(status().isCreated()).andReturn());
        assertThat(created.has("passwordHash")).isFalse();
        assertThat(created.get("role").asText()).isEqualTo("READ_ONLY_REVIEWER");
        mvc.perform(put("/api/v1/users/" + admin.getId() + "/role").with(as(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"DATA_OPERATOR\"}")).andExpect(status().isConflict());
        // Mass assignment: unknown fields such as "active"/"passwordHash" are ignored by the explicit DTO.
        mvc.perform(patch("/api/v1/users/" + op.getId()).with(as(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Renamed\",\"role\":\"ADMINISTRATOR\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DATA_OPERATOR"));
    }

    @Test
    void reviewerCannotMutateAnything() throws Exception {
        AppUser rev = user(Role.READ_ONLY_REVIEWER);
        UUID any = UUID.randomUUID();
        assertThat(upload(rev, "a.csv", "A,B\n1,2\n".getBytes()).getResponse().getStatus()).isEqualTo(403);
        mvc.perform(patch("/api/v1/land-records/" + any).with(as(rev)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"reason\":\"x\",\"changes\":{}}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/quality/issues/" + any + "/review").with(as(rev)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISMISSED\",\"note\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/imports/" + any + "/confirm").with(as(rev)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmationKey\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/exports/land-records.csv").with(as(rev))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-events").with(as(rev))).andExpect(status().isForbidden());
    }
}

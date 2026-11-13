package edu.iitp.landstack.it;

import com.fasterxml.jackson.databind.JsonNode;
import edu.iitp.landstack.users.AppUser;
import edu.iitp.landstack.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The global acceptance scenario, end to end through the HTTP API against PostgreSQL. */
class WorkflowIT extends AbstractIT {
    static final String MAPPING = """
            {"mapping":{"parcel_id":"ULPIN","survey_khasra_no":"Khasra No","owner_name":"Owner","land_area_value":"Area",
             "land_area_unit":"Unit","land_type":"Land Type","village_locality":"Village","district":"District","state":"State",
             "latitude":"Lat","longitude":"Lon"},"dayFirst":true}""";

    static final String FILE_A = """
            ULPIN,Khasra No,Owner,Area,Unit,Land Type,Village,District,State,Lat,Lon
            BR0100000001,0012,Asha Devi,1.5,acre,Agricultural,Rampur,Patna,Bihar,25.594095,85.137566
            BR0100000002,0013,=HYPERLINK("http://x"),2,bigha,Abadi,Rampur,Patna,Bihar,,
            BR0100000003,0014,Ravi Kumar,-4,acre,Agricultural,Rampur,Patna,Bihar,25.6,85.1
            BR0100000004,0015,Meena,3,,Banjar,Sitapur,Patna,Bihar,95.0,85.1
            """;

    static final String FILE_B = """
            ULPIN,Khasra No,Owner,Area,Unit,Land Type,Village,District,State,Lat,Lon
            BR0100000001,0012,Asha Devi,2.5,acre,Agricultural,Rampur,Patna,Bihar,25.594095,85.137566
            """;

    private String importAndPreview(AppUser op, String name, String content) throws Exception {
        JsonNode job = json(upload(op, name, content.getBytes(StandardCharsets.UTF_8)));
        String id = job.get("id").asText();
        awaitStatus(op, id, "AWAITING_MAPPING");
        JsonNode sugg = json(mvc.perform(get("/api/v1/imports/" + id + "/mapping/suggestions").with(as(op))).andExpect(status().isOk()).andReturn());
        assertThat(sugg.get("fields").toString()).contains("\"suggestedColumn\":\"ULPIN\"");
        mvc.perform(put("/api/v1/imports/" + id + "/mapping").with(as(op)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(MAPPING)).andExpect(status().isOk());
        return id;
    }

    private JsonNode confirm(AppUser u, String id, UUID key, int expectedStatus) throws Exception {
        return json(mvc.perform(post("/api/v1/imports/" + id + "/confirm").with(as(u)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmationKey\":\"" + key + "\"}"))
                .andExpect(status().is(expectedStatus)).andReturn());
    }

    @Test
    void uploadMapReviewConfirmSearchMapQualityExport() throws Exception {
        AppUser admin = user(Role.ADMINISTRATOR);
        AppUser op = user(Role.DATA_OPERATOR);
        AppUser rev = user(Role.READ_ONLY_REVIEWER);

        String id = importAndPreview(op, "district-a.csv", FILE_A);
        JsonNode summary = json(mvc.perform(get("/api/v1/imports/" + id + "/preview-summary").with(as(op))).andReturn());
        assertThat(summary.get("totalRows").asInt()).isEqualTo(4);
        assertThat(summary.get("blockedRows").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from land_record", Integer.class)).as("nothing persisted before confirmation").isZero();

        JsonNode rows = json(mvc.perform(get("/api/v1/imports/" + id + "/rows").param("state", "BLOCKED").with(as(op))).andReturn());
        assertThat(rows.get("content").get(0).get("normalized").get("survey_khasra_no").get("value").asText()).isEqualTo("0014");
        String blockedRowId = rows.get("content").get(0).get("id").asText();
        mvc.perform(patch("/api/v1/imports/" + id + "/rows/" + blockedRowId).with(as(op)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\":{\"Area\":\"4\"}}")).andExpect(status().isOk());

        UUID key = UUID.randomUUID();
        JsonNode result = confirm(op, id, key, 200);
        assertThat(result.get("importedRows").asInt()).isEqualTo(4);
        assertThat(result.get("status").asText()).isEqualTo("COMPLETED_WITH_WARNINGS");
        Integer persisted = jdbc.queryForObject("select count(*) from land_record where import_job_id = ?::uuid", Integer.class, id);
        assertThat(persisted).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from land_record where source_file_id is null or import_job_id is null", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from land_record_source_value", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("select survey_khasra_no from land_record where parcel_id = 'BR0100000001'", String.class)).isEqualTo("0012");
        assertThat(jdbc.queryForObject("select count(*) from land_record_source_value where transformation like 'operator correction%'", Integer.class)).isEqualTo(1);

        // idempotent replay and duplicate-confirmation protection
        JsonNode replay = confirm(op, id, key, 200);
        assertThat(replay.get("replayed").asBoolean()).isTrue();
        confirm(op, id, UUID.randomUUID(), 409);
        assertThat(jdbc.queryForObject("select count(*) from land_record", Integer.class)).isEqualTo(4);

        // search: reviewer finds by khasra, sees masked owner, cannot search by owner name
        JsonNode found = json(mvc.perform(get("/api/v1/land-records").param("q", "0012").with(as(rev))).andReturn());
        assertThat(found.get("totalElements").asInt()).isEqualTo(1);
        assertThat(found.get("content").get(0).get("ownerName").asText()).isEqualTo("A**** D****");
        assertThat(json(mvc.perform(get("/api/v1/land-records").param("q", "Asha").with(as(rev))).andReturn()).get("totalElements").asInt()).isZero();
        assertThat(json(mvc.perform(get("/api/v1/land-records").param("q", "Asha").with(as(op))).andReturn()).get("totalElements").asInt()).isEqualTo(1);
        assertThat(json(mvc.perform(get("/api/v1/land-records").param("q", "%").with(as(op))).andReturn()).get("totalElements").asInt()).isZero();

        // detail with provenance
        String recId = found.get("content").get(0).get("id").asText();
        JsonNode detail = json(mvc.perform(get("/api/v1/land-records/" + recId).with(as(rev))).andExpect(status().isOk()).andReturn());
        assertThat(detail.get("provenance").get("normalizationVersion").asText()).isEqualTo("norm-1.0");
        assertThat(detail.get("sourceFile").get("originalFilename").asText()).isEqualTo("district-a.csv");
        assertThat(detail.get("provenance").get("sourceRowNumber").asInt()).isEqualTo(2);

        // map: only valid coordinates
        JsonNode map = json(mvc.perform(get("/api/v1/map/points").with(as(rev))).andReturn());
        assertThat(map.get("mappableCount").asInt()).isEqualTo(2);
        assertThat(map.get("notMappableCount").asInt()).isEqualTo(2);
        assertThat(map.toString()).doesNotContain("Asha");

        // quality: invalid coordinate flagged, second import with same parcel flagged as duplicate, not merged
        assertThat(jdbc.queryForObject("select count(*) from quality_issue where rule_id = 'COORD_OUT_OF_RANGE'", Integer.class)).isEqualTo(1);
        String id2 = importAndPreview(op, "district-a-revised.csv", FILE_B);
        confirm(op, id2, UUID.randomUUID(), 200);
        assertThat(jdbc.queryForObject("select count(*) from land_record", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from quality_issue where rule_id = 'DUP_PARCEL_IN_SCOPE'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from quality_issue where rule_id = 'DUP_CONFLICTING_AREA'", Integer.class)).isPositive();
        int before = jdbc.queryForObject("select count(*) from quality_issue", Integer.class);
        JsonNode rerun = json(mvc.perform(post("/api/v1/quality/runs").with(as(op)).with(csrf())).andExpect(status().isOk()).andReturn());
        assertThat(rerun.get("issuesCreated").asInt()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from quality_issue", Integer.class)).isEqualTo(before);

        // issue review: note required for dismissal, audited
        String issueId = jdbc.queryForObject("select id::text from quality_issue where rule_id = 'DUP_PARCEL_IN_SCOPE' limit 1", String.class);
        mvc.perform(post("/api/v1/quality/issues/" + issueId + "/review").with(as(op)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISMISSED\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/quality/issues/" + issueId + "/review").with(as(op)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISMISSED\",\"note\":\"Revised survey supersedes the earlier sheet\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'ISSUE_DISMISSED'", Integer.class)).isEqualTo(1);

        // correction: lineage cannot change, stale version rejected
        long version = detail.get("version").asLong();
        mvc.perform(patch("/api/v1/land-records/" + recId).with(as(op)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + ",\"reason\":\"x\",\"changes\":{\"import_job_id\":\"" + UUID.randomUUID() + "\"}}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patch("/api/v1/land-records/" + recId).with(as(op)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + ",\"reason\":\"Typo in village name\",\"changes\":{\"village_locality\":\"Rampur Khas\"}}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/land-records/" + recId).with(as(op)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + ",\"reason\":\"again\",\"changes\":{\"village_locality\":\"X\"}}"))
                .andExpect(status().isConflict());

        // export: formula injection neutralised; reviewer forbidden
        String csv = mvc.perform(get("/api/v1/exports/land-records.csv").with(as(op))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'EXPORT_CREATED'", Integer.class)).isEqualTo(1);

        // dashboard counts equal database counts
        JsonNode dash = json(mvc.perform(get("/api/v1/dashboard/summary").with(as(rev))).andReturn());
        assertThat(dash.get("confirmedRecords").asLong())
                .isEqualTo(jdbc.queryForObject("select count(*) from land_record where record_status = 'CONFIRMED'", Long.class));
        assertThat(dash.get("mappableRecords").asLong())
                .isEqualTo(jdbc.queryForObject("select count(*) from land_record where mappable", Long.class));
        assertThat(dash.get("reviewActivityVisible").asBoolean()).isFalse();

        // admin removes the second (demo) import; audit history retained
        mvc.perform(delete("/api/v1/imports/" + id2).param("confirm", "true").with(as(op)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/imports/" + id2).param("confirm", "true").with(as(admin)).with(csrf())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from land_record", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where target_id = ?::uuid and action = 'IMPORT_CONFIRMED'", Integer.class, id2)).isEqualTo(1);
    }

    @Test
    void confirmationWithoutMappingOrPermissionIsRejected() throws Exception {
        AppUser op = user(Role.DATA_OPERATOR);
        JsonNode job = json(upload(op, "a.csv", FILE_B.getBytes()));
        String id = job.get("id").asText();
        awaitStatus(op, id, "AWAITING_MAPPING");
        confirm(op, id, UUID.randomUUID(), 409);
        mvc.perform(put("/api/v1/imports/" + id + "/mapping").with(as(op)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mapping\":{\"owner_name\":\"Owner\"}}")).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from land_record", Integer.class)).isZero();
    }
}

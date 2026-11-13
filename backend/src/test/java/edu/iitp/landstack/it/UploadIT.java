package edu.iitp.landstack.it;

import com.fasterxml.jackson.databind.JsonNode;
import edu.iitp.landstack.users.AppUser;
import edu.iitp.landstack.users.Role;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadIT extends AbstractIT {
    private static final byte[] CSV = "Khasra No,Area,Unit,District\n0012,1.5,acre,Patna\n".getBytes();

    @Test
    void validCsvCreatesTraceableJobWithoutExposingPaths() throws Exception {
        AppUser op = user(Role.DATA_OPERATOR);
        MvcResult r = upload(op, "../../parcels.csv", CSV);
        assertThat(r.getResponse().getStatus()).isEqualTo(202);
        JsonNode job = json(r);
        assertThat(job.get("originalFilename").asText()).isEqualTo("parcels.csv");
        assertThat(r.getResponse().getContentAsString()).doesNotContain("storage").doesNotContain("/tmp");
        assertThat(job.get("sha256").asText()).hasSize(64);
        JsonNode done = awaitStatus(op, job.get("id").asText(), "AWAITING_MAPPING", "FAILED");
        assertThat(done.get("status").asText()).isEqualTo("AWAITING_MAPPING");
        assertThat(done.get("sourceColumns")).hasSize(4);
        try (Stream<Path> files = Files.walk(Path.of(System.getProperty("java.io.tmpdir"), "landstack-test-storage"))) {
            assertThat(files.map(Path::toString)).noneMatch(s -> s.contains("parcels"));
        }
    }

    @Test
    void invalidUploadsAreRejected() throws Exception {
        AppUser op = user(Role.DATA_OPERATOR);
        assertThat(upload(op, "x.exe", CSV).getResponse().getStatus()).isEqualTo(415);
        assertThat(upload(op, "x.xls", CSV).getResponse().getStatus()).isEqualTo(415);
        assertThat(upload(op, "x.pdf", CSV).getResponse().getStatus()).isEqualTo(415);
        assertThat(upload(op, "x.csv", "%PDF-1.4 fake".getBytes()).getResponse().getStatus()).isEqualTo(415);
        byte[] big = new byte[2_500_000];
        Arrays.fill(big, (byte) 'a');
        MvcResult tooBig = upload(op, "big.csv", big);
        assertThat(tooBig.getResponse().getStatus()).isEqualTo(413);
        assertThat(json(tooBig).get("code").asText()).isEqualTo("FILE_TOO_LARGE");
        assertThat(upload(op, "empty.csv", new byte[0]).getResponse().getStatus()).isEqualTo(422);
        assertThat(jdbc.queryForObject("select count(*) from import_job", Integer.class)).isZero();
    }

    @Test
    void unauthenticatedUploadIsRejected() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/imports")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "a.csv", "text/csv", CSV)).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateChecksumIsFlaggedAndNeverOverwritten() throws Exception {
        AppUser op = user(Role.DATA_OPERATOR);
        JsonNode first = json(upload(op, "a.csv", CSV));
        awaitStatus(op, first.get("id").asText(), "AWAITING_MAPPING");
        JsonNode second = json(upload(op, "b.csv", CSV));
        assertThat(second.get("duplicateOfJobId").asText()).isEqualTo(first.get("id").asText());
        assertThat(second.get("sourceFileId").asText()).isNotEqualTo(first.get("sourceFileId").asText());
        awaitStatus(op, second.get("id").asText(), "AWAITING_MAPPING");
    }

    @Test
    void pdfFailsHonestlyWhenProcessingServiceUnavailableAndCsvStillWorks() throws Exception {
        AppUser op = user(Role.DATA_OPERATOR);
        JsonNode pdf = json(upload(op, "deed.pdf", "%PDF-1.4\n%%EOF".getBytes()));
        JsonNode failed = awaitStatus(op, pdf.get("id").asText(), "FAILED");
        assertThat(failed.get("statusCode").asText()).isEqualTo("PROCESSING_UNAVAILABLE");
        assertThat(failed.get("retryable").asBoolean()).isTrue();
        JsonNode csv = json(upload(op, "ok.csv", CSV));
        awaitStatus(op, csv.get("id").asText(), "AWAITING_MAPPING");
        assertThat(jdbc.queryForObject("select count(*) from land_record", Integer.class)).isZero();
    }

    @Test
    void multiSheetWorkbookRequiresSheetSelection() throws Exception {
        AppUser op = user(Role.DATA_OPERATOR);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("ReadMe").createRow(0).createCell(0).setCellValue("Synthetic");
            var s = wb.createSheet("Parcels");
            var h = s.createRow(0);
            h.createCell(0).setCellValue("Khasra No");
            h.createCell(1).setCellValue("District");
            var r = s.createRow(1);
            r.createCell(0).setCellValue("0099");
            r.createCell(1).setCellValue("Gaya");
            wb.write(out);
        }
        JsonNode job = json(upload(op, "book.xlsx", out.toByteArray()));
        JsonNode waiting = awaitStatus(op, job.get("id").asText(), "AWAITING_SHEET_SELECTION");
        assertThat(waiting.get("sheetNames")).hasSize(2);
        mvc.perform(post("/api/v1/imports/" + job.get("id").asText() + "/sheet-selection").with(as(op)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"sheetName\":\"Parcels\"}")).andExpect(status().isOk());
        JsonNode ready = awaitStatus(op, job.get("id").asText(), "AWAITING_MAPPING");
        assertThat(ready.get("selectedSheet").asText()).isEqualTo("Parcels");
    }

    @Test
    void sourceFileBytesOnlyForOperatorsAndNeverPublic() throws Exception {
        AppUser op = user(Role.DATA_OPERATOR);
        AppUser rev = user(Role.READ_ONLY_REVIEWER);
        JsonNode job = json(upload(op, "a.csv", CSV));
        String sf = job.get("sourceFileId").asText();
        mvc.perform(get("/api/v1/source-files/" + sf + "/content").with(as(rev))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/source-files/" + sf + "/content").with(as(op))).andExpect(status().isOk());
        mvc.perform(get("/storage/a.csv")).andExpect(status().isUnauthorized());
    }
}

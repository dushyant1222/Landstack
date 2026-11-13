package edu.iitp.landstack.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.iitp.landstack.security.AppUserPrincipal;
import edu.iitp.landstack.users.AppUser;
import edu.iitp.landstack.users.AppUserRepository;
import edu.iitp.landstack.users.Role;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Integration tests run against real PostgreSQL: a Testcontainers postgres:16 container by default,
 * or an existing disposable database when LANDSTACK_TEST_DB_URL (+ _USER/_PASSWORD) is set.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIT {
    static final String PASSWORD = "Synthetic-Passw0rd";
    private static PostgreSQLContainer<?> pg;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        String url = System.getenv("LANDSTACK_TEST_DB_URL");
        if (url != null && !url.isBlank()) {
            r.add("spring.datasource.url", () -> url);
            r.add("spring.datasource.username", () -> System.getenv().getOrDefault("LANDSTACK_TEST_DB_USER", "landstack"));
            r.add("spring.datasource.password", () -> System.getenv().getOrDefault("LANDSTACK_TEST_DB_PASSWORD", ""));
            return;
        }
        if (pg == null) {
            pg = new PostgreSQLContainer<>("postgres:16-alpine");
            pg.start();
        }
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;
    @Autowired protected AppUserRepository users;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE quality_issue, land_record_source_value, land_record, import_row, processing_attempt, "
                + "import_mapping, field_mapping_template, import_job, source_file, audit_event, "
                + "spring_session_attributes, spring_session, app_user CASCADE");
    }

    protected AppUser user(Role role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 6) + "@test.local";
        return users.save(new AppUser(email, role.name() + " tester", encoder.encode(PASSWORD), role));
    }

    protected RequestPostProcessor as(AppUser u) {
        AppUserPrincipal p = AppUserPrincipal.from(u);
        p.eraseCredentials();
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    protected JsonNode json(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsByteArray());
    }

    protected MvcResult upload(AppUser u, String filename, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/v1/imports").file(new MockMultipartFile("file", filename, "application/octet-stream", content))
                .with(as(u)).with(csrf())).andReturn();
    }

    /** Polls the import until it reaches one of the given statuses (parsing runs asynchronously). */
    protected JsonNode awaitStatus(AppUser u, String jobId, String... statuses) throws Exception {
        Set<String> wanted = Set.of(statuses);
        JsonNode last = null;
        for (int i = 0; i < 100; i++) {
            last = json(mvc.perform(get("/api/v1/imports/" + jobId).with(as(u))).andReturn());
            if (wanted.contains(last.get("status").asText())) return last;
            Thread.sleep(100);
        }
        throw new AssertionError("Import " + jobId + " did not reach " + wanted + "; last=" + last);
    }
}

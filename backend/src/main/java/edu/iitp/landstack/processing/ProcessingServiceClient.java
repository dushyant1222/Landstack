package edu.iitp.landstack.processing;

import edu.iitp.landstack.common.Text;
import edu.iitp.landstack.config.LandStackProperties;
import edu.iitp.landstack.parsing.ParseFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Client for the optional Python processing service (PDF extraction). Every failure becomes a
 * {@link ParseFailure}; the core application never depends on this service being up.
 */
@Component
public class ProcessingServiceClient {
    private static final Logger log = LoggerFactory.getLogger(ProcessingServiceClient.class);
    public static final String UNAVAILABLE = "PROCESSING_UNAVAILABLE";

    private final LandStackProperties.Processing cfg;
    private final RestClient client;

    public ProcessingServiceClient(LandStackProperties props, RestClient.Builder builder) {
        this.cfg = props.processing();
        HttpClient http = HttpClient.newBuilder().connectTimeout(cfg.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(cfg.readTimeout());
        this.client = builder.requestFactory(factory)
                .baseUrl(Text.isBlank(cfg.url()) ? "http://localhost:8090" : cfg.url()).build();
    }

    public boolean configured() {
        return cfg.enabled() && !Text.isBlank(cfg.url()) && !Text.isBlank(cfg.token());
    }

    public PdfExtraction extractPdf(Path file, int maxPages) {
        if (!configured()) {
            throw new ParseFailure(UNAVAILABLE, "PDF processing is not available: the processing service is not configured. "
                    + "CSV and Excel imports are unaffected.", true);
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file) {
            @Override
            public String getFilename() {
                return "document.pdf"; // never forward the user's filename
            }
        });
        body.add("maxPages", String.valueOf(maxPages));
        try {
            PdfExtraction result = client.post().uri("/v1/pdf/extract")
                    .header("X-Service-Token", cfg.token())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(PdfExtraction.class);
            if (result == null || !"1".equals(result.contractVersion())) {
                throw new ParseFailure("PROCESSING_CONTRACT_MISMATCH", "The processing service returned an unexpected response.", true);
            }
            return result;
        } catch (ResourceAccessException e) {
            boolean timeout = e.getCause() instanceof HttpTimeoutException;
            log.warn("Processing service {}", timeout ? "timed out" : "unreachable");
            throw new ParseFailure(timeout ? "PROCESSING_TIMEOUT" : UNAVAILABLE, timeout
                    ? "PDF extraction took too long and was stopped. Try a smaller document or retry later."
                    : "The PDF processing service is unavailable. Retry later; CSV and Excel imports are unaffected.", true);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                log.error("Processing service rejected the service token (check LANDSTACK_PROCESSING_TOKEN)");
                throw new ParseFailure(UNAVAILABLE, "The PDF processing service is misconfigured. Contact the administrator.", true);
            }
            if (e.getStatusCode().value() == 413) {
                throw new ParseFailure("PDF_TOO_LARGE", "The PDF is too large for the processing service.", false);
            }
            throw new ParseFailure("PDF_UNREADABLE", "The PDF could not be processed (it may be corrupt).", false);
        } catch (HttpServerErrorException e) {
            throw new ParseFailure(UNAVAILABLE, "The PDF processing service reported an error. Retry later.", true);
        }
    }

    public Map<String, Object> health() {
        if (!configured()) return Map.of("status", "NOT_CONFIGURED");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> r = client.get().uri("/v1/health").retrieve().body(Map.class);
            return r == null ? Map.of("status", "UNKNOWN") : r;
        } catch (RuntimeException e) {
            return Map.of("status", "DOWN");
        }
    }
}

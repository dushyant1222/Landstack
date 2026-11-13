package edu.iitp.landstack.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.iitp.landstack.common.ApiError;
import edu.iitp.landstack.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/** Writes the standard error envelope from security filters (outside Spring MVC). */
@Component
public class SecurityErrorWriter {
    private final ObjectMapper mapper;

    public SecurityErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletRequest req, HttpServletResponse res, ErrorCode code, String message) throws IOException {
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), new ApiError(Instant.now(), code.status().value(), code.name(),
                message, req.getRequestURI(), null, UUID.randomUUID().toString().substring(0, 8)));
    }
}

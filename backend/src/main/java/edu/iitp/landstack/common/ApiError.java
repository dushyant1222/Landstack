package edu.iitp.landstack.common;

import java.time.Instant;
import java.util.List;

/** The single, safe error envelope returned by every endpoint. Never contains stack traces or paths. */
public record ApiError(Instant timestamp, int status, String code, String message, String path,
                       List<FieldError> fieldErrors, String traceId) {
    public record FieldError(String field, String message) {}
}

package edu.iitp.landstack.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    CONFLICT(HttpStatus.CONFLICT),
    VERSION_CONFLICT(HttpStatus.CONFLICT),
    IMPORT_STATE_CONFLICT(HttpStatus.CONFLICT),
    IMPORT_ALREADY_CONFIRMED(HttpStatus.CONFLICT),
    CONFIRMATION_FAILED(HttpStatus.CONFLICT),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_SIGNATURE_MISMATCH(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_UNREADABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    EXPORT_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    PROCESSING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}

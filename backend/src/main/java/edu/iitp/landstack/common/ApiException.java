package edu.iitp.landstack.common;

import java.util.List;

/** Application exception whose message is safe to show to end users. */
public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final List<ApiError.FieldError> fieldErrors;

    public ApiException(ErrorCode code, String safeMessage) {
        this(code, safeMessage, List.of());
    }

    public ApiException(ErrorCode code, String safeMessage, List<ApiError.FieldError> fieldErrors) {
        super(safeMessage);
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public ErrorCode code() {
        return code;
    }

    public List<ApiError.FieldError> fieldErrors() {
        return fieldErrors;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " was not found.");
    }

    public static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    public static ApiException field(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                List.of(new ApiError.FieldError(field, message)));
    }
}

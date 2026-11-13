package edu.iitp.landstack.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static ResponseEntity<ApiError> build(ErrorCode code, String message, HttpServletRequest req,
                                                 List<ApiError.FieldError> fieldErrors) {
        ApiError body = new ApiError(Instant.now(), code.status().value(), code.name(), message,
                req.getRequestURI(), fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors,
                UUID.randomUUID().toString().substring(0, 8));
        return ResponseEntity.status(code.status()).body(body);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
        return build(ex.code(), ex.getMessage(), req, ex.fieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage())).toList();
        return build(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", req, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<ApiError.FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(v.getPropertyPath().toString(), v.getMessage())).toList();
        return build(ErrorCode.VALIDATION_FAILED, "One or more parameters are invalid.", req, errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest req) {
        String msg = switch (ex) {
            case MissingServletRequestParameterException m -> "Missing required parameter '" + m.getParameterName() + "'.";
            case MissingServletRequestPartException m -> "Missing required part '" + m.getRequestPartName() + "'.";
            case MissingRequestHeaderException m -> "Missing required header '" + m.getHeaderName() + "'.";
            case MethodArgumentTypeMismatchException m -> "Parameter '" + m.getName() + "' has an invalid value.";
            default -> "The request body could not be read.";
        };
        return build(ErrorCode.BAD_REQUEST, msg, req, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return build(ErrorCode.FILE_TOO_LARGE, "The file exceeds the maximum allowed upload size.", req, null);
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiError> handleMultipart(MultipartException ex, HttpServletRequest req) {
        return build(ErrorCode.BAD_REQUEST, "The upload request was malformed.", req, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleMedia(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return build(ErrorCode.UNSUPPORTED_FILE_TYPE, "Unsupported request content type.", req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(ErrorCode.BAD_REQUEST, "HTTP method not supported for this endpoint.", req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return build(ErrorCode.NOT_FOUND, "Resource not found.", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action.", req, null);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    ResponseEntity<ApiError> handleNoAuth(AuthenticationCredentialsNotFoundException ex, HttpServletRequest req) {
        return build(ErrorCode.AUTH_REQUIRED, "Sign in to continue.", req, null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleOptimistic(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return build(ErrorCode.VERSION_CONFLICT, "This item was changed by someone else. Reload and try again.", req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getClass().getSimpleName());
        return build(ErrorCode.CONFLICT, "The change conflicts with existing data.", req, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred. Please try again later.", req, null);
    }
}

package com.memcyco.scheduler.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns every error into an RFC 7807 {@link ProblemDetail} with a machine-readable {@code code}, and for validation
 * errors an {@code errors} map keyed by form field path, so the UI can show each message under its input.
 * Unexpected errors are logged and returned without internals (no stack traces).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException e) {
        ProblemDetail problem = problem(e.getCode(), e.getMessage());
        e.properties().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return handleApiException(new ConcurrentModificationException());
    }

    /** Malformed JSON, wrong value types, or an unknown/missing timing {@code type}. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpMessageNotReadableException e) {
        Throwable cause = e.getMostSpecificCause();
        if (cause instanceof InvalidTypeIdException typeId) {
            return validation(path(typeId), "must be one of ONE_TIME, INTERVAL, WEEKLY, CRON");
        }
        if (cause instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            return validation(path(mapping), "has an invalid value");
        }
        return validation("body", "is not valid JSON");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validation(e.getName(), "has an invalid value");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail handleMissingHeader(MissingRequestHeaderException e) {
        return validation(e.getHeaderName(), "header is required");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail handleMissingParameter(MissingServletRequestParameterException e) {
        return validation(e.getParameterName(), "is required");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResource(NoResourceFoundException e) {
        return problem(ErrorCode.NOT_FOUND, "No endpoint " + e.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return problem(ErrorCode.INTERNAL_ERROR, "Something went wrong. Please try again.");
    }

    private static ProblemDetail validation(String field, String message) {
        return handleValidation(new ValidationException(Map.of(field, message)));
    }

    private static ProblemDetail handleValidation(ValidationException e) {
        ProblemDetail problem = problem(e.getCode(), e.getMessage());
        problem.setProperty("errors", e.getErrors());
        return problem;
    }

    private static ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        return problem;
    }

    private static String path(JsonMappingException e) {
        String path = e.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
        if (e instanceof InvalidTypeIdException) {
            path = path.isEmpty() ? "type" : path + ".type";
        }
        return path.replace(".[", "[");
    }
}

package com.kairon.common.error;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single translation point from exceptions to RFC 7807
 * {@code application/problem+json} responses (docs/DESIGN.md §3.2). Framework
 * exceptions (validation, unreadable body, 404, the 401/403 raised by Spring
 * Security's entry points) already render as problem details via
 * {@code spring.mvc.problemdetails.enabled}; this advice adds the app's own
 * exception types and enriches validation errors with a per-field list.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setType(ex.getType());
        return problem;
    }

    @ExceptionHandler({ OptimisticLockingFailureException.class,
            ObjectOptimisticLockingFailureException.class })
    public ProblemDetail handleOptimisticLock(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The resource was modified by another request. Reload and try again.");
        problem.setType(URI.create("https://kairon.app/problems/conflict"));
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request validation failed.");
        problem.setType(URI.create("https://kairon.app/problems/validation"));
        List<FieldProblem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldProblem(fe.getField(), fe.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    private record FieldProblem(String field, String message) {
    }
}

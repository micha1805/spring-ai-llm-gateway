package io.github.micha1805.llmgateway.error;

import java.net.URI;

import io.github.micha1805.llmgateway.chat.EmptyAnswerException;
import io.github.micha1805.llmgateway.chat.ProviderUnavailableException;
import io.github.micha1805.llmgateway.chat.UpstreamModelException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Runs ahead of Spring Boot's {@code ProblemDetailsExceptionHandler} so validation
 * and JSON errors use this service's titles and {@code errors} list.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request validation failed.", request);
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
                .toList());
        return respond(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        String specific = specificMessage(exception);
        String detail = specific == null
                ? "Request body must be JSON with a provider (openai, anthropic, grok, mistral, ollama) and a message."
                : specific;
        return respond(problem(HttpStatus.BAD_REQUEST, "Malformed request", detail, request));
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    ResponseEntity<ProblemDetail> unavailable(ProviderUnavailableException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "Provider not available",
                exception.getMessage(), request);
        problem.setProperty("provider", exception.provider().id());
        return respond(problem);
    }

    @ExceptionHandler(EmptyAnswerException.class)
    ResponseEntity<ProblemDetail> empty(EmptyAnswerException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_GATEWAY, "Empty provider response",
                exception.getMessage(), request);
        problem.setProperty("provider", exception.provider().id());
        return respond(problem);
    }

    @ExceptionHandler(UpstreamModelException.class)
    ResponseEntity<ProblemDetail> upstream(UpstreamModelException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_GATEWAY, "Provider request failed",
                exception.getMessage(), request);
        problem.setProperty("provider", exception.provider().id());
        return respond(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), exception);
        return respond(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
                "The request could not be completed.", request));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static String specificMessage(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof IllegalArgumentException illegal && illegal.getMessage() != null
                    && illegal.getMessage().startsWith("Unknown provider")) {
                return illegal.getMessage();
            }
            current = current.getCause();
        }
        return null;
    }
}

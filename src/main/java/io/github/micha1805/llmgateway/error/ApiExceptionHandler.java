package io.github.micha1805.llmgateway.error;

import java.net.URI;

import java.util.UUID;

import io.github.micha1805.llmgateway.chat.EmptyAnswerException;
import io.github.micha1805.llmgateway.chat.ProviderUnavailableException;
import io.github.micha1805.llmgateway.chat.UpstreamModelException;
import io.github.micha1805.llmgateway.rag.BadDocumentException;
import io.github.micha1805.llmgateway.rag.DocumentNotFoundException;
import io.github.micha1805.llmgateway.rag.EmbeddingFailedException;
import io.github.micha1805.llmgateway.rag.RagUnavailableException;
import io.github.micha1805.llmgateway.rag.UnsupportedDocumentException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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
        String detail = specific == null ? malformedDetail(request) : specific;
        return respond(problem(HttpStatus.BAD_REQUEST, "Malformed request", detail, request));
    }

    @ExceptionHandler(RagUnavailableException.class)
    ResponseEntity<ProblemDetail> retrievalDown(RagUnavailableException exception, HttpServletRequest request) {
        return respond(problem(HttpStatus.SERVICE_UNAVAILABLE, "Retrieval not available",
                exception.getMessage(), request));
    }

    @ExceptionHandler(EmbeddingFailedException.class)
    ResponseEntity<ProblemDetail> embeddingFailed(EmbeddingFailedException exception, HttpServletRequest request) {
        return respond(problem(HttpStatus.BAD_GATEWAY, "Embedding request failed",
                exception.getMessage(), request));
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ResponseEntity<ProblemDetail> missingDocument(DocumentNotFoundException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Document not found", exception.getMessage(), request);
        problem.setProperty("documentId", exception.documentId().toString());
        return respond(problem);
    }

    @ExceptionHandler(BadDocumentException.class)
    ResponseEntity<ProblemDetail> badDocument(BadDocumentException exception, HttpServletRequest request) {
        return respond(problem(HttpStatus.BAD_REQUEST, "Invalid document", exception.getMessage(), request));
    }

    @ExceptionHandler(UnsupportedDocumentException.class)
    ResponseEntity<ProblemDetail> unsupportedDocument(UnsupportedDocumentException exception, HttpServletRequest request) {
        return respond(problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported document",
                exception.getMessage(), request));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> tooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return respond(problem(HttpStatus.PAYLOAD_TOO_LARGE, "Document too large",
                "The file is larger than 10 MB.", request));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ProblemDetail> missingPart(MissingServletRequestPartException exception, HttpServletRequest request) {
        return respond(problem(HttpStatus.BAD_REQUEST, "Invalid document",
                "Upload the file in the multipart part named 'file'.", request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        String detail = exception.getRequiredType() == UUID.class
                ? "The document id must be a UUID."
                : "A path or query value has the wrong type.";
        return respond(problem(HttpStatus.BAD_REQUEST, "Invalid request", detail, request));
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

    private static String malformedDetail(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/ask")) {
            return "Request body must be JSON with a provider (openai, anthropic, grok, mistral, ollama) and a question.";
        }
        return "Request body must be JSON with a provider (openai, anthropic, grok, mistral, ollama) and a message.";
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

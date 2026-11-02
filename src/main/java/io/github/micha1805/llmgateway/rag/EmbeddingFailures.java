package io.github.micha1805.llmgateway.rag;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

final class EmbeddingFailures {

    private EmbeddingFailures() {
    }

    static RuntimeException translate(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RagUnavailableException unavailable) {
                return unavailable;
            }
            if (current instanceof EmbeddingFailedException failed) {
                return failed;
            }
            current = current.getCause();
        }
        return new EmbeddingFailedException(detail(exception), exception);
    }

    private static String detail(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ResourceAccessException) {
                return "Could not reach the embedding provider. Check its base URL and that the service is running.";
            }
            if (current instanceof RestClientResponseException response) {
                return "The embedding provider returned HTTP " + response.getStatusCode().value() + ".";
            }
            current = current.getCause();
        }
        return "The embedding provider failed to embed the text.";
    }
}

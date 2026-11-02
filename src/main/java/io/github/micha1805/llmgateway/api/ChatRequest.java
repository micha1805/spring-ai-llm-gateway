package io.github.micha1805.llmgateway.api;

import io.github.micha1805.llmgateway.chat.ProviderId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "ChatRequest", description = "One user message routed to a single provider")
public record ChatRequest(
        @NotBlank(message = "message is required")
        @Size(max = ApiConstraints.MAX_MESSAGE_LENGTH, message = "message must be at most 32000 characters")
        @Schema(description = "User message sent to the model", example = "Explain virtual threads",
                maxLength = ApiConstraints.MAX_MESSAGE_LENGTH)
        String message,

        @NotNull(message = "provider is required")
        @Schema(description = "Provider to call for this request", example = "grok")
        ProviderId provider,

        @Size(max = ApiConstraints.MAX_MODEL_LENGTH, message = "model must be at most 128 characters")
        @Schema(description = "Optional model id. When omitted, the provider's configured default is used.",
                example = "grok-4.7", nullable = true, maxLength = ApiConstraints.MAX_MODEL_LENGTH)
        String model
) {

    public ChatRequest {
        message = message == null ? null : message.strip();
        if (model != null) {
            model = model.strip();
            if (model.isEmpty()) {
                model = null;
            }
        }
    }
}

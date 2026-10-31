package io.github.micha1805.llmgateway.api;

import io.github.micha1805.llmgateway.chat.ProviderId;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ChatResponse", description = "The provider's answer for one chat request")
public record ChatReply(
        @Schema(description = "Provider that produced the answer", example = "grok")
        ProviderId provider,

        @Schema(description = "Model id reported by the provider, or the model that was requested",
                example = "grok-4.7")
        String model,

        @Schema(description = "Assistant text", example = "Virtual threads are JVM threads that share carrier platform threads.")
        String answer
) {
}

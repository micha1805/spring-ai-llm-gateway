package io.github.micha1805.llmgateway.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.micha1805.llmgateway.chat.ProviderId;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Provider", description = "A provider the gateway can route to, and whether it is configured")
public record ProviderInfo(
        @Schema(example = "grok")
        ProviderId id,

        @Schema(example = "xAI Grok")
        String displayName,

        @Schema(description = "True when the provider can accept chat requests. For Ollama this means it is enabled, not that the daemon is reachable.",
                example = "false")
        boolean available,

        @Schema(example = "grok-4.7")
        String defaultModel,

        @Schema(example = "https://api.x.ai")
        String baseUrl,

        @Schema(description = "Environment variable that configures this provider", example = "XAI_API_KEY")
        String configurationEnv,

        @Schema(description = "Why the provider cannot be called. Omitted when it is available.",
                example = "Provider 'grok' is not available. Set the XAI_API_KEY environment variable.")
        String reason
) {
}

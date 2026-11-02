package io.github.micha1805.llmgateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {

    @Bean
    OpenAPI llmGatewayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("LLM Gateway")
                .version("1.0.0")
                .description("""
                        Routes one chat message to OpenAI, Anthropic, xAI Grok, Mistral, or Ollama \
                        through Spring AI. The provider is chosen on each request. A provider with no \
                        API key stays listed by GET /api/providers and returns 503 from POST /api/chat \
                        until its environment variable is set. This sample has no authentication; do not \
                        expose it on a public network with live keys.""")
                .contact(new Contact().name("Michel Plainchamp").url("https://github.com/micha1805"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}

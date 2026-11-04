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
                        through Spring AI, and answers questions from ingested PDF, text, and Markdown \
                        using pgvector. The chat provider is chosen on each request. Embeddings use one \
                        configured model so the vector store stays consistent; xAI is not an embedding \
                        provider. A missing key or database does not stop startup: that route returns 503. \
                        This sample has no authentication; do not expose it on a public network with live keys.""")
                .contact(new Contact().name("Michel Plainchamp").url("https://github.com/micha1805"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}

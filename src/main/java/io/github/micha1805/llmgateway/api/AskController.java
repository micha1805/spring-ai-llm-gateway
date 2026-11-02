package io.github.micha1805.llmgateway.api;

import io.github.micha1805.llmgateway.rag.RagService;
import io.github.micha1805.llmgateway.rag.RagStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Retrieval", description = "Answer a question from ingested documents")
public class AskController {

    private final RagService ragService;

    private final RagStatus ragStatus;

    public AskController(RagService ragService, RagStatus ragStatus) {
        this.ragService = ragService;
        this.ragStatus = ragStatus;
    }

    @Operation(summary = "Ask a question about ingested documents",
            description = """
                    Embeds the question with the configured embedding model, retrieves the nearest chunks \
                    from pgvector, and asks the selected chat provider to answer only from those chunks. \
                    The model is instructed to say when the answer is not in the documents, and to reply \
                    in the language of the question (French, Dutch, or English). Embeddings do not follow \
                    the chat provider: xAI does not provide an embeddings API.""")
    @ApiResponse(responseCode = "200", description = "The model answered from the retrieved chunks",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AskReply.class),
                    examples = @ExampleObject(name = "French", value = """
                            {"provider":"grok","model":"grok-4.7","answer":"Le forfait journalier fictif de la Mutuelle Exemple est de 47 euros par jour, avec un maximum de 30 jours.","sources":[{"documentId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","documentName":"hospitalisation-fr.pdf","page":1,"chunk":0,"snippet":"Forfait journalier fictif: 47 euros par jour, maximum 30 jours par annee civile.","score":0.7421}]}""")))
    @ApiResponse(responseCode = "400", description = "The JSON body is malformed or fails validation",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "The chat provider or the embedding provider was called and failed",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Retrieval is not configured, or the chat provider has no key",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title":"Retrieval not available","status":503,"detail":"Document store is not available. Set SPRING_DATASOURCE_URL to a PostgreSQL database with the pgvector extension.","instance":"/api/ask"}""")))
    @PostMapping(path = "/api/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AskReply ask(@io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AskRequest.class),
                    examples = {
                            @ExampleObject(name = "French", value = """
                                    {"provider":"grok","question":"Quel est le forfait journalier fictif de la garantie hospitalisation ?","topK":4}"""),
                            @ExampleObject(name = "Dutch", value = """
                                    {"provider":"mistral","question":"Hoeveel extra tegemoetkoming geeft het Voorbeeldziekenfonds per verpakking van categorie B?"}"""),
                            @ExampleObject(name = "English", value = """
                                    {"provider":"openai","model":"gpt-4o-mini","question":"What is a Belgian mutualité in this sample corpus?"}""")
                    })) @Valid @RequestBody AskRequest request) {
        return ragService.ask(request);
    }

    @Operation(summary = "Show whether retrieval is configured",
            description = "Reports the embedding provider, model, and vector width. API keys are never returned. available is false when the database URL or a required embedding key is missing; chat still works.")
    @ApiResponse(responseCode = "200", description = "The retrieval status",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = RagInfo.class)))
    @GetMapping("/api/rag")
    public RagInfo status() {
        return ragStatus.info();
    }
}

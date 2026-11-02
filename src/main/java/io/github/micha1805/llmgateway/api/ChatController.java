package io.github.micha1805.llmgateway.api;

import java.util.List;

import io.github.micha1805.llmgateway.chat.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
@Tag(name = "Chat", description = "Send a message to a selected LLM provider")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "Send a chat message",
            description = "Forwards the message to the chosen provider through Spring AI's ChatClient and returns the assistant text.")
    @ApiResponse(responseCode = "200", description = "The model answered",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ChatReply.class),
                    examples = @ExampleObject(name = "Grok", value = """
                            {"provider":"grok","model":"grok-4.7","answer":"Virtual threads are JVM threads scheduled on a small set of carrier platform threads."}""")))
    @ApiResponse(responseCode = "400", description = "The JSON body is malformed or fails validation",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "The provider was called and failed, or returned an empty answer",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "The provider is known but not configured",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title":"Provider not available","status":503,"detail":"Provider 'openai' is not available. Set the OPENAI_API_KEY environment variable.","instance":"/api/chat","provider":"openai"}""")))
    @PostMapping(path = "/api/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatReply chat(@io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ChatRequest.class),
                    examples = {
                            @ExampleObject(name = "Grok", value = """
                                    {"provider":"grok","message":"Explain virtual threads"}"""),
                            @ExampleObject(name = "OpenAI with model override", value = """
                                    {"provider":"openai","model":"gpt-4o-mini","message":"Say hello in one sentence"}""")
                    })) @Valid @RequestBody ChatRequest request) {
        return chatService.chat(request);
    }

    @Operation(summary = "List providers",
            description = "Shows every supported provider, its default model, and whether it is configured. API keys are never returned.")
    @ApiResponse(responseCode = "200", description = "The provider catalog",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = ProviderInfo.class))))
    @GetMapping("/api/providers")
    public List<ProviderInfo> providers() {
        return chatService.providers();
    }
}

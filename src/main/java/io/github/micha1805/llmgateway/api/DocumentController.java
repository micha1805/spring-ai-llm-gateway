package io.github.micha1805.llmgateway.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import io.github.micha1805.llmgateway.rag.BadDocumentException;
import io.github.micha1805.llmgateway.rag.DocumentService;
import io.github.micha1805.llmgateway.rag.IngestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Documents", description = "Ingest PDF, text, and Markdown into the pgvector store")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @Operation(summary = "Ingest a document",
            description = """
                    Reads a PDF, text, or Markdown file, splits it into chunks, embeds the chunks with the \
                    configured embedding model, and stores them in PostgreSQL with pgvector. The same bytes \
                    are not embedded twice: a repeat upload returns the existing document with HTTP 200. \
                    PDF chunks keep a 1-based page number. Text and Markdown chunks omit the page.""")
    @ApiResponse(responseCode = "201", description = "The document was embedded",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DocumentSummary.class),
                    examples = @ExampleObject(value = """
                            {"id":"3fa85f64-5717-4562-b3fc-2c963f66afa6","name":"consultations-fr.md","mediaType":"text/markdown","source":"upload","byteSize":1840,"chunkCount":4,"ingestedAt":"2026-11-02T10:15:30Z"}""")))
    @ApiResponse(responseCode = "200", description = "Those exact bytes were already ingested",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DocumentSummary.class)))
    @ApiResponse(responseCode = "400", description = "The file is empty, not UTF-8, or not a readable PDF",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "The file is larger than 10 MB",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "The file is not PDF, text, or Markdown",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "The embedding provider was called and failed",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "The database or the embedding model is not configured",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title":"Retrieval not available","status":503,"detail":"Document store is not available. Set SPRING_DATASOURCE_URL to a PostgreSQL database with the pgvector extension.","instance":"/api/documents"}""")))
    @PostMapping(path = "/api/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentSummary> upload(
            @Parameter(description = "PDF, UTF-8 text, or UTF-8 Markdown file. Maximum 10 MB.", required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Origin label stored on each chunk. Defaults to upload. Use sample for the bundled corpus.",
                    example = "upload")
            @RequestParam(value = "source", required = false) String source) {
        byte[] bytes = bytes(file);
        IngestResult result = documents.ingest(file.getOriginalFilename(), file.getContentType(), bytes, source);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.document());
    }

    @Operation(summary = "List ingested documents",
            description = "Returns the catalog newest first. Chunk text is not included.")
    @ApiResponse(responseCode = "200", description = "The catalog",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = DocumentSummary.class))))
    @ApiResponse(responseCode = "503", description = "The database or the embedding model is not configured",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/documents")
    public List<DocumentSummary> list() {
        return documents.list();
    }

    @Operation(summary = "Get one ingested document",
            description = "Returns catalog metadata for one id. Chunk text is not included.")
    @ApiResponse(responseCode = "200", description = "The document",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DocumentSummary.class)))
    @ApiResponse(responseCode = "404", description = "No document has this id",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/documents/{id}")
    public DocumentSummary get(@Parameter(description = "Document id returned by the ingest or list call")
            @PathVariable UUID id) {
        return documents.get(id);
    }

    @Operation(summary = "Delete an ingested document",
            description = "Removes the catalog row and every chunk that belongs to the document.")
    @ApiResponse(responseCode = "204", description = "The document and its chunks were deleted")
    @ApiResponse(responseCode = "404", description = "No document has this id",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "The database or the embedding model is not configured",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/api/documents/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documents.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        }
        catch (IOException exception) {
            throw new BadDocumentException("The file could not be read.");
        }
    }
}

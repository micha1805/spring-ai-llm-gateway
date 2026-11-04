package io.github.micha1805.llmgateway.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.micha1805.llmgateway.error.ApiExceptionHandler;
import io.github.micha1805.llmgateway.rag.BadDocumentException;
import io.github.micha1805.llmgateway.rag.DocumentNotFoundException;
import io.github.micha1805.llmgateway.rag.DocumentService;
import io.github.micha1805.llmgateway.rag.IngestResult;
import io.github.micha1805.llmgateway.rag.RagStatus;
import io.github.micha1805.llmgateway.rag.RagUnavailableException;
import io.github.micha1805.llmgateway.rag.UnsupportedDocumentException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DocumentController.class)
@Import(ApiExceptionHandler.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documents;

    @Test
    void createsADocument() throws Exception {
        UUID id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        DocumentSummary summary = new DocumentSummary(id, "note.md", "text/markdown", "upload", 12, 1,
                Instant.parse("2026-11-02T10:15:30Z"));
        when(documents.ingest(eq("note.md"), eq("text/markdown"), any(), eq("upload")))
                .thenReturn(new IngestResult(summary, true));

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "note.md", "text/markdown", "hello mutualite".getBytes()))
                        .param("source", "upload"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("note.md"))
                .andExpect(jsonPath("$.chunkCount").value(1));
    }

    @Test
    void returnsOkWhenTheBytesWereAlreadyIngested() throws Exception {
        DocumentSummary summary = new DocumentSummary(UUID.randomUUID(), "note.md", "text/markdown", "upload", 4, 1,
                Instant.parse("2026-11-02T10:15:30Z"));
        when(documents.ingest(any(), any(), any(), isNull())).thenReturn(new IngestResult(summary, false));

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "note.md", "text/markdown", "same".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("note.md"));
    }

    @Test
    void rejectsAnUnsupportedFile() throws Exception {
        when(documents.ingest(any(), any(), any(), any()))
                .thenThrow(new UnsupportedDocumentException("notes.docx"));

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "notes.docx", "application/octet-stream", "x".getBytes())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unsupported document"));
    }

    @Test
    void rejectsAnEmptyExtract() throws Exception {
        when(documents.ingest(any(), any(), any(), any()))
                .thenThrow(new BadDocumentException("The document has no extractable text."));

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "blank.pdf", "application/pdf", "not-a-pdf".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid document"));
    }

    @Test
    void listsAndDeletes() throws Exception {
        UUID id = UUID.randomUUID();
        when(documents.list()).thenReturn(List.of(new DocumentSummary(id, "a.txt", "text/plain", "upload", 3, 1,
                Instant.parse("2026-11-02T10:15:30Z"))));
        when(documents.get(id)).thenReturn(new DocumentSummary(id, "a.txt", "text/plain", "upload", 3, 1,
                Instant.parse("2026-11-02T10:15:30Z")));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("a.txt"));

        mockMvc.perform(get("/api/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        mockMvc.perform(delete("/api/documents/{id}", id))
                .andExpect(status().isNoContent());
        verify(documents).delete(id);
    }

    @Test
    void missingDocumentIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(documents.get(id)).thenThrow(new DocumentNotFoundException(id));

        mockMvc.perform(get("/api/documents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Document not found"))
                .andExpect(jsonPath("$.documentId").value(id.toString()));
    }

    @Test
    void badIdIs400() throws Exception {
        mockMvc.perform(get("/api/documents/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The document id must be a UUID."));
    }

    @Test
    void reportsAMissingStoreAs503() throws Exception {
        when(documents.list()).thenThrow(new RagUnavailableException(RagStatus.NO_DATABASE));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Retrieval not available"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("SPRING_DATASOURCE_URL")));
    }
}

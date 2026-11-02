package io.github.micha1805.llmgateway.rag;

import java.util.Map;
import java.util.UUID;

import io.github.micha1805.llmgateway.api.SourceCitation;
import org.springframework.ai.document.Document;

public final class Citations {

    static final int MAX_SNIPPET = 400;

    private Citations() {
    }

    public static SourceCitation from(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        double score = document.getScore() == null ? 0.0 : document.getScore();
        double rounded = Math.round(score * 1_000_000d) / 1_000_000d;
        return new SourceCitation(
                uuid(metadata.get(ChunkMetadata.DOCUMENT_ID)),
                text(metadata.get(ChunkMetadata.DOCUMENT_NAME)),
                integer(metadata.get(ChunkMetadata.PAGE)),
                integer(metadata.get(ChunkMetadata.CHUNK)) == null ? 0 : integer(metadata.get(ChunkMetadata.CHUNK)),
                snippet(document.getText()),
                rounded);
    }

    public static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String collapsed = text.replaceAll("\\s+", " ").strip();
        if (collapsed.length() <= MAX_SNIPPET) {
            return collapsed;
        }
        return collapsed.substring(0, MAX_SNIPPET - 1).strip() + "…";
    }

    private static UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}

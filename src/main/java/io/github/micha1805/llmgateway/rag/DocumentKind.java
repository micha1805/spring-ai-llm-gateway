package io.github.micha1805.llmgateway.rag;

import java.util.Locale;

public enum DocumentKind {

    PDF("application/pdf"),
    TEXT("text/plain"),
    MARKDOWN("text/markdown");

    private final String mediaType;

    DocumentKind(String mediaType) {
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return mediaType;
    }

    public static DocumentKind detect(String filename, String contentType) {
        String extension = extension(filename);
        if ("pdf".equals(extension)) {
            return PDF;
        }
        if ("txt".equals(extension)) {
            return TEXT;
        }
        if ("md".equals(extension) || "markdown".equals(extension)) {
            return MARKDOWN;
        }
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        int separator = type.indexOf(';');
        if (separator >= 0) {
            type = type.substring(0, separator).trim();
        }
        return switch (type) {
            case "application/pdf" -> PDF;
            case "text/plain" -> TEXT;
            case "text/markdown", "text/x-markdown" -> MARKDOWN;
            default -> throw new UnsupportedDocumentException(filename);
        };
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

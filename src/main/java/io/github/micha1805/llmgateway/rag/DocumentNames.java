package io.github.micha1805.llmgateway.rag;

public final class DocumentNames {

    public static final int MAX_FILENAME_LENGTH = 255;

    public static final int MAX_SOURCE_LENGTH = 64;

    private DocumentNames() {
    }

    public static String filename(String original) {
        if (original == null || original.isBlank()) {
            throw new BadDocumentException("A file name is required.");
        }
        String cleaned = original.replace('\\', '/').trim();
        int slash = cleaned.lastIndexOf('/');
        String name = (slash >= 0 ? cleaned.substring(slash + 1) : cleaned).trim();
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            throw new BadDocumentException("A file name is required.");
        }
        if (name.length() > MAX_FILENAME_LENGTH) {
            throw new BadDocumentException("The file name must be at most " + MAX_FILENAME_LENGTH + " characters.");
        }
        return name;
    }

    public static String source(String source) {
        if (source == null || source.isBlank()) {
            return "upload";
        }
        String value = source.strip();
        if (value.length() > MAX_SOURCE_LENGTH || !value.matches("[\\p{Alnum}._:-]+")) {
            throw new BadDocumentException(
                    "source must be 1-64 characters and use only letters, digits, or . _ : -.");
        }
        return value;
    }
}

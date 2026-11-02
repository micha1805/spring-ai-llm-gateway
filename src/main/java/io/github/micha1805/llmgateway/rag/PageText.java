package io.github.micha1805.llmgateway.rag;

/**
 * One unit of extracted text. {@code page} is 1-based for PDFs and null for text or Markdown.
 */
public record PageText(Integer page, String text) {
}

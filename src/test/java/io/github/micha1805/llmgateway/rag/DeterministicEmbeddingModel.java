package io.github.micha1805.llmgateway.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Bag-of-words hashing embedder for tests. The same tokens always land on the same
 * dimensions, so a question that repeats a rare token retrieves the chunk that contains it.
 * It is not a semantic model and is not a production provider.
 */
public final class DeterministicEmbeddingModel extends AbstractEmbeddingModel {

    private final int dimensions;

    public DeterministicEmbeddingModel(int dimensions) {
        if (dimensions < 8) {
            throw new IllegalArgumentException("dimensions must be at least 8");
        }
        this.dimensions = dimensions;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[] embed(Document document) {
        return embedText(document.getText());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        int index = 0;
        for (String text : request.getInstructions()) {
            embeddings.add(new Embedding(embedText(text), index++));
        }
        return new EmbeddingResponse(embeddings);
    }

    float[] embedText(String text) {
        float[] vector = new float[dimensions];
        boolean any = false;
        for (String token : tokenize(text)) {
            int bucket = Math.floorMod(hash(token), dimensions);
            vector[bucket] += 1.0f;
            any = true;
        }
        if (!any) {
            vector[0] = 1.0f;
            return vector;
        }
        double norm = 0;
        for (float value : vector) {
            norm += (double) value * value;
        }
        float scale = (float) Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= scale;
        }
        return vector;
    }

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    static int hash(String token) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < token.length(); i++) {
            hash ^= token.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }
}

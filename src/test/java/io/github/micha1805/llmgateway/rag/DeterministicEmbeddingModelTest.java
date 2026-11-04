package io.github.micha1805.llmgateway.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeterministicEmbeddingModelTest {

    @Test
    void sharedTokensAreCloserThanUnrelatedText() {
        DeterministicEmbeddingModel model = new DeterministicEmbeddingModel(32);
        float[] question = model.embedText("forfait journalier fictif 47 euros mutuelle exemple");
        float[] match = model.embedText("Forfait journalier fictif: 47 euros par jour. Mutuelle Exemple.");
        float[] other = model.embedText("The quick brown fox jumps over the lazy dog near the river.");

        assertThat(cosine(question, match)).isGreaterThan(cosine(question, other));
        assertThat(model.embedText("same words")).containsExactly(model.embedText("same words"));
    }

    private static double cosine(float[] left, float[] right) {
        double dot = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
        }
        return dot;
    }
}

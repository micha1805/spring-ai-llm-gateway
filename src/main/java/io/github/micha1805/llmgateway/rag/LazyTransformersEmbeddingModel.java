package io.github.micha1805.llmgateway.rag;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.transformers.TransformersEmbeddingModel;

/**
 * Loads the ONNX model on the first embed call. A failed download leaves chat available
 * and makes the RAG routes return 503.
 */
public final class LazyTransformersEmbeddingModel extends AbstractEmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(LazyTransformersEmbeddingModel.class);

    private final String modelUri;

    private final String tokenizerUri;

    private final String outputName;

    private final String cacheDirectory;

    private final int dimensions;

    private final Object lock = new Object();

    private volatile TransformersEmbeddingModel delegate;

    private volatile RagUnavailableException failure;

    public LazyTransformersEmbeddingModel(String modelUri, String tokenizerUri, String outputName,
            String cacheDirectory, int dimensions) {
        this.modelUri = modelUri;
        this.tokenizerUri = tokenizerUri;
        this.outputName = outputName;
        this.cacheDirectory = cacheDirectory;
        this.dimensions = dimensions;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[] embed(Document document) {
        String text = document.getText() == null ? "" : document.getText();
        return model().embed(text);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return model().call(request);
    }

    private TransformersEmbeddingModel model() {
        TransformersEmbeddingModel ready = delegate;
        if (ready != null) {
            return ready;
        }
        RagUnavailableException earlier = failure;
        if (earlier != null) {
            throw earlier;
        }
        synchronized (lock) {
            if (delegate != null) {
                return delegate;
            }
            if (failure != null) {
                throw failure;
            }
            try {
                TransformersEmbeddingModel created = new TransformersEmbeddingModel();
                created.setModelResource(modelUri);
                created.setTokenizerResource(tokenizerUri);
                created.setModelOutputName(outputName);
                created.setResourceCacheDirectory(cacheDirectory);
                created.setTokenizerOptions(Map.of("padding", "true", "truncation", "true"));
                created.afterPropertiesSet();
                delegate = created;
                log.info("Loaded local embedding model output={} cache={}", outputName, cacheDirectory);
                return created;
            }
            catch (Exception exception) {
                log.warn("Local embedding model failed to load from {}", modelUri, exception);
                String detail = exception.getMessage() == null ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                if (detail.length() > 400) {
                    detail = detail.substring(0, 400);
                }
                failure = new RagUnavailableException(
                        "The local embedding model could not be loaded. Check TRANSFORMERS_MODEL_URI, "
                                + "TRANSFORMERS_TOKENIZER_URI, and TRANSFORMERS_OUTPUT_NAME. " + detail);
                throw failure;
            }
        }
    }
}

-- Catalog of ingested files. Chunk text and embeddings live in vector_store,
-- which Spring AI's PgVectorStore creates when initializeSchema is true.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE ingested_document (
    id UUID PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(127) NOT NULL,
    source VARCHAR(64) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    chunk_count INTEGER NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ingested_document_content_hash_idx ON ingested_document (content_hash);

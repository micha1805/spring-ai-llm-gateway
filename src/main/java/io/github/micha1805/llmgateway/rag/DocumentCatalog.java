package io.github.micha1805.llmgateway.rag;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.micha1805.llmgateway.api.DocumentSummary;
import io.github.micha1805.llmgateway.config.DatabaseConfigured;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Conditional(DatabaseConfigured.class)
public class DocumentCatalog {

    private static final RowMapper<DocumentSummary> ROW = (rs, row) -> new DocumentSummary(
            rs.getObject("id", UUID.class),
            rs.getString("filename"),
            rs.getString("media_type"),
            rs.getString("source"),
            rs.getLong("byte_size"),
            rs.getInt("chunk_count"),
            rs.getTimestamp("ingested_at").toInstant());

    private final NamedParameterJdbcTemplate jdbc;

    public DocumentCatalog(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DocumentSummary> findByHash(String hash) {
        List<DocumentSummary> rows = jdbc.query("""
                SELECT id, filename, media_type, source, byte_size, chunk_count, ingested_at
                FROM ingested_document
                WHERE content_hash = :hash
                """, Map.of("hash", hash), ROW);
        return rows.stream().findFirst();
    }

    public Optional<DocumentSummary> findById(UUID id) {
        List<DocumentSummary> rows = jdbc.query("""
                SELECT id, filename, media_type, source, byte_size, chunk_count, ingested_at
                FROM ingested_document
                WHERE id = :id
                """, Map.of("id", id), ROW);
        return rows.stream().findFirst();
    }

    public List<DocumentSummary> list() {
        return jdbc.query("""
                SELECT id, filename, media_type, source, byte_size, chunk_count, ingested_at
                FROM ingested_document
                ORDER BY ingested_at DESC, filename ASC
                """, Map.of(), ROW);
    }

    public void insert(DocumentSummary document, String hash) {
        jdbc.update("""
                INSERT INTO ingested_document
                    (id, filename, media_type, source, content_hash, byte_size, chunk_count, ingested_at)
                VALUES
                    (:id, :filename, :mediaType, :source, :hash, :byteSize, :chunkCount, :ingestedAt)
                """, Map.of(
                "id", document.id(),
                "filename", document.name(),
                "mediaType", document.mediaType(),
                "source", document.source(),
                "hash", hash,
                "byteSize", document.byteSize(),
                "chunkCount", document.chunkCount(),
                "ingestedAt", Timestamp.from(document.ingestedAt())));
    }

    public int delete(UUID id) {
        return jdbc.update("DELETE FROM ingested_document WHERE id = :id", Map.of("id", id));
    }

    public static String sha256(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        }
        catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

}

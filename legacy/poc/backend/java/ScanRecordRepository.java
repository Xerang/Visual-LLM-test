package com.bozor.poc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ScanRecordRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ScanRecordRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public long save(byte[] image, String mime, String filename, AnalysisResponse scan)
            throws JsonProcessingException {
        String sha256;
        try {
            sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO bozor_poc.scan_records
                    (image_mime, image_filename, image_sha256, image_data, model, prompt_version, raw_scan)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb) RETURNING id
                """, Long.class, mime, filename, sha256, image, scan.model, scan.promptVersion,
                json.writeValueAsString(scan));
        return id;
    }

    public Optional<ScanRecord> find(long id) {
        List<ScanRecord> rows = jdbc.query("""
                SELECT id, created_at, reviewed_at, schema_version, image_mime,
                       image_filename, image_sha256, model, prompt_version, raw_scan::text AS raw_scan,
                       selected_index, reviewed_product::text AS reviewed_product,
                       reviewed_products::text AS reviewed_products
                FROM bozor_poc.scan_records WHERE id = ?
                """, (rs, row) -> {
            try {
                String reviewed = rs.getString("reviewed_product");
                String reviewedProducts = rs.getString("reviewed_products");
                return new ScanRecord(rs.getLong("id"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant(),
                        rs.getInt("schema_version"), rs.getString("image_mime"),
                        rs.getString("image_filename"), rs.getString("image_sha256"),
                        rs.getString("model"), rs.getString("prompt_version"), json.readTree(rs.getString("raw_scan")),
                        (Integer) rs.getObject("selected_index"),
                        reviewed == null ? null : json.readTree(reviewed),
                        reviewedProducts == null ? null : json.readTree(reviewedProducts));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Stored scan JSON is invalid", ex);
            }
        }, id);
        return rows.stream().findFirst();
    }

    public Optional<StoredImage> findImage(long id) {
        List<StoredImage> rows = jdbc.query("""
                SELECT image_data, image_mime FROM bozor_poc.scan_records WHERE id = ?
                """, (rs, row) -> new StoredImage(rs.getBytes("image_data"), rs.getString("image_mime")), id);
        return rows.stream().findFirst();
    }

    public boolean saveReview(long id, int selectedIndex, ProductCandidate product)
            throws JsonProcessingException {
        return jdbc.update("""
                UPDATE bozor_poc.scan_records
                SET selected_index = ?, reviewed_product = ?::jsonb, reviewed_at = now(),
                    reviewed_products = NULL, schema_version = 1
                WHERE id = ?
                """, selectedIndex, json.writeValueAsString(product), id) == 1;
    }

    public void saveReviews(long id, List<ScanRecordController.ReviewedItem> items)
            throws JsonProcessingException {
        jdbc.update("""
                UPDATE bozor_poc.scan_records
                SET reviewed_products = ?::jsonb, reviewed_at = now(), schema_version = 2,
                    selected_index = NULL, reviewed_product = NULL
                WHERE id = ?
                """, json.writeValueAsString(items), id);
    }

    public record ScanRecord(long id, Instant createdAt, Instant reviewedAt, int schemaVersion,
                             String imageMime, String imageFilename, String imageSha256,
                             String model, String promptVersion, JsonNode rawScan, Integer selectedIndex,
                             JsonNode reviewedProduct,
                             @JsonProperty("reviewed_products") JsonNode reviewedProducts) {}

    public record StoredImage(byte[] data, String mime) {}
}

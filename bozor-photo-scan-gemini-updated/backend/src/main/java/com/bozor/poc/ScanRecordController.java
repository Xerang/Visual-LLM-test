package com.bozor.poc;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scan-records")
public class ScanRecordController {
    private static final Logger LOG = LoggerFactory.getLogger(ScanRecordController.class);
    private static final Set<String> CATEGORIES = Set.of(
            "clothing", "accessories", "handicrafts", "food", "household", "other");
    private final ScanRecordRepository records;

    public ScanRecordController(ScanRecordRepository records) {
        this.records = records;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable long id) {
        return records.find(id).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AnalysisController.ErrorResponse("저장된 스캔 기록이 없습니다.")));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        return records.findImage(id).map(image -> ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(image.mime()))
                .body(image.data())).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<?> review(@PathVariable long id, @RequestBody ReviewRequest input)
            throws JsonProcessingException {
        var existing = records.find(id);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AnalysisController.ErrorResponse("저장된 스캔 기록이 없습니다."));
        }
        int count = existing.get().rawScan().path("candidates").size();
        ProductCandidate product = input == null ? null : input.selectedProduct();
        if (input == null || input.selectedIndex() == null || input.selectedIndex() < 0
                || input.selectedIndex() >= count || product == null
                || product.name == null || product.name.isBlank()
                || product.category == null || !CATEGORIES.contains(product.category)
                || product.priceAmount != null && product.priceAmount.compareTo(BigDecimal.ZERO) < 0
                || product.stockQuantity != null && product.stockQuantity < 0) {
            return ResponseEntity.badRequest().body(new AnalysisController.ErrorResponse(
                    "선택한 후보 번호와 상품명·카테고리·가격·재고를 확인해 주세요."));
        }
        // Price and stock are seller-entered fields; Gemini's raw scan is kept intact.
        boolean updated = records.saveReview(id, input.selectedIndex(), product);
        if (!updated) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AnalysisController.ErrorResponse("저장된 스캔 기록이 없습니다."));
        }
        return ResponseEntity.ok(records.find(id).orElseThrow());
    }

    public record ReviewRequest(@JsonProperty("selected_index") Integer selectedIndex,
                                @JsonProperty("selected_product") ProductCandidate selectedProduct) {}

    /** Replace the seller-confirmed list for this photo, preserving raw_scan. */
    @PutMapping("/{id}/reviews")
    public ResponseEntity<?> reviews(@PathVariable long id, @RequestBody ReviewsRequest input)
            throws JsonProcessingException {
        var existing = records.find(id);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AnalysisController.ErrorResponse("저장된 스캔 기록이 없습니다."));
        }
        int count = existing.get().rawScan().path("candidates").size();
        if (input == null || input.reviewedProducts() == null || input.reviewedProducts().isEmpty()) {
            return ResponseEntity.badRequest().body(new AnalysisController.ErrorResponse(
                    "확정할 상품 후보를 하나 이상 선택해 주세요."));
        }
        Set<Integer> seen = new HashSet<>();
        for (ReviewedItem item : input.reviewedProducts()) {
            if (item == null || item.candidateIndex() != null
                    && (item.candidateIndex() < 0 || item.candidateIndex() >= count
                        || !seen.add(item.candidateIndex()))
                    || item.product() == null || item.product().name == null
                    || item.product().name.isBlank() || item.product().category == null
                    || !CATEGORIES.contains(item.product().category)
                    || item.product().priceAmount != null
                    && item.product().priceAmount.compareTo(BigDecimal.ZERO) < 0
                    || item.product().stockQuantity != null && item.product().stockQuantity < 0) {
                return ResponseEntity.badRequest().body(new AnalysisController.ErrorResponse(
                        "상품별 후보 번호가 중복되거나 이름·카테고리·가격·재고가 올바르지 않습니다."));
            }
        }
        records.saveReviews(id, input.reviewedProducts());
        return ResponseEntity.ok(records.find(id).orElseThrow());
    }

    public record ReviewedItem(@JsonProperty("candidate_index") Integer candidateIndex,
                               ProductCandidate product) {}

    public record ReviewsRequest(@JsonProperty("reviewed_products") List<ReviewedItem> reviewedProducts) {}

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<AnalysisController.ErrorResponse> databaseError(DataAccessException ex) {
        LOG.error("Failed to access scan records", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new AnalysisController.ErrorResponse("PostgreSQL 연결 또는 저장 상태를 확인해 주세요."));
    }
}

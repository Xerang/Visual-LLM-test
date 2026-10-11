package com.bozor.scan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScanCandidateRepository extends JpaRepository<ScanCandidate, Long> {

    /** 후보 id → 그 후보로 만든 상품 id. 검수 후 화면에서 "만든 상품 보기" 링크용. */
    @Query(value = "SELECT id, source_candidate_id FROM products WHERE source_candidate_id IN (:candidateIds)",
            nativeQuery = true)
    List<Object[]> findProductLinks(@Param("candidateIds") List<Long> candidateIds);
}

package com.bozor.store;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {
    List<Store> findByOwnerIdOrderByIdAsc(Long ownerId);

    Optional<Store> findBySlugAndStatus(String slug, StoreStatus status);

    boolean existsBySlug(String slug);

    /** 공개 상점 목록. q 가 빈 문자열이면 이름 조건을 건너뛴다(PostgreSQL에 NULL 파라미터 타입 문제를 피하려고). */
    @Query("""
            select s from Store s
            where s.status = com.bozor.store.StoreStatus.ACTIVE
              and (:marketId is null or s.marketId = :marketId)
              and (:q = '' or lower(s.name) like lower(concat('%', :q, '%')))
            order by s.id
            """)
    Page<Store> searchActive(@Param("marketId") Long marketId, @Param("q") String q, Pageable pageable);
}

package com.bozor.scan;

import com.bozor.scan.ScanEnums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScanJobRepository extends JpaRepository<ScanJob, Long> {

    /** 검수 대기함: ?status=SUCCEEDED&reviewed=false */
    @Query("""
            select j from ScanJob j
            where j.storeId = :storeId
              and (:status is null or j.status = :status)
              and (:reviewed is null
                   or (:reviewed = true and j.reviewedAt is not null)
                   or (:reviewed = false and j.reviewedAt is null))
            order by j.id desc
            """)
    Page<ScanJob> search(@Param("storeId") Long storeId, @Param("status") JobStatus status,
                         @Param("reviewed") Boolean reviewed, Pageable pageable);
}

package com.bozor.order;

import com.bozor.order.OrderEnums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
            select o from Order o where o.buyerId = :buyerId and (:status is null or o.status = :status)
            order by o.id desc
            """)
    Page<Order> findForBuyer(@Param("buyerId") Long buyerId, @Param("status") Status status, Pageable pageable);

    @Query("""
            select o from Order o where o.storeId = :storeId and (:status is null or o.status = :status)
            order by o.id desc
            """)
    Page<Order> findForStore(@Param("storeId") Long storeId, @Param("status") Status status, Pageable pageable);
}

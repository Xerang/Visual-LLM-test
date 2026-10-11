package com.bozor.product;

import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findByStoreIdOrderByIdDesc(Long storeId, Pageable pageable);

    Page<Product> findByStoreIdAndStatusOrderByIdDesc(Long storeId, ProductStatus status, Pageable pageable);

    /**
     * 판매자가 주문 접수 때 "재고에서 빼기"를 고른 경우에만 호출한다. 0 아래로는 내려가지 않는다.
     * 재고를 관리하지 않는 상품(NULL)은 그대로 둔다.
     */
    @Modifying
    @Query(value = """
            UPDATE products SET stock_quantity = GREATEST(stock_quantity - :quantity, 0), updated_at = now()
            WHERE id = :id AND stock_quantity IS NOT NULL
            """, nativeQuery = true)
    int deductStock(@Param("id") Long id, @Param("quantity") BigDecimal quantity);

    /** 재고를 뺀 뒤 주문이 취소되면 되돌린다. */
    @Modifying
    @Query(value = """
            UPDATE products SET stock_quantity = stock_quantity + :quantity, updated_at = now()
            WHERE id = :id AND stock_quantity IS NOT NULL
            """, nativeQuery = true)
    int restoreStock(@Param("id") Long id, @Param("quantity") BigDecimal quantity);
}

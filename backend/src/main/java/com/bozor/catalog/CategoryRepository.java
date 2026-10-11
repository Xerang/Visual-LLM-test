package com.bozor.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByActiveTrueOrderBySortOrderAscIdAsc();

    Optional<Category> findByCode(String code);

    boolean existsByParentId(Long parentId);
}

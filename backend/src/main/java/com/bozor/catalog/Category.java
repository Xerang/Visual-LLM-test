package com.bozor.catalog;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/** 카테고리 트리(최대 2단계). 기준 데이터라 V2__reference_data.sql 로만 바꾼다. */
@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long parentId;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    /** 상품 등록 시 제안할 판매 단위. 대분류는 NULL. */
    @Enumerated(EnumType.STRING)
    @Column(length = 6)
    private SaleUnit defaultUnit;

    @Column(nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "category_translations", joinColumns = @JoinColumn(name = "category_id"))
    private List<CategoryTranslation> translations = new ArrayList<>();

    protected Category() {}

    public Long getId() { return id; }
    public Long getParentId() { return parentId; }
    public String getCode() { return code; }
    public SaleUnit getDefaultUnit() { return defaultUnit; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
    public List<CategoryTranslation> getTranslations() { return translations; }
}

package com.bozor.product;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/** 언어별 상품명·설명. 지금은 ko 하나만 쓴다(임시 결정 #2). */
@Embeddable
public class ProductTranslation {
    @Column(nullable = false, length = 2)
    private String locale;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** 판매자가 확인하지 않은 기계 번역이면 true. */
    @Column(name = "is_auto_translated", nullable = false)
    private boolean autoTranslated;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProductTranslation() {}

    public ProductTranslation(String locale, String name, String description) {
        this.locale = locale;
        this.name = name;
        this.description = description;
        this.autoTranslated = false;
        this.updatedAt = Instant.now();
    }

    public String getLocale() { return locale; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isAutoTranslated() { return autoTranslated; }
}

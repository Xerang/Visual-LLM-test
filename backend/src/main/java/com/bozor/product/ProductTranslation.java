package com.bozor.product;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/** 언어별 상품명·설명. 판매자가 쓴 원문(지금은 ko, 임시 결정 #2)과 AI 번역 초안(is_auto_translated)이 함께 들어간다. */
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
        this(locale, name, description, false);
    }

    public ProductTranslation(String locale, String name, String description, boolean autoTranslated) {
        this.locale = locale;
        this.name = name;
        this.description = description;
        this.autoTranslated = autoTranslated;
        this.updatedAt = Instant.now();
    }

    /** 같은 글인지(앞뒤 공백·빈 설명은 같게 본다). 자동 번역을 판매자가 고쳤는지 판단할 때 쓴다. */
    public boolean sameText(String otherName, String otherDescription) {
        return name.strip().equals(otherName == null ? "" : otherName.strip())
                && blankToEmpty(description).equals(blankToEmpty(otherDescription));
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s.strip();
    }

    public String getLocale() { return locale; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isAutoTranslated() { return autoTranslated; }
}

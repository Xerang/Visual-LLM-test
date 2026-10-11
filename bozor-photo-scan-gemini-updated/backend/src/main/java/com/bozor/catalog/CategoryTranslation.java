package com.bozor.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class CategoryTranslation {
    @Column(nullable = false, length = 2)
    private String locale;

    @Column(nullable = false, length = 60)
    private String name;

    protected CategoryTranslation() {}

    public String getLocale() { return locale; }
    public String getName() { return name; }
}

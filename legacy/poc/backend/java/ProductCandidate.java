package com.bozor.poc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/** Seller-reviewable AI suggestion; price and stock are left for the seller. */
public final class ProductCandidate {
    public String name;
    public String category;
    public String description;
    @JsonProperty("visible_text") public List<String> visibleText;
    @JsonProperty("review_notes") public List<String> reviewNotes;
    @JsonProperty("price_amount") public BigDecimal priceAmount;
    @JsonProperty("stock_quantity") public Integer stockQuantity;

    public ProductCandidate() {}
}

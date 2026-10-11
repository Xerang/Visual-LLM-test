package com.bozor.product;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 상품에 붙은 사진. 진열대 사진 한 장을 여러 상품이 같이 쓸 수 있다(N:M). sort_order 0 = 대표 사진. */
@Embeddable
public class ProductImageRef {
    @Column(nullable = false)
    private Long imageId;

    @Column(nullable = false)
    private short sortOrder;

    protected ProductImageRef() {}

    public ProductImageRef(Long imageId, int sortOrder) {
        this.imageId = imageId;
        this.sortOrder = (short) sortOrder;
    }

    public Long getImageId() { return imageId; }
    public short getSortOrder() { return sortOrder; }
}

package com.bozor.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 스캔 한 건의 사진(1~3장, 인스타 캐러셀 포함). */
@Embeddable
public class ScanJobImageRef {
    @Column(nullable = false)
    private Long imageId;

    @Column(nullable = false)
    private short sortOrder;

    protected ScanJobImageRef() {}

    public ScanJobImageRef(Long imageId, int sortOrder) {
        this.imageId = imageId;
        this.sortOrder = (short) sortOrder;
    }

    public Long getImageId() { return imageId; }
}

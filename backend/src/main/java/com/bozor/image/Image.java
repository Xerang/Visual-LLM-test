package com.bozor.image;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 업로드한 사진의 정보. 파일 자체는 ImageStorage(지금은 로컬 디스크)에 있다. */
@Entity
@Table(name = "images")
public class Image {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long uploaderId;

    @Column(nullable = false, unique = true)
    private String storageKey;

    private String thumbnailKey;

    @Column(nullable = false, length = 32)
    private String mimeType;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(nullable = false)
    private int byteSize;

    /** 원본 파일의 SHA-256. 같은 사람이 같은 사진을 다시 올렸는지 찾는다. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String sha256;

    private String originalFilename;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Image() {}

    public Image(Long uploaderId, String storageKey, String thumbnailKey, String mimeType,
                 int width, int height, int byteSize, String sha256, String originalFilename) {
        this.uploaderId = uploaderId;
        this.storageKey = storageKey;
        this.thumbnailKey = thumbnailKey;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
        this.byteSize = byteSize;
        this.sha256 = sha256;
        this.originalFilename = originalFilename;
    }

    public Long getId() { return id; }
    public Long getUploaderId() { return uploaderId; }
    public String getStorageKey() { return storageKey; }
    public String getThumbnailKey() { return thumbnailKey; }
    public String getMimeType() { return mimeType; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}

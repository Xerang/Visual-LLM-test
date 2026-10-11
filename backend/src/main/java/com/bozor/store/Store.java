package com.bozor.store;

import com.bozor.store.dto.StoreRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "stores")
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StoreTemplate template;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 7)
    private String themeColor;

    private Long logoImageId;
    private Long bannerImageId;
    private Long marketId;

    /** 시장 안 위치: 줄·칸 번호 등 */
    @Column(length = 100)
    private String locationDetail;

    private String addressText;

    /** 현지 주소는 랜드마크(ориентир)를 함께 적는 것이 일반적이다(조사 §6). */
    private String landmark;

    @Column(length = 16)
    private String contactPhone;

    @Column(length = 16)
    private String whatsappPhone;

    @Column(length = 30)
    private String instagramHandle;

    @Column(length = 32)
    private String telegramUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StoreStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Store() {}

    public Store(Long ownerId, String slug, StoreRequest request) {
        this.ownerId = ownerId;
        this.slug = slug;
        this.status = StoreStatus.DRAFT;
        apply(request);
    }

    /** 상점 설정 폼 전체를 반영한다(PUT). slug 는 서비스에서 따로 확인 후 바꾼다. */
    public void apply(StoreRequest r) {
        this.name = r.name().strip();
        this.description = r.description();
        this.template = r.template();
        this.themeColor = r.themeColor();
        this.logoImageId = r.logoImageId();
        this.bannerImageId = r.bannerImageId();
        this.marketId = r.marketId();
        this.locationDetail = r.locationDetail();
        this.addressText = r.addressText();
        this.landmark = r.landmark();
        this.contactPhone = r.contactPhone();
        this.whatsappPhone = r.whatsappPhone();
        this.instagramHandle = r.instagramHandle();
        this.telegramUsername = r.telegramUsername();
    }

    public void changeSlug(String slug) {
        this.slug = slug;
    }

    public void changeStatus(StoreStatus status) {
        this.status = status;
    }

    /** 구매자가 연락할 수단이 있는지. 공개(ACTIVE)하려면 필요하다. */
    public boolean hasContact() {
        return whatsappPhone != null || contactPhone != null;
    }

    public boolean isOwnedBy(Long userId) {
        return ownerId.equals(userId);
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public StoreTemplate getTemplate() { return template; }
    public String getThemeColor() { return themeColor; }
    public Long getLogoImageId() { return logoImageId; }
    public Long getBannerImageId() { return bannerImageId; }
    public Long getMarketId() { return marketId; }
    public String getLocationDetail() { return locationDetail; }
    public String getAddressText() { return addressText; }
    public String getLandmark() { return landmark; }
    public String getContactPhone() { return contactPhone; }
    public String getWhatsappPhone() { return whatsappPhone; }
    public String getInstagramHandle() { return instagramHandle; }
    public String getTelegramUsername() { return telegramUsername; }
    public StoreStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

package com.bozor.user;

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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String phoneNumber;

    /** 목 로그인 단계에서는 NULL(임시 결정 #1). */
    @Column(length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String displayName;

    @Column(nullable = false, length = 2)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    /** 해외 서버(Gemini·클라우드 저장소) 전송 동의 시각. 개인정보보호법 No.1537. */
    @Column(nullable = false)
    private Instant privacyConsentedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected User() {}

    public void updateProfile(String displayName, String locale) {
        this.displayName = displayName;
        this.locale = locale;
    }

    public Long getId() { return id; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getDisplayName() { return displayName; }
    public String getLocale() { return locale; }
    public Role getRole() { return role; }
}

package com.bozor.store.dto;

import com.bozor.store.StoreTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 상점 만들기(POST)와 설정 저장(PUT)에 같이 쓰는 폼.
 * PUT은 전체 교체라서 비워 보낸 값은 지워진다(예: logo_image_id 를 null 로 보내면 로고 삭제).
 */
public record StoreRequest(
        @NotBlank @Size(max = 80) String name,
        @Pattern(regexp = SLUG, message = "영문 소문자·숫자·하이픈 3~60자") String slug,
        @Size(max = 2000) String description,
        @NotNull StoreTemplate template,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "#RRGGBB 형식") String themeColor,
        Long logoImageId,
        Long bannerImageId,
        Long marketId,
        @Size(max = 100) String locationDetail,
        @Size(max = 255) String addressText,
        @Size(max = 255) String landmark,
        @Pattern(regexp = PHONE, message = "+국가번호 형식 전화번호") String contactPhone,
        @Pattern(regexp = PHONE, message = "+국가번호 형식 전화번호") String whatsappPhone,
        @Size(max = 30) String instagramHandle,
        @Size(max = 32) String telegramUsername) {

    public static final String SLUG = "^[a-z0-9][a-z0-9-]{1,58}[a-z0-9]$";
    /** E.164. 실서비스에서는 +992 + 9자리만 받도록 좁힌다(조사 §7). */
    public static final String PHONE = "^\\+[1-9][0-9]{7,14}$";
}

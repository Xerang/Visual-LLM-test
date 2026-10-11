package com.bozor.product.dto;

import com.bozor.catalog.SaleUnit;
import com.bozor.common.i18n.Locales;
import com.bozor.product.PriceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 상품 등록·수정 폼. 직접 등록, 수정(PUT), AI 검수 확정에서 같이 쓴다.
 * - unit 을 비우면 카테고리의 기본 단위
 * - price_type 을 비우면 FIXED
 * - image_ids 를 비우면 (검수 확정일 때) 스캔 사진을 그대로 쓴다
 */
public record ProductInput(
        @NotNull Long categoryId,
        @NotEmpty @Size(max = 4) List<@Valid TranslationInput> translations,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price,
        PriceType priceType,
        SaleUnit unit,
        @DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal stockQuantity,
        @Size(max = 10) List<Long> imageIds) {

    /**
     * is_auto_translated 는 보통 비워 둔다. 서버가 정한다: 자동 번역을 글자 하나 안 바꾸고 다시 보내면 그대로 자동 번역,
     * 고쳤거나 새로 쓴 번역은 판매자 확인. 고치지 않고 "확인"만 하려면 false 를 보낸다.
     */
    public record TranslationInput(
            @NotNull @Pattern(regexp = Locales.PATTERN) String locale,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            Boolean isAutoTranslated) {

        public TranslationInput(String locale, String name, String description) {
            this(locale, name, description, null);
        }
    }
}

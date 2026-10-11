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

    public record TranslationInput(
            @NotNull @Pattern(regexp = Locales.PATTERN) String locale,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description) {}
}

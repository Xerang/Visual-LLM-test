package com.bozor.order.dto;

import com.bozor.order.OrderEnums.FulfillmentType;
import com.bozor.order.OrderEnums.PaymentMethod;
import com.bozor.order.OrderEnums.Status;
import com.bozor.store.dto.StoreRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 주문 API 요청·응답(docs/03-api.md 5.8). */
public final class OrderDtos {
    private OrderDtos() {}

    public record PlaceRequest(
            @NotNull Long storeId,
            @NotEmpty @Size(max = 30) List<@Valid ItemRequest> items,
            @NotNull FulfillmentType fulfillmentType,
            PaymentMethod paymentMethod,
            @Pattern(regexp = StoreRequest.PHONE, message = "+국가번호 형식 전화번호") String contactPhone,
            @Size(max = 255) String deliveryAddress,
            @Size(max = 255) String landmark,
            @Size(max = 500) String buyerNote) {}

    public record ItemRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 7, fraction = 3) BigDecimal quantity) {}

    /** deduct_stock: 접수(CONFIRMED)할 때만 의미가 있다. true 면 재고를 관리하는 상품의 재고에서 주문 수량을 뺀다. */
    public record SellerStatusRequest(@NotNull Status status, boolean deductStock) {}

    public record OrderResponse(
            Long id,
            String orderNumber,
            String status,
            StoreBrief store,
            BuyerBrief buyer,
            List<ItemResponse> items,
            BigDecimal totalAmount,
            String currency,
            boolean isEstimate,
            String fulfillmentType,
            String paymentMethod,
            String contactPhone,
            String deliveryAddress,
            String landmark,
            String buyerNote,
            boolean stockDeducted,
            Instant createdAt,
            Instant updatedAt) {}

    public record StoreBrief(Long id, String slug, String name, String whatsappPhone) {}

    public record BuyerBrief(Long id, String displayName) {}

    public record ItemResponse(Long productId, String productName, BigDecimal unitPrice, String priceType,
                               String unit, BigDecimal quantity, BigDecimal lineTotal) {}
}

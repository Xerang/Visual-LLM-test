package com.bozor.order;

import com.bozor.common.error.ApiException;
import com.bozor.common.error.ErrorCode;
import com.bozor.common.error.FieldErrorItem;
import com.bozor.common.i18n.Locales;
import com.bozor.common.web.PageResponse;
import com.bozor.common.web.Pages;
import com.bozor.order.OrderEnums.FulfillmentType;
import com.bozor.order.OrderEnums.PaymentMethod;
import com.bozor.order.OrderEnums.Status;
import com.bozor.order.dto.OrderDtos;
import com.bozor.product.Product;
import com.bozor.product.ProductRepository;
import com.bozor.product.ProductStatus;
import com.bozor.product.ProductTranslation;
import com.bozor.store.Store;
import com.bozor.store.StoreRepository;
import com.bozor.store.StoreService;
import com.bozor.store.StoreStatus;
import com.bozor.user.User;
import com.bozor.user.UserRepository;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문(실결제·배송 추적 제외, 상태 관리까지).
 * 재고는 자동 차감하지 않는다. 판매자가 접수 때 "재고에서 빼기"를 고른 경우만 뺀다(판매자 직접 관리 원칙).
 */
@Service
@Transactional
public class OrderService {
    private static final ZoneId DUSHANBE = ZoneId.of("Asia/Dushanbe");
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final OrderRepository orders;
    private final ProductRepository products;
    private final StoreRepository stores;
    private final StoreService storeService;
    private final UserRepository users;
    private final Locales locales;

    public OrderService(OrderRepository orders, ProductRepository products, StoreRepository stores,
                        StoreService storeService, UserRepository users, Locales locales) {
        this.orders = orders;
        this.products = products;
        this.stores = stores;
        this.storeService = storeService;
        this.users = users;
        this.locales = locales;
    }

    // ---- 구매자 ----------------------------------------------------------

    public OrderDtos.OrderResponse place(Long userId, OrderDtos.PlaceRequest request) {
        User buyer = users.findById(userId).orElseThrow(() -> ApiException.notFound("사용자"));
        Store store = stores.findById(request.storeId())
                .filter(s -> s.getStatus() == StoreStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("상점"));
        if (request.fulfillmentType() == FulfillmentType.DELIVERY
                && (request.deliveryAddress() == null || request.deliveryAddress().isBlank())) {
            throw ApiException.invalid("delivery_address", "배송 주문은 주소가 필요합니다.");
        }
        Order order = new Order("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                userId, store.getId(), request.fulfillmentType(),
                request.paymentMethod() != null ? request.paymentMethod() : PaymentMethod.CASH,
                request.contactPhone() != null ? request.contactPhone() : buyer.getPhoneNumber(),
                blankToNull(request.deliveryAddress()), blankToNull(request.landmark()), blankToNull(request.buyerNote()));

        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < request.items().size(); i++) {
            OrderDtos.ItemRequest item = request.items().get(i);
            String field = "items[" + i + "]";
            if (!seen.add(item.productId())) {
                throw ApiException.invalid(field + ".product_id", "같은 상품이 두 번 들어 있습니다.");
            }
            Product product = products.findById(item.productId())
                    .filter(p -> p.getStoreId().equals(store.getId()) && p.getStatus() == ProductStatus.PUBLISHED)
                    .orElseThrow(() -> unavailable(field, "주문할 수 없는 상품입니다(판매 중지 또는 다른 상점 상품)."));
            if (product.getUnit().countable() && item.quantity().stripTrailingZeros().scale() > 0) {
                throw ApiException.invalid(field + ".quantity", "개수 단위 상품은 정수로 주문해 주세요.");
            }
            if (product.getStockQuantity() != null && item.quantity().compareTo(product.getStockQuantity()) > 0) {
                throw unavailable(field, "재고가 부족합니다. 남은 수량: " + product.getStockQuantity().stripTrailingZeros().toPlainString());
            }
            order.addItem(new OrderItem(order, product.getId(), nameOf(product, buyer.getLocale()), product.getPrice(),
                    product.getPriceType(), product.getUnit(), item.quantity()));
        }
        orders.saveAndFlush(order);
        order.assignNumber("BZ-" + order.getCreatedAt().atZone(DUSHANBE).format(NUMBER_DATE)
                + "-" + String.format("%05d", order.getId()));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDtos.OrderResponse> listMine(Long userId, Status status, Integer page, Integer size) {
        return PageResponse.from(orders.findForBuyer(userId, status, Pages.of(page, size)), this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderResponse getMine(Long orderId, Long userId) {
        return toResponse(getAsBuyer(orderId, userId));
    }

    /** 접수 전·접수 후(준비 전)까지 취소 가능. 접수 때 재고를 뺐다면 되돌린다. */
    public OrderDtos.OrderResponse cancel(Long orderId, Long userId) {
        Order order = getAsBuyer(orderId, userId);
        if (!order.getStatus().buyerCanCancel()) {
            throw new ApiException(ErrorCode.ORDER_STATUS_INVALID, "준비가 끝난 주문은 취소할 수 없습니다. 판매자에게 연락해 주세요.");
        }
        if (order.isStockDeducted()) {
            order.getItems().forEach(item -> products.restoreStock(item.getProductId(), item.getQuantity()));
            order.markStockDeducted(false);
        }
        order.changeStatus(Status.CANCELLED);
        return toResponse(order);
    }

    // ---- 판매자 ----------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<OrderDtos.OrderResponse> listForStore(Long storeId, Long userId, Status status,
                                                             Integer page, Integer size) {
        storeService.getOwned(storeId, userId);
        return PageResponse.from(orders.findForStore(storeId, status, Pages.of(page, size)), this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderResponse getForSeller(Long orderId, Long userId) {
        return toResponse(getAsSeller(orderId, userId));
    }

    /**
     * 접수·준비 완료·거래 완료·거절.
     * 접수(CONFIRMED) 때 deductStock=true 면 재고를 관리하는 상품의 재고에서 수량을 뺀다(0 아래로는 안 내려감).
     */
    public OrderDtos.OrderResponse changeStatus(Long orderId, Long userId, OrderDtos.SellerStatusRequest request) {
        Order order = getAsSeller(orderId, userId);
        Status next = request.status();
        if (!order.getStatus().sellerCanChangeTo(next)) {
            throw new ApiException(ErrorCode.ORDER_STATUS_INVALID,
                    order.getStatus() + " 주문은 " + next + "(으)로 바꿀 수 없습니다.");
        }
        if (next == Status.CONFIRMED && request.deductStock()) {
            order.getItems().forEach(item -> products.deductStock(item.getProductId(), item.getQuantity()));
            order.markStockDeducted(true);
        }
        order.changeStatus(next);
        return toResponse(order);
    }

    // ---- 공통 -----------------------------------------------------------

    private Order getAsBuyer(Long orderId, Long userId) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("주문"));
        if (!order.getBuyerId().equals(userId)) {
            throw ApiException.notFound("주문");   // 남의 주문은 있는지조차 알려주지 않는다
        }
        return order;
    }

    private Order getAsSeller(Long orderId, Long userId) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("주문"));
        storeService.getOwned(order.getStoreId(), userId);
        return order;
    }

    private OrderDtos.OrderResponse toResponse(Order o) {
        Store store = stores.findById(o.getStoreId()).orElseThrow();
        User buyer = users.findById(o.getBuyerId()).orElseThrow();
        return new OrderDtos.OrderResponse(o.getId(), o.getOrderNumber(), o.getStatus().name(),
                new OrderDtos.StoreBrief(store.getId(), store.getSlug(), store.getName(), store.getWhatsappPhone()),
                new OrderDtos.BuyerBrief(buyer.getId(), buyer.getDisplayName()),
                o.getItems().stream().map(i -> new OrderDtos.ItemResponse(i.getProductId(), i.getProductName(),
                        i.getUnitPrice(), i.getPriceType().name(), i.getUnit().name(), i.getQuantity(),
                        i.getLineTotal())).toList(),
                o.getTotalAmount(), o.getCurrency(), o.isEstimate(), o.getFulfillmentType().name(),
                o.getPaymentMethod().name(), o.getContactPhone(), o.getDeliveryAddress(), o.getLandmark(),
                o.getBuyerNote(), o.isStockDeducted(), o.getCreatedAt(), o.getUpdatedAt());
    }

    private String nameOf(Product product, String locale) {
        return locales.pick(product.getTranslations(), ProductTranslation::getLocale, locale)
                .map(ProductTranslation::getName).orElse("상품 #" + product.getId());
    }

    private static ApiException unavailable(String field, String message) {
        return new ApiException(ErrorCode.ORDER_ITEM_UNAVAILABLE, message,
                List.of(new FieldErrorItem(field, message)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}

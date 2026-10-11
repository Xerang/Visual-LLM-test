package com.bozor.order;

import com.bozor.auth.LoginUser;
import com.bozor.common.web.PageResponse;
import com.bozor.order.OrderEnums.Status;
import com.bozor.order.dto.OrderDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 주문: 구매자(/orders)와 판매자(/seller/...) (docs/03-api.md 5.8) */
@RestController
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/v1/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.OrderResponse place(@LoginUser Long userId, @Valid @RequestBody OrderDtos.PlaceRequest request) {
        return orderService.place(userId, request);
    }

    @GetMapping("/api/v1/orders")
    public PageResponse<OrderDtos.OrderResponse> myOrders(@LoginUser Long userId,
                                                         @RequestParam(required = false) Status status,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        return orderService.listMine(userId, status, page, size);
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public OrderDtos.OrderResponse myOrder(@LoginUser Long userId, @PathVariable Long orderId) {
        return orderService.getMine(orderId, userId);
    }

    @PostMapping("/api/v1/orders/{orderId}/cancel")
    public OrderDtos.OrderResponse cancel(@LoginUser Long userId, @PathVariable Long orderId) {
        return orderService.cancel(orderId, userId);
    }

    @GetMapping("/api/v1/seller/stores/{storeId}/orders")
    public PageResponse<OrderDtos.OrderResponse> storeOrders(@LoginUser Long userId, @PathVariable Long storeId,
                                                            @RequestParam(required = false) Status status,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return orderService.listForStore(storeId, userId, status, page, size);
    }

    @GetMapping("/api/v1/seller/orders/{orderId}")
    public OrderDtos.OrderResponse storeOrder(@LoginUser Long userId, @PathVariable Long orderId) {
        return orderService.getForSeller(orderId, userId);
    }

    @PostMapping("/api/v1/seller/orders/{orderId}/status")
    public OrderDtos.OrderResponse changeStatus(@LoginUser Long userId, @PathVariable Long orderId,
                                                @Valid @RequestBody OrderDtos.SellerStatusRequest request) {
        return orderService.changeStatus(orderId, userId, request);
    }
}

package com.bozor.store;

import com.bozor.auth.LoginUser;
import com.bozor.store.dto.StoreRequest;
import com.bozor.store.dto.StoreResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 판매자: 내 상점 관리 (docs/03-api.md 5.4) */
@RestController
@RequestMapping("/api/v1/seller/stores")
public class SellerStoreController {
    private final StoreService storeService;

    public SellerStoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    public List<StoreResponse> myStores(@LoginUser Long userId) {
        return storeService.listMine(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreResponse create(@LoginUser Long userId, @Valid @RequestBody StoreRequest request) {
        return storeService.create(userId, request);
    }

    @GetMapping("/{storeId}")
    public StoreResponse get(@LoginUser Long userId, @PathVariable Long storeId) {
        return storeService.get(storeId, userId);
    }

    @PutMapping("/{storeId}")
    public StoreResponse update(@LoginUser Long userId, @PathVariable Long storeId,
                                @Valid @RequestBody StoreRequest request) {
        return storeService.update(storeId, userId, request);
    }

    @PostMapping("/{storeId}/status")
    public StoreResponse changeStatus(@LoginUser Long userId, @PathVariable Long storeId,
                                      @Valid @RequestBody StatusRequest request) {
        return storeService.changeStatus(storeId, userId, request.status());
    }

    public record StatusRequest(@NotNull StoreStatus status) {}
}

package com.bozor.product;

import com.bozor.auth.LoginUser;
import com.bozor.common.web.PageResponse;
import com.bozor.product.dto.ProductInput;
import com.bozor.product.dto.SellerProductResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자: 상품 관리 (docs/03-api.md 5.6).
 * 수정 폼(PUT), 상태 변경, 재고 변경을 따로 둔다. 목록 화면의 [+][−]·발행 버튼이 한 가지 일만 하는 API를 부르게 하기 위해서다.
 */
@RestController
@RequestMapping("/api/v1/seller")
public class SellerProductController {
    private final ProductService productService;

    public SellerProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/stores/{storeId}/products")
    public PageResponse<SellerProductResponse> list(@LoginUser Long userId, @PathVariable Long storeId,
                                                    @RequestParam(required = false) ProductStatus status,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return productService.list(storeId, userId, status, page, size);
    }

    @PostMapping("/stores/{storeId}/products")
    @ResponseStatus(HttpStatus.CREATED)
    public SellerProductResponse create(@LoginUser Long userId, @PathVariable Long storeId,
                                        @Valid @RequestBody ProductInput input) {
        return productService.createDirect(storeId, userId, input);
    }

    @GetMapping("/products/{productId}")
    public SellerProductResponse get(@LoginUser Long userId, @PathVariable Long productId) {
        return productService.get(productId, userId);
    }

    @PutMapping("/products/{productId}")
    public SellerProductResponse update(@LoginUser Long userId, @PathVariable Long productId,
                                        @Valid @RequestBody ProductInput input) {
        return productService.update(productId, userId, input);
    }

    @PostMapping("/products/{productId}/status")
    public SellerProductResponse changeStatus(@LoginUser Long userId, @PathVariable Long productId,
                                              @Valid @RequestBody StatusRequest request) {
        return productService.changeStatus(productId, userId, request.status());
    }

    /** stock_quantity 를 null 로 보내면 "재고 관리 안 함". */
    @PostMapping("/products/{productId}/stock")
    public SellerProductResponse changeStock(@LoginUser Long userId, @PathVariable Long productId,
                                             @Valid @RequestBody StockRequest request) {
        return productService.changeStock(productId, userId, request.stockQuantity());
    }

    @DeleteMapping("/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@LoginUser Long userId, @PathVariable Long productId) {
        productService.delete(productId, userId);
    }

    public record StatusRequest(@NotNull ProductStatus status) {}

    public record StockRequest(@DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal stockQuantity) {}
}

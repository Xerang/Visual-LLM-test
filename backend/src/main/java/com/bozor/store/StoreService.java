package com.bozor.store;

import com.bozor.catalog.MarketRepository;
import com.bozor.common.error.ApiException;
import com.bozor.common.error.ErrorCode;
import com.bozor.image.Image;
import com.bozor.image.ImageService;
import com.bozor.store.dto.StoreRequest;
import com.bozor.store.dto.StoreResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자의 상점 관리. 모든 메서드는 "내 상점"인지 먼저 확인한다. */
@Service
@Transactional
public class StoreService {
    private final StoreRepository stores;
    private final MarketRepository markets;
    private final ImageService imageService;

    public StoreService(StoreRepository stores, MarketRepository markets, ImageService imageService) {
        this.stores = stores;
        this.markets = markets;
        this.imageService = imageService;
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> listMine(Long userId) {
        return stores.findByOwnerIdOrderByIdAsc(userId).stream().map(this::toResponse).toList();
    }

    /** slug 를 비우면 store-{id} 로 만든다. 새 상점은 DRAFT 상태다. */
    public StoreResponse create(Long userId, StoreRequest request) {
        validateReferences(userId, request);
        if (request.slug() != null && stores.existsBySlug(request.slug())) {
            throw slugTaken();
        }
        String slug = request.slug() != null ? request.slug() : "tmp-" + UUID.randomUUID().toString().substring(0, 12);
        Store store = saveAndFlush(new Store(userId, slug, request));
        if (request.slug() == null) {
            store.changeSlug("store-" + store.getId());
        }
        return toResponse(store);
    }

    @Transactional(readOnly = true)
    public StoreResponse get(Long storeId, Long userId) {
        return toResponse(getOwned(storeId, userId));
    }

    /** 상점 설정 폼 저장(전체 교체). slug 를 비우면 기존 주소를 유지한다. */
    public StoreResponse update(Long storeId, Long userId, StoreRequest request) {
        Store store = getOwned(storeId, userId);
        validateReferences(userId, request);
        if (request.slug() != null && !request.slug().equals(store.getSlug())) {
            if (stores.existsBySlug(request.slug())) throw slugTaken();
            store.changeSlug(request.slug());
        }
        store.apply(request);
        if (store.getStatus() == StoreStatus.ACTIVE && !store.hasContact()) {
            throw new ApiException(ErrorCode.STORE_CONTACT_REQUIRED, "공개 중인 상점은 WhatsApp 또는 전화번호가 있어야 합니다.");
        }
        return toResponse(saveAndFlush(store));
    }

    /** 공개(ACTIVE)·숨김(HIDDEN) 전환. 공개하려면 구매자가 연락할 수단이 있어야 한다. */
    public StoreResponse changeStatus(Long storeId, Long userId, StoreStatus status) {
        if (status == StoreStatus.DRAFT) {
            throw ApiException.invalid("status", "ACTIVE 또는 HIDDEN 만 고를 수 있습니다.");
        }
        Store store = getOwned(storeId, userId);
        if (status == StoreStatus.ACTIVE && !store.hasContact()) {
            throw new ApiException(ErrorCode.STORE_CONTACT_REQUIRED, "상점을 공개하려면 WhatsApp 또는 전화번호를 먼저 입력해 주세요.");
        }
        store.changeStatus(status);
        return toResponse(store);
    }

    /** 없으면 404, 남의 상점이면 403. 상점 하위 기능(스캔·상품·주문)이 모두 이걸로 권한을 확인한다. */
    @Transactional(readOnly = true)
    public Store getOwned(Long storeId, Long userId) {
        Store store = stores.findById(storeId).orElseThrow(() -> ApiException.notFound("상점"));
        if (!store.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "내 상점이 아닙니다.");
        }
        return store;
    }

    @Transactional(readOnly = true)
    public StoreResponse toResponse(Store store) {
        List<Long> imageIds = new ArrayList<>();
        if (store.getLogoImageId() != null) imageIds.add(store.getLogoImageId());
        if (store.getBannerImageId() != null) imageIds.add(store.getBannerImageId());
        Map<Long, Image> images = imageService.findAll(imageIds);
        StoreResponse.MarketRef market = store.getMarketId() == null ? null
                : markets.findById(store.getMarketId())
                        .map(m -> new StoreResponse.MarketRef(m.getId(), m.getName())).orElse(null);
        return new StoreResponse(store.getId(), store.getSlug(), store.getName(), store.getDescription(),
                store.getTemplate().name(), store.getThemeColor(),
                imageOrNull(images.get(store.getLogoImageId())), imageOrNull(images.get(store.getBannerImageId())),
                market, store.getLocationDetail(), store.getAddressText(), store.getLandmark(),
                store.getContactPhone(), store.getWhatsappPhone(), store.getInstagramHandle(),
                store.getTelegramUsername(), store.getStatus().name(), store.getCreatedAt(), store.getUpdatedAt());
    }

    private void validateReferences(Long userId, StoreRequest request) {
        if (request.logoImageId() != null) {
            imageService.requireOwned(List.of(request.logoImageId()), userId, "logo_image_id");
        }
        if (request.bannerImageId() != null) {
            imageService.requireOwned(List.of(request.bannerImageId()), userId, "banner_image_id");
        }
        if (request.marketId() != null && !markets.existsById(request.marketId())) {
            throw ApiException.invalid("market_id", "없는 시장입니다.");
        }
    }

    private Store saveAndFlush(Store store) {
        try {
            return stores.saveAndFlush(store);
        } catch (DataIntegrityViolationException e) {
            throw slugTaken();   // 동시에 같은 주소로 만든 경우
        }
    }

    private static ApiException slugTaken() {
        return new ApiException(ErrorCode.STORE_SLUG_TAKEN, "이미 사용 중인 상점 주소입니다. 다른 주소를 입력해 주세요.");
    }

    /** 판매자 화면이므로 비공개(DRAFT·HIDDEN) 상점의 로고·배너도 보이도록 서명 주소를 쓴다. */
    private com.bozor.image.ImageResponse imageOrNull(Image image) {
        return image == null ? null : imageService.signedResponse(image);
    }
}

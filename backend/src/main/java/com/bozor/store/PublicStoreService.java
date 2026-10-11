package com.bozor.store;

import com.bozor.catalog.Category;
import com.bozor.catalog.CategoryService;
import com.bozor.catalog.MarketRepository;
import com.bozor.common.error.ApiException;
import com.bozor.common.web.PageResponse;
import com.bozor.common.web.Pages;
import com.bozor.image.ImageService;
import com.bozor.store.dto.PublicStoreDetail;
import com.bozor.store.dto.PublicStoreSummary;
import com.bozor.store.dto.StoreResponse;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 구매자용 상점 조회. ACTIVE 상점만 보여주고, 나머지는 404로 숨긴다. */
@Service
@Transactional(readOnly = true)
public class PublicStoreService {
    private final StoreRepository stores;
    private final MarketRepository markets;
    private final CategoryService categoryService;
    private final NamedParameterJdbcTemplate jdbc;

    public PublicStoreService(StoreRepository stores, MarketRepository markets, CategoryService categoryService,
                              NamedParameterJdbcTemplate jdbc) {
        this.stores = stores;
        this.markets = markets;
        this.categoryService = categoryService;
        this.jdbc = jdbc;
    }

    public PageResponse<PublicStoreSummary> list(Long marketId, String q, Integer page, Integer size) {
        Page<Store> result = stores.searchActive(marketId, q == null ? "" : q.strip(), Pages.of(page, size));
        Map<Long, Long> counts = publishedCounts(result.getContent().stream().map(Store::getId).toList());
        return PageResponse.from(result, s -> new PublicStoreSummary(s.getSlug(), s.getName(), s.getTemplate().name(),
                logoUrl(s), market(s), s.getLocationDetail(), counts.getOrDefault(s.getId(), 0L)));
    }

    /** 상점 화면 하나에 필요한 정보를 한 번에 준다. categories 는 필터 칩·가격표형 묶음 제목용. */
    public PublicStoreDetail detail(String slug, String locale) {
        Store s = requireActive(slug);
        List<PublicStoreDetail.CategoryCount> categories = jdbc.query("""
                        SELECT category_id, count(*) AS cnt FROM products
                        WHERE store_id = :storeId AND status = 'PUBLISHED' GROUP BY category_id
                        """, new MapSqlParameterSource("storeId", s.getId()),
                        (rs, i) -> Map.entry(rs.getLong("category_id"), rs.getLong("cnt")))
                .stream()
                .map(e -> {
                    Category c = categoryService.get(e.getKey());
                    return new PublicStoreDetail.CategoryCount(c.getId(), c.getCode(),
                            categoryService.nameOf(c, locale), e.getValue());
                })
                .sorted(Comparator.comparing(PublicStoreDetail.CategoryCount::code))
                .toList();
        long total = categories.stream().mapToLong(PublicStoreDetail.CategoryCount::productCount).sum();
        return new PublicStoreDetail(s.getSlug(), s.getName(), s.getDescription(), s.getTemplate().name(),
                s.getThemeColor(), logoUrl(s),
                s.getBannerImageId() == null ? null : ImageService.urlOf(s.getBannerImageId()),
                market(s), s.getLocationDetail(), s.getAddressText(), s.getLandmark(),
                new PublicStoreDetail.Contact(s.getContactPhone(), s.getWhatsappPhone(),
                        s.getInstagramHandle(), s.getTelegramUsername()),
                categories, total);
    }

    public Store requireActive(String slug) {
        return stores.findBySlugAndStatus(slug, StoreStatus.ACTIVE).orElseThrow(() -> ApiException.notFound("상점"));
    }

    private Map<Long, Long> publishedCounts(List<Long> storeIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (storeIds.isEmpty()) return counts;
        jdbc.query("""
                SELECT store_id, count(*) AS cnt FROM products
                WHERE store_id IN (:ids) AND status = 'PUBLISHED' GROUP BY store_id
                """, new MapSqlParameterSource("ids", storeIds),
                rs -> { counts.put(rs.getLong("store_id"), rs.getLong("cnt")); });
        return counts;
    }

    private StoreResponse.MarketRef market(Store s) {
        return s.getMarketId() == null ? null : markets.findById(s.getMarketId())
                .map(m -> new StoreResponse.MarketRef(m.getId(), m.getName())).orElse(null);
    }

    private static String logoUrl(Store s) {
        return s.getLogoImageId() == null ? null : ImageService.thumbnailUrlOf(s.getLogoImageId());
    }
}

package com.bozor.product;

import com.bozor.catalog.Category;
import com.bozor.catalog.CategoryService;
import com.bozor.common.i18n.Locales;
import com.bozor.common.web.PageResponse;
import com.bozor.image.ImageService;
import com.bozor.product.dto.ProductSummary;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 구매자용 상품 검색. 언어 대체·2단계 카테고리·trigram 검색 때문에 SQL을 직접 쓴다.
 * 공통 조건: ACTIVE 상점의 PUBLISHED 상품만.
 *
 * 검색어는 ILIKE(부분 일치)와 trigram 유사도(%)를 함께 쓴다. ERD 검증에서 "살구"처럼 짧은 한글
 * 검색어의 유사도가 0.30으로 기본 임계값에 겨우 걸려, trigram 만으로는 빠질 수 있었다.
 */
@Repository
public class ProductQueryRepository {
    private static final String FROM = """
            FROM products p
            JOIN stores s ON s.id = p.store_id AND s.status = 'ACTIVE'
            LEFT JOIN markets m ON m.id = s.market_id
            JOIN categories c ON c.id = p.category_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final CategoryService categoryService;
    private final Locales locales;

    public ProductQueryRepository(NamedParameterJdbcTemplate jdbc, CategoryService categoryService, Locales locales) {
        this.jdbc = jdbc;
        this.categoryService = categoryService;
        this.locales = locales;
    }

    public enum Sort { RECENT, PRICE_ASC, PRICE_DESC, RELEVANCE }

    public record Condition(String q, Long categoryId, Long marketId, String storeSlug, Sort sort) {}

    public PageResponse<ProductSummary> search(Condition cond, String locale, int page, int size) {
        String wanted = locales.resolve(locale);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("locale", wanted)
                .addValue("defaultLocale", locales.defaultLocale())
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        StringBuilder where = new StringBuilder("WHERE p.status = 'PUBLISHED'\n");
        String q = cond.q() == null ? "" : cond.q().strip();
        if (!q.isEmpty()) {
            where.append("""
                    AND EXISTS (SELECT 1 FROM product_translations x WHERE x.product_id = p.id
                                AND (x.name ILIKE :pattern OR x.name % :q))
                    """);
            params.addValue("q", q).addValue("pattern", "%" + escapeLike(q) + "%");
        }
        if (cond.categoryId() != null) {
            where.append("AND (c.id = :categoryId OR c.parent_id = :categoryId)\n");   // 대분류면 소분류까지
            params.addValue("categoryId", cond.categoryId());
        }
        if (cond.marketId() != null) {
            where.append("AND s.market_id = :marketId\n");
            params.addValue("marketId", cond.marketId());
        }
        if (cond.storeSlug() != null) {
            where.append("AND s.slug = :storeSlug\n");
            params.addValue("storeSlug", cond.storeSlug());
        }

        Sort sort = cond.sort() != null ? cond.sort() : q.isEmpty() ? Sort.RECENT : Sort.RELEVANCE;
        if (sort == Sort.RELEVANCE && q.isEmpty()) sort = Sort.RECENT;
        String orderBy = switch (sort) {
            case PRICE_ASC -> "p.price ASC, p.id DESC";
            case PRICE_DESC -> "p.price DESC, p.id DESC";
            case RELEVANCE -> "(SELECT max(similarity(x.name, :q)) FROM product_translations x "
                    + "WHERE x.product_id = p.id) DESC, p.id DESC";
            case RECENT -> "p.published_at DESC, p.id DESC";
        };

        Long total = jdbc.queryForObject("SELECT count(*) " + FROM + where, params, Long.class);
        List<ProductSummary> items = jdbc.query(selectSummary() + FROM + translationJoin() + where
                + "ORDER BY " + orderBy + "\nLIMIT :limit OFFSET :offset", params, this::mapSummary);
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    /** 상세 화면용 한 건. 공개 조건을 만족하지 않으면 비어 있다. */
    public Optional<Row> findPublic(Long productId, String locale) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("locale", locales.resolve(locale))
                .addValue("defaultLocale", locales.defaultLocale())
                .addValue("id", productId);
        List<Row> rows = jdbc.query(selectSummary()
                        + ", t.description, t.is_auto_translated, s.id AS store_id, s.location_detail "
                        + FROM + translationJoin() + "WHERE p.status = 'PUBLISHED' AND p.id = :id", params,
                (rs, i) -> new Row(mapSummary(rs, i), rs.getString("description"), rs.getBoolean("is_auto_translated"),
                        rs.getLong("store_id"), rs.getString("location_detail")));
        return rows.stream().findFirst();
    }

    public List<Long> imageIdsOf(Long productId) {
        return jdbc.queryForList("SELECT image_id FROM product_images WHERE product_id = :id ORDER BY sort_order, image_id",
                new MapSqlParameterSource("id", productId), Long.class);
    }

    public record Row(ProductSummary summary, String description, boolean autoTranslated, Long storeId,
                      String locationDetail) {}

    private static String selectSummary() {
        return """
                SELECT p.id, p.price, p.currency, p.price_type, p.unit, p.stock_quantity, p.category_id,
                       t.name, t.locale, s.id AS store_id_brief, s.slug AS store_slug, s.name AS store_name,
                       m.name AS market_name,
                       (SELECT pi.image_id FROM product_images pi WHERE pi.product_id = p.id
                        ORDER BY pi.sort_order, pi.image_id LIMIT 1) AS cover_image_id
                """;
    }

    /** 요청 언어 → 기본 언어 → 아무 언어 순으로 번역 하나를 붙인다. */
    private static String translationJoin() {
        return """
                JOIN LATERAL (
                    SELECT pt.name, pt.locale, pt.description, pt.is_auto_translated FROM product_translations pt
                    WHERE pt.product_id = p.id
                    ORDER BY (pt.locale = :locale) DESC, (pt.locale = :defaultLocale) DESC, pt.locale
                    LIMIT 1) t ON true
                """;
    }

    private ProductSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        Category category = categoryService.get(rs.getLong("category_id"));
        long cover = rs.getLong("cover_image_id");
        String thumbnail = rs.wasNull() ? null : ImageService.thumbnailUrlOf(cover);
        return new ProductSummary(rs.getLong("id"), rs.getString("name"), rs.getString("locale"),
                rs.getBigDecimal("price"), rs.getString("currency").strip(), rs.getString("price_type"),
                rs.getString("unit"), (BigDecimal) rs.getObject("stock_quantity"), thumbnail,
                new ProductSummary.CategoryBrief(category.getId(), category.getCode(),
                        categoryService.nameOf(category, rs.getString("locale"))),
                new ProductSummary.StoreBrief(rs.getLong("store_id_brief"), rs.getString("store_slug"),
                        rs.getString("store_name"), rs.getString("market_name")));
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

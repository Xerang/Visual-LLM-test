package com.bozor.catalog;

import com.bozor.common.error.ApiException;
import com.bozor.common.i18n.Locales;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리 규칙(docs/02-erd.md 카테고리 트리 v1).
 * 상품과 AI 후보는 말단(자식이 없는) 카테고리만 가리킨다.
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categories;
    private final Locales locales;

    public CategoryService(CategoryRepository categories, Locales locales) {
        this.categories = categories;
        this.locales = locales;
    }

    public List<CategoryNode> tree(String locale) {
        List<Category> all = categories.findByActiveTrueOrderBySortOrderAscIdAsc();
        Map<Long, List<Category>> childrenByParent = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));
        List<CategoryNode> roots = new ArrayList<>();
        for (Category root : all) {
            if (root.getParentId() != null) continue;
            List<CategoryNode> children = childrenByParent.getOrDefault(root.getId(), List.of()).stream()
                    .map(c -> new CategoryNode(c.getId(), c.getCode(), nameOf(c, locale), c.getDefaultUnit(), List.of()))
                    .toList();
            roots.add(new CategoryNode(root.getId(), root.getCode(), nameOf(root, locale), root.getDefaultUnit(), children));
        }
        return roots;
    }

    /** 활성 말단 카테고리. AI 서버에 허용 목록으로 보낸다. */
    public List<Category> activeLeaves() {
        List<Category> all = categories.findByActiveTrueOrderBySortOrderAscIdAsc();
        Set<Long> parents = all.stream().map(Category::getParentId).filter(p -> p != null).collect(Collectors.toSet());
        return all.stream().filter(c -> !parents.contains(c.getId())).toList();
    }

    /** 상품·후보에 쓸 수 있는 카테고리인지 확인한다. field 는 오류 응답에 표시할 요청 경로. */
    public Category requireLeaf(Long id, String field) {
        Category category = categories.findById(id)
                .filter(Category::isActive)
                .orElseThrow(() -> ApiException.invalid(field, "없는 카테고리입니다."));
        if (categories.existsByParentId(category.getId())) {
            throw ApiException.invalid(field, "세부 카테고리(말단)를 골라 주세요.");
        }
        return category;
    }

    public Category get(Long id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("카테고리"));
    }

    public String nameOf(Category category, String locale) {
        return locales.pick(category.getTranslations(), CategoryTranslation::getLocale, locale)
                .map(CategoryTranslation::getName)
                .orElse(category.getCode());
    }

    public CategoryRef ref(Category category, String locale) {
        return new CategoryRef(category.getId(), category.getCode(), nameOf(category, locale), category.getDefaultUnit());
    }

    public record CategoryNode(Long id, String code, String name, SaleUnit defaultUnit, List<CategoryNode> children) {}

    /** 다른 응답 안에 넣는 카테고리 요약. */
    public record CategoryRef(Long id, String code, String name, SaleUnit defaultUnit) {}
}

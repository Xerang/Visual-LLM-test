package com.bozor.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** 목록 응답 형식: { items, page, size, total_elements, total_pages } */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int totalPages = size == 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResponse<>(items, page, size, total, totalPages);
    }
}

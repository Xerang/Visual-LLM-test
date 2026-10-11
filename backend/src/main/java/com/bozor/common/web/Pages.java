package com.bozor.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** page·size 파라미터를 안전한 범위로 맞춘다(size 1~50). */
public final class Pages {
    public static final int MAX_SIZE = 50;

    private Pages() {}

    public static PageRequest of(Integer page, Integer size, Sort sort) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, MAX_SIZE);
        return PageRequest.of(p, s, sort);
    }

    public static PageRequest of(Integer page, Integer size) {
        return of(page, size, Sort.unsorted());
    }
}

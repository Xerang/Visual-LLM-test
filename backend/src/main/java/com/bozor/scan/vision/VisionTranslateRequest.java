package com.bozor.scan.vision;

import java.util.List;

/** 판매자가 확정한 상품명·설명(source_locale)을 target_locales 로 번역해 달라는 요청. */
public record VisionTranslateRequest(String sourceLocale, List<String> targetLocales, String name,
                                     String description, String categoryName) {}

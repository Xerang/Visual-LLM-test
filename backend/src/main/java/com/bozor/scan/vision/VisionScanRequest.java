package com.bozor.scan.vision;

import java.util.List;

/**
 * FastAPI POST /v1/scan 요청(docs/03-api.md 7장).
 * categories 는 DB의 활성 말단 카테고리에서 만든다 → 카테고리 목록을 DB 한 곳에서만 관리한다.
 */
public record VisionScanRequest(
        List<byte[]> images,
        String outputLocale,
        String captionText,
        List<CategoryOption> categories,
        int maxCandidates) {

    public record CategoryOption(String code, String name) {}
}

package com.bozor.scan.vision;

import java.util.List;

/** 번역 초안. 저장할 때 is_auto_translated=true 로 표시한다. */
public record VisionTranslateResult(String model, String promptVersion, List<Translation> translations) {
    public record Translation(String locale, String name, String description) {}
}

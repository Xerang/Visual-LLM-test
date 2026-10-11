package com.bozor.scan.vision;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

/** FastAPI 응답. raw 는 scan_jobs.raw_result 에 그대로 저장하는 원본 JSON 이다. */
public record VisionScanResult(
        JsonNode raw,
        String model,
        String promptVersion,
        int elapsedMs,
        List<Candidate> candidates,
        List<String> imageNotes) {

    public record Candidate(String name, String description, String categoryCode,
                            List<String> visibleText, List<String> reviewNotes, BigDecimal suggestedPrice) {}
}

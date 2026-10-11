package com.bozor.poc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Gemini scan JSON from FastAPI, forwarded to the browser. */
public final class AnalysisResponse {
    public List<ProductCandidate> candidates;
    @JsonProperty("review_notes") public List<String> reviewNotes;
    public String source;
    public String model;
    @JsonProperty("prompt_version") public String promptVersion;
    @JsonProperty("elapsed_ms") public long elapsedMs;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    @JsonProperty("record_id") public Long recordId;

    public AnalysisResponse() {}

}

package com.bozor.image;

import com.fasterxml.jackson.annotation.JsonInclude;

/** duplicate 는 업로드 응답에만 들어간다. */
public record ImageResponse(Long id, String url, String thumbnailUrl, int width, int height,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean duplicate) {}

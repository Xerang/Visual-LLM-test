package com.bozor.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/** 배포용 화면 경로 규칙(스프링 없이 도는 단위 테스트) */
class SpaFallbackResolverTest {
    @TempDir Path dir;

    Resource resolve(String path) throws IOException {
        Files.writeString(dir.resolve("index.html"), "<!doctype html>");
        Files.createDirectories(dir.resolve("assets"));
        Files.writeString(dir.resolve("assets/index-abc.js"), "console.log(1)");
        return new FrontendResourceConfig.SpaFallbackResolver().getResource(path, new FileSystemResource(dir.toString() + "/"));
    }

    @Test
    void existingFileIsServedAsIs() throws IOException {
        assertThat(resolve("assets/index-abc.js").getFilename()).isEqualTo("index-abc.js");
    }

    @Test
    void screenAddressesFallBackToIndexHtml() throws IOException {
        assertThat(resolve("seller/stores/1").getFilename()).isEqualTo("index.html");
        assertThat(resolve("s/farhod-dried-fruit").getFilename()).isEqualTo("index.html");
    }

    @Test
    void missingApiAndMissingFilesStay404() throws IOException {
        assertThat(resolve("api/v1/nope")).isNull();
        assertThat(resolve("assets/old-hash.js")).isNull();
    }
}

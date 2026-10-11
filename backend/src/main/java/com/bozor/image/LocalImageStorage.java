package com.bozor.image;

import com.bozor.common.AppProperties;
import com.bozor.common.error.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** app.storage.local-dir 아래에 key 경로 그대로 파일을 저장한다. */
@Component
public class LocalImageStorage implements ImageStorage {
    private final Path root;

    public LocalImageStorage(AppProperties properties) {
        this.root = Path.of(properties.storage().localDir()).toAbsolutePath().normalize();
    }

    @Override
    public void save(String key, byte[] bytes) {
        Path path = resolve(key);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("사진 파일을 저장하지 못했습니다: " + key, e);
        }
    }

    @Override
    public byte[] load(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (NoSuchFileException e) {
            throw ApiException.notFound("사진 파일");
        } catch (IOException e) {
            throw new UncheckedIOException("사진 파일을 읽지 못했습니다: " + key, e);
        }
    }

    /** key가 저장 폴더 밖을 가리키지 못하게 막는다. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("잘못된 저장 경로: " + key);
        }
        return path;
    }
}

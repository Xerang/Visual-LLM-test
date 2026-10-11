package com.bozor.image;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {
    Optional<Image> findFirstByUploaderIdAndSha256(Long uploaderId, String sha256);
}

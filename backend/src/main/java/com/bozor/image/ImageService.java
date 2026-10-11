package com.bozor.image;

import com.bozor.common.error.ApiException;
import com.bozor.common.error.ErrorCode;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageService {
    private final ImageRepository images;
    private final ImageStorage storage;
    private final ImageProcessor processor;
    private final ImageUrlSigner signer;
    private final NamedParameterJdbcTemplate jdbc;

    public ImageService(ImageRepository images, ImageStorage storage, ImageProcessor processor,
                        ImageUrlSigner signer, NamedParameterJdbcTemplate jdbc) {
        this.images = images;
        this.storage = storage;
        this.processor = processor;
        this.signer = signer;
        this.jdbc = jdbc;
    }

    /** 같은 사람이 같은 파일을 다시 올리면 새로 저장하지 않고 기존 사진을 돌려준다(SNS 일괄 등록 중복 방지). */
    @Transactional
    public Upload upload(Long userId, MultipartFile file) {
        byte[] original = read(file);
        String sha256 = sha256(original);
        var existing = images.findFirstByUploaderIdAndSha256(userId, sha256);
        if (existing.isPresent()) {
            return new Upload(existing.get(), true);
        }
        ImageProcessor.Processed processed = processor.process(original);
        String base = "images/" + YearMonth.now(ZoneOffset.UTC) + "/" + UUID.randomUUID();
        storage.save(base + ".jpg", processed.jpeg());
        storage.save(base + "_thumb.jpg", processed.thumbnail());
        Image image = images.save(new Image(userId, base + ".jpg", base + "_thumb.jpg", "image/jpeg",
                processed.width(), processed.height(), processed.jpeg().length, sha256,
                sanitizeFilename(file.getOriginalFilename())));
        return new Upload(image, false);
    }

    /** 서버 안에서만 쓰는 읽기(스캔 워커가 AI 서버로 보낼 때). 접근 검사를 하지 않는다. */
    @Transactional(readOnly = true)
    public StoredFile file(Long imageId, boolean thumbnail) {
        Image image = images.findById(imageId).orElseThrow(() -> ApiException.notFound("사진"));
        String key = thumbnail && image.getThumbnailKey() != null ? image.getThumbnailKey() : image.getStorageKey();
        return new StoredFile(storage.load(key), image.getMimeType());
    }

    /**
     * 브라우저에 사진 파일을 내준다(임시 결정 #9).
     * 공개 사진(판매 중 상품, 공개 상점의 로고·배너)이거나 주인에게 준 서명 주소가 맞을 때만 내준다.
     * 그 밖에는 사진이 있는지조차 알리지 않으려고 404로 답한다.
     */
    @Transactional(readOnly = true)
    public ServedFile serve(Long imageId, boolean thumbnail, Long exp, String sig) {
        boolean signed = signer.verify(imageId, exp, sig);
        if (!signed && !isPublic(imageId)) {
            throw ApiException.notFound("사진");
        }
        return new ServedFile(file(imageId, thumbnail), !signed);
    }

    /** 판매 중(PUBLISHED) 상품이 공개(ACTIVE) 상점에 있거나, 공개 상점의 로고·배너면 공개 사진이다. */
    boolean isPublic(Long imageId) {
        Boolean result = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM product_images pi
                               JOIN products p ON p.id = pi.product_id
                               JOIN stores s ON s.id = p.store_id
                               WHERE pi.image_id = :id AND p.status = 'PUBLISHED' AND s.status = 'ACTIVE')
                    OR EXISTS (SELECT 1 FROM stores s
                               WHERE s.status = 'ACTIVE' AND (s.logo_image_id = :id OR s.banner_image_id = :id))
                """, new MapSqlParameterSource("id", imageId), Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    /** 주인에게 주는 응답(판매자 화면·업로드 결과)에 쓰는 서명 주소. */
    public ImageResponse signedResponse(Image image) {
        return signer.signedResponse(image);
    }

    /**
     * 요청에 들어온 사진 id들이 모두 ownerId가 올린 사진인지 확인하고, 요청 순서대로 돌려준다.
     * 남의 사진 id를 넣어 상품에 붙이는 것을 막는다.
     */
    @Transactional(readOnly = true)
    public List<Image> requireOwned(List<Long> ids, Long ownerId, String field) {
        List<Long> unique = new ArrayList<>(new LinkedHashSet<>(ids));
        if (unique.size() != ids.size()) {
            throw ApiException.invalid(field, "같은 사진이 두 번 들어 있습니다.");
        }
        Map<Long, Image> found = images.findAllById(unique).stream()
                .collect(Collectors.toMap(Image::getId, Function.identity()));
        List<Image> result = new ArrayList<>();
        for (Long id : unique) {
            Image image = found.get(id);
            if (image == null || !image.getUploaderId().equals(ownerId)) {
                throw ApiException.invalid(field, "사용할 수 없는 사진입니다: " + id);
            }
            result.add(image);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<Long, Image> findAll(List<Long> ids) {
        return images.findAllById(ids).stream().collect(Collectors.toMap(Image::getId, Function.identity()));
    }

    /** 공개 화면용 주소. 프런트는 응답의 URL을 그대로 쓴다. S3로 바꾸면 이 두 메서드와 서명 방식만 바뀐다(임시 결정 #6). */
    public static String urlOf(Long imageId) {
        return "/api/v1/images/" + imageId + "/file";
    }

    public static String thumbnailUrlOf(Long imageId) {
        return urlOf(imageId) + "?size=thumb";
    }

    /** 공개 화면(구매자용 상품·상점)에 쓰는 응답. 공개 사진에만 쓴다. */
    public static ImageResponse response(Image image) {
        return new ImageResponse(image.getId(), urlOf(image.getId()), thumbnailUrlOf(image.getId()),
                image.getWidth(), image.getHeight(), null);
    }

    private static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.IMAGE_INVALID, "사진을 선택해 주세요.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.IMAGE_INVALID, "사진을 읽을 수 없습니다.");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sanitizeFilename(String name) {
        if (name == null) return null;
        String cleaned = name.replaceAll("[\\\\/\\p{Cntrl}]", "_");
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }

    public record Upload(Image image, boolean duplicate) {}

    public record StoredFile(byte[] bytes, String mimeType) {}

    /** publicImage: 공개 사진이면 true(브라우저·중간 캐시가 오래 보관해도 됨). */
    public record ServedFile(StoredFile file, boolean publicImage) {}
}

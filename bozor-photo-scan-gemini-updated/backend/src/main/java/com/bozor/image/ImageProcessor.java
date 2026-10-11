package com.bozor.image;

import com.bozor.common.error.ApiException;
import com.bozor.common.error.ErrorCode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

/**
 * 업로드한 사진을 저장용으로 정규화한다.
 * 1) 실제 JPEG/PNG/WEBP인지 파일 내용으로 확인(브라우저가 보낸 MIME은 믿지 않는다)
 * 2) 픽셀 수 상한 검사(압축 폭탄 방지) — 전체를 디코딩하기 전에 크기만 읽는다
 * 3) EXIF 방향 보정 → 긴 변 2048px 이하로 축소 → JPEG 재인코딩(EXIF·촬영 위치 제거)
 * 4) 긴 변 400px 썸네일 생성(느린 회선에서 목록 화면용, 조사 §1)
 */
@Component
public class ImageProcessor {
    static final int MAX_EDGE = 2048;
    static final int THUMB_EDGE = 400;
    static final long MAX_PIXELS = 25_000_000L;
    private static final Set<String> FORMATS = Set.of("jpeg", "jpg", "png", "webp");

    public Processed process(byte[] original) {
        int[] size = readSize(original);
        try {
            double scale = Math.min(1.0, (double) MAX_EDGE / Math.max(size[0], size[1]));
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(original))
                    .scale(scale)
                    .useExifOrientation(true)
                    .addFilter(ImageProcessor::onWhite)
                    .outputFormat("jpg")
                    .outputQuality(0.88)
                    .toOutputStream(jpeg);

            BufferedImage normalized = ImageIO.read(new ByteArrayInputStream(jpeg.toByteArray()));
            double thumbScale = Math.min(1.0, (double) THUMB_EDGE / Math.max(normalized.getWidth(), normalized.getHeight()));
            ByteArrayOutputStream thumb = new ByteArrayOutputStream();
            Thumbnails.of(normalized).scale(thumbScale).outputFormat("jpg").outputQuality(0.8).toOutputStream(thumb);

            return new Processed(jpeg.toByteArray(), thumb.toByteArray(), normalized.getWidth(), normalized.getHeight());
        } catch (IOException | RuntimeException e) {
            throw new ApiException(ErrorCode.IMAGE_INVALID, "사진을 처리하지 못했습니다. 다른 사진으로 시도해 주세요.");
        }
    }

    private static int[] readSize(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new ApiException(ErrorCode.IMAGE_INVALID, "JPEG, PNG, WEBP 사진만 올릴 수 있습니다.");
            }
            ImageReader reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase())) {
                    throw new ApiException(ErrorCode.IMAGE_INVALID, "JPEG, PNG, WEBP 사진만 올릴 수 있습니다.");
                }
                reader.setInput(in);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) {
                    throw new ApiException(ErrorCode.IMAGE_INVALID, "해상도가 너무 큰 사진입니다.");
                }
                return new int[] {width, height};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ApiException(ErrorCode.IMAGE_INVALID, "사진을 읽을 수 없습니다.");
        }
    }

    /** 투명 배경(PNG·WEBP)을 흰색으로 채운다. JPEG에는 투명도가 없어 그대로 두면 검게 나온다. */
    private static BufferedImage onWhite(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }

    public record Processed(byte[] jpeg, byte[] thumbnail, int width, int height) {}
}

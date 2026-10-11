package com.bozor.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bozor.common.error.ApiException;
import com.bozor.common.error.ErrorCode;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** 사진 정규화 규칙(스프링 없이 도는 단위 테스트) */
class ImageProcessorTest {
    private final ImageProcessor processor = new ImageProcessor();

    @Test
    void largePhotoIsShrunkToMaxEdgeAndThumbnailIsMade() throws IOException {
        ImageProcessor.Processed out = processor.process(png(3000, 1000, Color.RED, false));
        assertThat(out.width()).isEqualTo(2048);
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(out.thumbnail()));
        assertThat(Math.max(thumb.getWidth(), thumb.getHeight())).isEqualTo(400);
        assertThat(isJpeg(out.jpeg())).isTrue();
    }

    @Test
    void smallPhotoIsNotEnlarged() throws IOException {
        ImageProcessor.Processed out = processor.process(png(320, 240, Color.BLUE, false));
        assertThat(out.width()).isEqualTo(320);
        assertThat(out.height()).isEqualTo(240);
    }

    @Test
    void transparentAreasBecomeWhiteNotBlack() throws IOException {
        ImageProcessor.Processed out = processor.process(png(100, 100, null, true));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(out.jpeg()));
        Color corner = new Color(image.getRGB(2, 2));
        assertThat(corner.getRed()).isGreaterThan(240);
        assertThat(corner.getGreen()).isGreaterThan(240);
        assertThat(corner.getBlue()).isGreaterThan(240);
    }

    @Test
    void notAnImageIsRejected() {
        assertThatThrownBy(() -> processor.process("hello".getBytes()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.IMAGE_INVALID);
    }

    /** 헤더만 거대한 해상도를 주장하는 파일(압축 폭탄)은 디코딩 전에 거절한다. */
    @Test
    void hugeResolutionIsRejectedBeforeDecoding() {
        assertThatThrownBy(() -> processor.process(pngHeaderOnly(6000, 5000)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("해상도");
    }

    private static byte[] png(int w, int h, Color color, boolean transparent) throws IOException {
        BufferedImage image = new BufferedImage(w, h, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        if (color != null) {
            var g = image.createGraphics();
            g.setColor(color);
            g.fillRect(0, 0, w, h);
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] pngHeaderOnly(int w, int h) {
        ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(w).putInt(h).put((byte) 8).put((byte) 2)
                .put((byte) 0).put((byte) 0).put((byte) 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        writeChunk(out, "IHDR", ihdr.array());
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        byte[] typeBytes = type.getBytes();
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length > 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
    }
}

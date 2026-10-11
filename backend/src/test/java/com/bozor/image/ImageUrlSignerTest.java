package com.bozor.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** 서명 주소 규칙(스프링 없이 도는 단위 테스트) */
class ImageUrlSignerTest {
    static final SecretKeySpec KEY = new SecretKeySpec(
            "test-secret-key-test-secret-key-32b".getBytes(StandardCharsets.US_ASCII), "HmacSHA256");
    static final Instant NOW = Instant.parse("2026-10-12T10:20:00Z");

    static ImageUrlSigner at(Instant now) {
        return new ImageUrlSigner(KEY, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void expiryIsAlignedToTheHourAndLastsOneToTwoHours() {
        long exp = expOf(at(NOW).signedResponse(image(7)).url());
        assertThat(exp).isEqualTo(Instant.parse("2026-10-12T12:00:00Z").getEpochSecond());
        // 같은 시간대 안에서는 주소가 바뀌지 않는다(폴링해도 브라우저 캐시가 유지됨)
        assertThat(at(NOW.plusSeconds(1500)).signedResponse(image(7)).url())
                .isEqualTo(at(NOW).signedResponse(image(7)).url());
    }

    @Test
    void validUntilExpiryOnly() {
        ImageUrlSigner signer = at(NOW);
        long exp = expOf(signer.signedResponse(image(7)).url());
        String sig = signer.sign(7, exp);
        assertThat(signer.verify(7, exp, sig)).isTrue();
        assertThat(at(Instant.ofEpochSecond(exp)).verify(7, exp, sig)).isTrue();
        assertThat(at(Instant.ofEpochSecond(exp + 1)).verify(7, exp, sig)).isFalse();
    }

    @Test
    void wrongPhotoMissingOrForeignSignatureIsRejected() {
        ImageUrlSigner signer = at(NOW);
        long exp = NOW.getEpochSecond() + 3600;
        String sig = signer.sign(7, exp);
        assertThat(signer.verify(8, exp, sig)).isFalse();
        assertThat(signer.verify(7, exp, null)).isFalse();
        assertThat(signer.verify(7, null, sig)).isFalse();
        ImageUrlSigner otherKey = new ImageUrlSigner(new SecretKeySpec(
                "another-secret-key-another-secret".getBytes(StandardCharsets.US_ASCII), "HmacSHA256"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(otherKey.verify(7, exp, sig)).isFalse();
    }

    static Image image(long id) {
        Image image = new Image();
        org.springframework.test.util.ReflectionTestUtils.setField(image, "id", id);
        return image;
    }

    static long expOf(String url) {
        String q = url.substring(url.indexOf("exp=") + 4);
        return Long.parseLong(q.substring(0, q.indexOf('&')));
    }
}

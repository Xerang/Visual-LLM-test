package com.bozor.image;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 비공개 사진(검수 전 스캔 사진, 초안·숨긴 상품 사진 등)에 기한이 있는 서명 주소를 붙인다(임시 결정 #9).
 *
 * 화면의 <img> 태그는 로그인 토큰을 보낼 수 없어서, 주인에게 주는 응답에만 서명 주소를 넣는다.
 * S3로 바꿀 때 쓰는 "서명 URL"과 같은 방식이라, 저장소를 바꿔도 화면은 그대로다.
 *
 * 만료 시각은 1시간 단위로 맞춘다. 폴링할 때마다 주소가 바뀌면 브라우저가 같은 사진을 매번 다시 받기 때문이다.
 * 그래서 서명 주소는 최소 1시간, 최대 2시간 동안 유효하다.
 */
@Component
public class ImageUrlSigner {
    static final Duration WINDOW = Duration.ofHours(1);

    private final SecretKey key;
    private final Clock clock;

    @Autowired
    public ImageUrlSigner(SecretKey jwtKey) {
        this(jwtKey, Clock.systemUTC());
    }

    ImageUrlSigner(SecretKey key, Clock clock) {
        this.key = key;
        this.clock = clock;
    }

    /** 주인에게 주는 사진 정보: 원본·썸네일 주소에 같은 서명을 붙인다. */
    public ImageResponse signedResponse(Image image) {
        long window = WINDOW.toSeconds();
        long exp = (clock.instant().getEpochSecond() / window + 2) * window;
        String query = "exp=" + exp + "&sig=" + sign(image.getId(), exp);
        String base = ImageService.urlOf(image.getId());
        return new ImageResponse(image.getId(), base + "?" + query, base + "?size=thumb&" + query,
                image.getWidth(), image.getHeight(), null);
    }

    /** 서명이 맞고 만료 전이면 true. 서명 비교는 시간 차이로 값이 새지 않게 상수 시간 비교를 쓴다. */
    public boolean verify(long imageId, Long exp, String sig) {
        if (exp == null || sig == null || exp < clock.instant().getEpochSecond()) {
            return false;
        }
        return MessageDigest.isEqual(sign(imageId, exp).getBytes(StandardCharsets.US_ASCII),
                sig.getBytes(StandardCharsets.US_ASCII));
    }

    String sign(long imageId, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal(("image:" + imageId + ":" + exp).getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}

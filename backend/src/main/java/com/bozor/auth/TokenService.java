package com.bozor.auth;

import com.bozor.common.AppProperties;
import com.bozor.user.User;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * 액세스 토큰 발급. 목 로그인(DevAuthController)과 나중에 붙일 실제 로그인이 모두 이 서비스를 쓴다.
 * 그래서 로그인 방식을 바꿔도 나머지 API와 프런트는 그대로다(임시 결정 #1, #8).
 */
@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final Duration ttl;

    public TokenService(JwtEncoder encoder, AppProperties properties) {
        this.encoder = encoder;
        this.ttl = properties.jwt().ttl();
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("bozor")
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new IssuedToken(token, ttl.toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {}
}

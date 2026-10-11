package com.bozor.auth;

import com.bozor.common.AppProperties;
import com.bozor.common.error.ErrorCode;
import com.bozor.common.error.Problems;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 권한 규칙(docs/03-api.md 2장).
 * - 공개: 기준 데이터, 상점·상품 조회, 사진 파일, dev 목 로그인
 * - 관리자: /api/v1/admin/**
 * - 나머지: 로그인 필요. 상점 주인·주문자 확인은 각 서비스에서 한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecretKey jwtKey(AppProperties properties) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET(app.jwt.secret)을 32바이트 이상으로 설정하세요.");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtKey) {
        return NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        // 토큰의 role 클레임(USER|ADMIN)을 ROLE_USER|ROLE_ADMIN 권한으로 바꾼다.
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);

        http.csrf(csrf -> csrf.disable())   // 쿠키가 아니라 Authorization 헤더를 쓰므로 CSRF 대상이 아니다
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/dev/**", "/api/v1/health", "/error",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/markets",
                                "/api/v1/stores", "/api/v1/stores/**", "/api/v1/products", "/api/v1/products/**",
                                "/api/v1/images/*/file").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) ->
                                writeProblem(res, objectMapper, ErrorCode.UNAUTHORIZED, "로그인이 필요하거나 로그인이 만료됐습니다."))
                        .accessDeniedHandler((req, res, ex) ->
                                writeProblem(res, objectMapper, ErrorCode.FORBIDDEN, "이 기능을 사용할 권한이 없습니다.")))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeProblem(res, objectMapper, ErrorCode.UNAUTHORIZED, "로그인이 필요합니다."))
                        .accessDeniedHandler((req, res, e) ->
                                writeProblem(res, objectMapper, ErrorCode.FORBIDDEN, "이 기능을 사용할 권한이 없습니다.")));
        return http.build();
    }

    /** 보안 필터의 오류도 컨트롤러 오류와 같은 형식(ProblemDetail + code)으로 응답한다. */
    private static void writeProblem(HttpServletResponse response, ObjectMapper objectMapper,
                                     ErrorCode code, String detail) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), Problems.of(code, detail));
    }
}

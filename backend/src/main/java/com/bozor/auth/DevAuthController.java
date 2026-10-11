package com.bozor.auth;

import com.bozor.common.error.ApiException;
import com.bozor.store.Store;
import com.bozor.store.StoreRepository;
import com.bozor.user.User;
import com.bozor.user.UserRepository;
import com.bozor.user.dto.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 목(mock) 로그인 — 임시 결정 #1.
 * app.auth.mode=mock(dev 프로필)일 때만 빈으로 등록된다. 그 밖에서는 /api/v1/dev/** 가 404다.
 * 실제 로그인을 붙일 때 이 클래스를 지우고, 같은 TokenService로 토큰을 발급하는 컨트롤러를 만든다.
 */
@RestController
@RequestMapping("/api/v1/dev")
@ConditionalOnProperty(prefix = "app.auth", name = "mode", havingValue = "mock")
public class DevAuthController {
    private final UserRepository users;
    private final StoreRepository stores;
    private final TokenService tokens;

    public DevAuthController(UserRepository users, StoreRepository stores, TokenService tokens) {
        this.users = users;
        this.stores = stores;
        this.tokens = tokens;
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<DevUser> users() {
        return users.findAllByOrderByIdAsc().stream()
                .map(u -> new DevUser(u.getId(), u.getDisplayName(), u.getRole().name(),
                        stores.findByOwnerIdOrderByIdAsc(u.getId()).stream().map(Store::getSlug).toList()))
                .toList();
    }

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = users.findById(request.userId()).orElseThrow(() -> ApiException.notFound("사용자"));
        TokenService.IssuedToken token = tokens.issue(user);
        return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), UserSummary.of(user));
    }

    public record LoginRequest(@NotNull Long userId) {}

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {}

    public record DevUser(Long id, String displayName, String role, List<String> storeSlugs) {}
}

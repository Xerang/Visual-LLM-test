package com.bozor.user;

import com.bozor.auth.LoginUser;
import com.bozor.common.error.ApiException;
import com.bozor.common.i18n.Locales;
import com.bozor.store.StoreRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보. 로직이 단순해 서비스 계층 없이 리포지토리를 바로 쓴다. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {
    private final UserRepository users;
    private final StoreRepository stores;

    public MeController(UserRepository users, StoreRepository stores) {
        this.users = users;
        this.stores = stores;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public MeResponse me(@LoginUser Long userId) {
        return toResponse(find(userId));
    }

    /** 이름·언어는 화면에서 둘 다 보내는 폼이라 PUT(전체 교체)으로 받는다. */
    @PutMapping
    @Transactional
    public MeResponse update(@LoginUser Long userId, @Valid @RequestBody UpdateMeRequest request) {
        User user = find(userId);
        user.updateProfile(request.displayName().strip(), request.locale());
        return toResponse(user);
    }

    private User find(Long userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("사용자"));
    }

    private MeResponse toResponse(User user) {
        List<MyStore> myStores = stores.findByOwnerIdOrderByIdAsc(user.getId()).stream()
                .map(s -> new MyStore(s.getId(), s.getSlug(), s.getName(), s.getStatus().name()))
                .toList();
        return new MeResponse(user.getId(), user.getPhoneNumber(), user.getDisplayName(), user.getLocale(),
                user.getRole().name(), myStores);
    }

    public record UpdateMeRequest(
            @NotBlank @Size(max = 50) String displayName,
            @NotNull @Pattern(regexp = Locales.PATTERN) String locale) {}

    public record MeResponse(Long id, String phoneNumber, String displayName, String locale, String role,
                             List<MyStore> stores) {}

    public record MyStore(Long id, String slug, String name, String status) {}
}

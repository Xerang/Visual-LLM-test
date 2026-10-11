package com.bozor.user.dto;

import com.bozor.user.User;

public record UserSummary(Long id, String displayName, String locale, String role) {
    public static UserSummary of(User user) {
        return new UserSummary(user.getId(), user.getDisplayName(), user.getLocale(), user.getRole().name());
    }
}

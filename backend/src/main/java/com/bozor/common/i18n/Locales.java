package com.bozor.common.i18n;

import com.bozor.common.AppProperties;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 언어 처리 규칙(docs/03-api.md 1장).
 * 요청 언어 → 앱 기본 언어(app.default-locale) → 아무 번역 순으로 고른다.
 */
@Component
public class Locales {
    public static final Set<String> SUPPORTED = Set.of("tg", "ru", "en", "ko");
    /** DTO 검증용 정규식. SUPPORTED 와 같은 값이어야 한다. */
    public static final String PATTERN = "tg|ru|en|ko";

    private final String defaultLocale;

    public Locales(AppProperties properties) {
        if (!SUPPORTED.contains(properties.defaultLocale())) {
            throw new IllegalStateException("app.default-locale 은 tg, ru, en, ko 중 하나여야 합니다.");
        }
        this.defaultLocale = properties.defaultLocale();
    }

    public String defaultLocale() {
        return defaultLocale;
    }

    /** 지원하지 않거나 비어 있으면 기본 언어. */
    public String resolve(String requested) {
        return requested != null && SUPPORTED.contains(requested) ? requested : defaultLocale;
    }

    /** 번역 목록에서 보여줄 하나를 고른다. */
    public <T> Optional<T> pick(Collection<T> items, Function<T, String> localeOf, String requested) {
        String wanted = resolve(requested);
        return items.stream().min(Comparator
                .comparing((T t) -> !localeOf.apply(t).equals(wanted))
                .thenComparing(t -> !localeOf.apply(t).equals(defaultLocale))
                .thenComparing(localeOf));
    }
}

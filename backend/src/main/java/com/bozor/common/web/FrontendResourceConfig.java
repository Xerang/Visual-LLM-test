package com.bozor.common.web;

import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 배포용 jar(mvn -Pwith-frontend)에 들어 있는 화면(classpath:/static)을 API와 같은 서버·같은 주소에서 서비스한다.
 * 개발 중에는 static 이 비어 있어서 아무것도 내주지 않는다(화면은 Vite 개발 서버가 맡는다).
 *
 * - 화면 주소(/seller/stores/1 등)로 바로 들어오거나 새로고침해도 index.html 을 준다. 어떤 화면인지는 React Router 가 고른다.
 * - /api/** 와 확장자가 있는 경로는 index.html 로 바꾸지 않는다. 없는 API·파일은 지금처럼 404 오류 형식으로 답한다.
 * - 파일 이름에 해시가 붙는 assets/ 는 1년 캐시, index.html 은 매번 새 버전 확인(no-cache).
 *   현지 모바일 회선이 느려서(조사 §1) 다시 받는 양을 줄이면서도, 새로 배포한 화면은 바로 보이게 하기 위해서다.
 *
 * Spring Boot 기본 정적 파일 설정(spring.web.resources.add-mappings)은 끄고 여기서만 등록한다.
 */
@Configuration
public class FrontendResourceConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /** 있는 파일은 그대로, 화면 주소는 index.html, 없는 API·파일은 null(→ 404). */
    static class SpaFallbackResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = super.getResource(resourcePath, location);
            if (requested != null) {
                return requested;
            }
            if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
                return null;
            }
            return super.getResource("index.html", location);
        }
    }
}

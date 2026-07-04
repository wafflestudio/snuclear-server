package com.wafflestudio.team8server.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * DB 스키마 문서(SchemaSpy) 접근 편의 설정.
 * Spring 은 하위 디렉터리의 index.html 을 자동 서빙하지 않으므로
 * `/schema`, `/schema/` 로 접근하면 실제 진입점인 `/schema/index.html` 로 리다이렉트한다.
 * 정적 파일 자체는 classpath:/static/schema/ 에서 서빙된다.
 */
@Configuration
class SchemaDocsConfig : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addRedirectViewController("/schema", "/schema/index.html")
        registry.addRedirectViewController("/schema/", "/schema/index.html")
    }
}

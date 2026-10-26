package com.pleos.backend.common

import com.pleos.backend.auth.AuthInterceptors
import com.pleos.backend.auth.PrincipalArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 클라이언트별 인증 경계.
 * - 폰 앱:   /api/v1 하위         사용자 토큰 (가입 POST /api/v1/users 제외)
 * - 차량 앱: /vehicle-api/v1 하위 차량 토큰   (등록 POST /vehicle-api/v1/vehicles 제외)
 */
@Configuration
class WebConfig(private val auth: AuthInterceptors) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(auth.user)
            .addPathPatterns("/api/v1/**")
            .excludePathPatterns("/api/v1/users")
        registry.addInterceptor(auth.vehicle)
            .addPathPatterns("/vehicle-api/v1/**")
            .excludePathPatterns("/vehicle-api/v1/vehicles")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(PrincipalArgumentResolver())
    }
}

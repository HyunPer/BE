package com.pleos.backend.common

import com.pleos.backend.auth.AuthUser
import com.pleos.backend.auth.AuthVehicle
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.models.GroupedOpenApi
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * FE 연결용 API 문서. Swagger UI: /swagger-ui.html, 스펙: /v3/api-docs/phone · /v3/api-docs/vehicle
 */
@Configuration
class OpenApiConfig {

    init {
        // 인증 주체 파라미터는 요청 값이 아니므로 문서에서 숨긴다.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthUser::class.java, AuthVehicle::class.java)
    }

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Pleos Backend API")
                .version("v1")
                .description("폰 앱에서 만든 프로필을 저장하고, 차량 앱이 시동 시 받아 적용한다."),
        )
        .components(Components().addSecuritySchemes(BEARER, SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")))
        .addSecurityItem(SecurityRequirement().addList(BEARER))

    @Bean
    fun phoneApi(): GroupedOpenApi =
        GroupedOpenApi.builder().group("phone").displayName("폰 앱 (사용자 토큰)").pathsToMatch("/api/v1/**").build()

    @Bean
    fun vehicleApi(): GroupedOpenApi =
        GroupedOpenApi.builder().group("vehicle").displayName("차량 앱 (차량 토큰)").pathsToMatch("/vehicle-api/v1/**").build()

    private companion object {
        const val BEARER = "bearer"
    }
}

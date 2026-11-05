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
                .title("쉼표 Backend API")
                .version("v1")
                .description("쉼표: 가족·차량 연결, 트립 기록(주행 요약), 도착 체크인, 탑승자 컨디션. 차량 앱은 차량 상태 조회 + NaviHelper로 멀미 위험도를 계산한다."),
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

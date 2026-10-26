package com.pleos.backend

import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get

/** FE가 보고 붙을 API 문서가 클라이언트별로 나뉘어 생성되는지 확인한다. */
class OpenApiDocsTest : ApiTestSupport() {

    @Test
    fun `폰 앱 문서에는 폰 API만, 인증 주체 파라미터는 숨긴다`() {
        mockMvc.get("/v3/api-docs/phone").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/v1/profiles']") { exists() }
            jsonPath("$.paths['/vehicle-api/v1/me']") { doesNotExist() }
            jsonPath("$.components.securitySchemes.bearer.scheme") { value("bearer") }
            content { string(not(containsString("AuthUser"))) }
        }
    }

    @Test
    fun `차량 앱 문서에는 차량 API만`() {
        mockMvc.get("/v3/api-docs/vehicle").andExpect {
            status { isOk() }
            jsonPath("$.paths['/vehicle-api/v1/me/next-driver']") { exists() }
            jsonPath("$.paths['/api/v1/profiles']") { doesNotExist() }
            content { string(not(containsString("AuthVehicle"))) }
        }
    }
}

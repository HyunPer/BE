package com.pleos.backend.qa

import com.pleos.backend.ApiTestSupport
import com.pleos.backend.bearer
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertTrue

class QaConsoleTest : ApiTestSupport() {

    @Test
    fun `콘솔 화면이 열리고 시드로 데모 가족이 만들어진다`() {
        mockMvc.get("/qa").andExpect { status { is3xxRedirection() } }
        // 정적 HTML은 charset 헤더 없이 나가고 <meta charset="utf-8">로 해석된다. 테스트도 UTF-8로 읽는다.
        val html = mockMvc.get("/qa/index.html").andExpect { status { isOk() } }
            .andReturn().response.getContentAsString(Charsets.UTF_8)
        assertTrue("Pleos QA 콘솔" in html)

        val seed = mockMvc.post("/qa/api/seed").andExpect {
            status { isCreated() }
            jsonPath("$.users", hasSize<Any>(3))
        }.andReturn().response.contentAsString
        val vehicleToken = seed.read("$.vehicle.token")
        val momToken = seed.read("$.users[0].token")

        mockMvc.get("/vehicle-api/v1/me/next-driver") { bearer(vehicleToken) }.andExpect {
            jsonPath("$.profileName") { value("밤 드라이브") }
            jsonPath("$.vehicleSettings.seatPosition.memorySlot") { value(2) }
        }
        mockMvc.get("/api/v1/themes/shared/${seed.read("$.sharedThemeCode")}") { bearer(momToken) }
            .andExpect { jsonPath("$.skin.name") { value("고양이 밤") } }
    }

    @Test
    fun `시드 API는 Swagger 문서에 나오지 않는다`() {
        mockMvc.get("/v3/api-docs/phone").andExpect { content { string(org.hamcrest.Matchers.not(containsString("/qa/api/seed"))) } }
    }
}

@SpringBootTest(properties = ["pleos.qa.enabled=false"])
@AutoConfigureMockMvc
class QaConsoleDisabledTest(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `꺼두면 콘솔과 시드 API가 없다`() {
        mockMvc.get("/qa/index.html").andExpect { status { isNotFound() } }
        val status = mockMvc.post("/qa/api/seed").andReturn().response.status
        assertTrue(status in 400..499, "시드 응답: $status")
    }
}

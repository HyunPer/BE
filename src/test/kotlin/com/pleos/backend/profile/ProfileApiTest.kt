package com.pleos.backend.profile

import com.pleos.backend.ApiTestSupport
import com.pleos.backend.bearer
import com.pleos.backend.json
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID
import kotlin.test.assertEquals

class ProfileApiTest : ApiTestSupport() {

    private lateinit var token: String

    @BeforeEach
    fun signUpUser() {
        token = signUp("엄마")
    }

    @Test
    fun `이름만 보내면 나머지 설정은 기본값으로 생성된다`() {
        mockMvc.post("/api/v1/profiles") {
            bearer(token)
            json("""{"name":"출근"}""")
        }.andExpect {
            status { isCreated() }
            header { string("Location", containsString("/api/v1/profiles/")) }
            jsonPath("$.name") { value("출근") }
            jsonPath("$.display.widgets") { isEmpty() }
            jsonPath("$.themeId") { doesNotExist() }
            jsonPath("$.seat.reclineAngle") { value(100) }
            jsonPath("$.climate.temperature") { value(22.0) }
            jsonPath("$.light.brightness") { value(50) }
            jsonPath("$.welcomeSoundId") { doesNotExist() }
            jsonPath("$.createdAt") { exists() }
        }

        val saved = profiles.findAll().single()
        assertEquals(saved.createdAt, saved.updatedAt, "생성 직후에는 수정 시각이 생성 시각과 같아야 한다")
    }

    @Test
    fun `전체 설정을 저장하고 조회할 수 있다`() {
        val id = createProfile(
            token,
            """
            {
              "name": "대학생 자녀",
              "display": {"widgets": ["music", "weather", "battery"]},
              "seat": {"reclineAngle": 110, "slidePosition": 70, "heatingLevel": 2, "steeringWheelHeating": true},
              "climate": {"temperature": 20.5, "fanSpeed": 5},
              "light": {"brightness": 80},
              "welcomeSoundId": "chime-01"
            }
            """.trimIndent(),
        )

        mockMvc.get("/api/v1/profiles/$id") { bearer(token) }.andExpect {
            status { isOk() }
            jsonPath("$.display.widgets[0]") { value("music") }
            jsonPath("$.display.widgets[2]") { value("battery") }
            jsonPath("$.seat.steeringWheelHeating") { value(true) }
            jsonPath("$.climate.temperature") { value(20.5) }
            jsonPath("$.light.brightness") { value(80) }
            jsonPath("$.welcomeSoundId") { value("chime-01") }
        }
    }

    @Test
    fun `프로필 수에 제한이 없고 생성 순서로 목록을 돌려준다`() {
        listOf("출근", "퇴근", "주말", "캠핑", "장거리").forEach { createProfile(token, """{"name":"$it"}""") }

        mockMvc.get("/api/v1/profiles") { bearer(token) }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(5))
            jsonPath("$[0].name") { value("출근") }
            jsonPath("$[4].name") { value("장거리") }
        }
    }

    @Test
    fun `PUT은 설정 전체를 교체한다`() {
        val id = createProfile(token, """{"name":"아빠","display":{"widgets":["music","map"]},"welcomeSoundId":"chime-01"}""")

        mockMvc.put("/api/v1/profiles/$id") {
            bearer(token)
            json("""{"name":"아빠(주말)","display":{"widgets":["camping"]},"climate":{"temperature":18.0,"fanSpeed":2}}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("아빠(주말)") }
            jsonPath("$.display.widgets", hasSize<Any>(1))
            jsonPath("$.display.widgets[0]") { value("camping") }
            jsonPath("$.climate.temperature") { value(18.0) }
            jsonPath("$.welcomeSoundId") { doesNotExist() }
        }
    }

    @Test
    fun `삭제하면 다시 조회할 수 없다`() {
        val id = createProfile(token, """{"name":"게스트"}""")

        mockMvc.delete("/api/v1/profiles/$id") { bearer(token) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/profiles/$id") { bearer(token) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `없는 프로필은 404 ProblemDetail을 돌려준다`() {
        mockMvc.get("/api/v1/profiles/${UUID.randomUUID()}") { bearer(token) }.andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(404) }
        }
    }

    @Test
    fun `남의 프로필은 보이지도 고쳐지지도 않는다`() {
        val id = createProfile(token, """{"name":"엄마 출근"}""")
        val other = signUp("아빠")

        mockMvc.get("/api/v1/profiles") { bearer(other) }.andExpect { jsonPath("$", hasSize<Any>(0)) }
        mockMvc.get("/api/v1/profiles/$id") { bearer(other) }.andExpect { status { isNotFound() } }
        mockMvc.delete("/api/v1/profiles/$id") { bearer(other) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `토큰이 없거나 틀리면 401`() {
        mockMvc.get("/api/v1/profiles").andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
        }
        mockMvc.get("/api/v1/profiles") { bearer("wrong-token") }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `이름이 비어 있으면 400`() {
        mockMvc.post("/api/v1/profiles") {
            bearer(token)
            json("""{"name":"  "}""")
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
        }
    }

    @Test
    fun `범위를 벗어난 설정값은 400`() {
        listOf(
            """{"name":"a","climate":{"temperature":40.0}}""",
            """{"name":"a","seat":{"heatingLevel":5}}""",
            """{"name":"a","light":{"brightness":150}}""",
            """{"name":"a","display":{"widgets":[""]}}""",
        ).forEach { body ->
            val status = mockMvc.post("/api/v1/profiles") {
                bearer(token)
                json(body)
            }.andReturn().response.status
            assertEquals(400, status, "요청 본문: $body")
        }
    }
}

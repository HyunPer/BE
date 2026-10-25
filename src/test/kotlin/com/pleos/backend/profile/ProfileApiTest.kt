package com.pleos.backend.profile

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class ProfileApiTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val repository: ProfileRepository,
) {

    @BeforeEach
    fun clean() = repository.deleteAll()

    private fun createProfile(body: String): String {
        val result = mockMvc.post("/api/v1/profiles") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isCreated() }
        }.andReturn()
        val json = result.response.contentAsString
        return Regex("\"id\"\\s*:\\s*\"([0-9a-f-]+)\"").find(json)!!.groupValues[1]
    }

    @Test
    fun `이름만 보내면 나머지 설정은 기본값으로 생성된다`() {
        mockMvc.post("/api/v1/profiles") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"엄마"}"""
        }.andExpect {
            status { isCreated() }
            header { string("Location", containsString("/api/v1/profiles/")) }
            jsonPath("$.name") { value("엄마") }
            jsonPath("$.display.themeColor") { value("#1E88E5") }
            jsonPath("$.display.widgets") { isEmpty() }
            jsonPath("$.seat.reclineAngle") { value(100) }
            jsonPath("$.climate.temperature") { value(22.0) }
            jsonPath("$.light.brightness") { value(50) }
            jsonPath("$.welcomeSoundId") { doesNotExist() }
            jsonPath("$.createdAt") { exists() }
        }

        val saved = repository.findAll().single()
        assertEquals(saved.createdAt, saved.updatedAt, "생성 직후에는 수정 시각이 생성 시각과 같아야 한다")
    }

    @Test
    fun `전체 설정을 저장하고 조회할 수 있다`() {
        val id = createProfile(
            """
            {
              "name": "대학생 자녀",
              "display": {"themeColor": "#ff5722", "wallpaperId": "night-city", "widgets": ["music", "weather", "battery"]},
              "seat": {"reclineAngle": 110, "slidePosition": 70, "heatingLevel": 2, "steeringWheelHeating": true},
              "climate": {"temperature": 20.5, "fanSpeed": 5},
              "light": {"color": "#7c4dff", "brightness": 80},
              "welcomeSoundId": "chime-01"
            }
            """.trimIndent(),
        )

        mockMvc.get("/api/v1/profiles/$id").andExpect {
            status { isOk() }
            jsonPath("$.display.themeColor") { value("#FF5722") }
            jsonPath("$.display.widgets[0]") { value("music") }
            jsonPath("$.display.widgets[2]") { value("battery") }
            jsonPath("$.seat.steeringWheelHeating") { value(true) }
            jsonPath("$.climate.temperature") { value(20.5) }
            jsonPath("$.light.color") { value("#7C4DFF") }
            jsonPath("$.welcomeSoundId") { value("chime-01") }
        }
    }

    @Test
    fun `프로필 수에 제한이 없고 생성 순서로 목록을 돌려준다`() {
        listOf("엄마", "아빠", "자녀1", "자녀2", "할머니").forEach { createProfile("""{"name":"$it"}""") }

        mockMvc.get("/api/v1/profiles").andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(5))
            jsonPath("$[0].name") { value("엄마") }
            jsonPath("$[4].name") { value("할머니") }
        }
    }

    @Test
    fun `PUT은 설정 전체를 교체한다`() {
        val id = createProfile("""{"name":"아빠","display":{"widgets":["music","map"]},"welcomeSoundId":"chime-01"}""")

        mockMvc.put("/api/v1/profiles/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"아빠(주말)","display":{"widgets":["camping"]},"climate":{"temperature":18.0,"fanSpeed":2}}"""
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
        val id = createProfile("""{"name":"게스트"}""")

        mockMvc.delete("/api/v1/profiles/$id").andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/profiles/$id").andExpect { status { isNotFound() } }
    }

    @Test
    fun `없는 프로필은 404 ProblemDetail을 돌려준다`() {
        mockMvc.get("/api/v1/profiles/${UUID.randomUUID()}").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(404) }
        }
    }

    @Test
    fun `이름이 비어 있으면 400`() {
        mockMvc.post("/api/v1/profiles") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"  "}"""
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
            """{"name":"a","light":{"color":"red"}}""",
            """{"name":"a","display":{"widgets":[""]}}""",
        ).forEach { body ->
            val status = mockMvc.post("/api/v1/profiles") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andReturn().response.status
            assertEquals(400, status, "요청 본문: $body")
        }
    }
}

package com.pleos.backend.theme

import com.pleos.backend.ApiTestSupport
import com.pleos.backend.bearer
import com.pleos.backend.json
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import kotlin.test.assertContentEquals

class ThemeApiTest : ApiTestSupport() {

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
    private val gif = "GIF89a-animated".toByteArray()

    private fun upload(token: String, bytes: ByteArray = png, name: String = "char.png"): String =
        mockMvc.multipart("/api/v1/assets") {
            file(MockMultipartFile("file", name, "application/octet-stream", bytes))
            bearer(token)
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString.read("$.id")

    @Test
    fun `이미지를 올리면 토큰 없이 캐시 가능한 주소로 내려받는다`() {
        val token = signUp("자녀")
        val id = upload(token)

        val body = mockMvc.get("/files/$id").andExpect {
            status { isOk() }
            content { contentType("image/png") }
            header { string(HttpHeaders.CACHE_CONTROL, containsString("immutable")) }
        }.andReturn().response.contentAsByteArray
        assertContentEquals(png, body)
    }

    @Test
    fun `GIF나 이미지가 아닌 파일은 400`() {
        val token = signUp("자녀")
        listOf(gif to "anim.gif", "hello".toByteArray() to "note.png").forEach { (bytes, name) ->
            mockMvc.multipart("/api/v1/assets") {
                file(MockMultipartFile("file", name, "image/png", bytes))
                bearer(token)
            }.andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun `글자가 배경에 묻히는 테마는 저장할 수 없다`() {
        val token = signUp("자녀")

        mockMvc.post("/api/v1/themes") {
            bearer(token)
            json("""{"name":"안 보임","colors":{"background":"#FFFFFF","surface":"#FFFFFF","text":"#EEEEEE"}}""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value(containsString("글자색과 배경색 대비가 부족합니다")) }
        }
    }

    @Test
    fun `남이 올린 이미지나 남의 테마는 쓸 수 없다`() {
        val mine = signUp("자녀")
        val other = signUp("남")
        val othersImage = upload(other)
        val othersTheme = createTheme(other, """{"name":"남의 테마"}""")

        mockMvc.post("/api/v1/themes") {
            bearer(mine)
            json("""{"name":"훔친 배경","backgroundAssetId":"$othersImage"}""")
        }.andExpect { status { isNotFound() } }
        mockMvc.post("/api/v1/profiles") {
            bearer(mine)
            json("""{"name":"출근","themeId":"$othersTheme"}""")
        }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/v1/themes/$othersTheme") { bearer(mine) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `프로필에 연결한 테마가 차량 설정 적용 후 웰컴 묶음으로 내려간다`() {
        val car = registerVehicle()
        val kid = signUp("자녀")
        pair(kid, car.pairingCode)
        val background = upload(kid, name = "night.png")
        val character = upload(kid, name = "cat.png")
        val theme = createTheme(
            kid,
            """
            {"name":"고양이 밤",
             "colors":{"background":"#0D0B1E","surface":"#1C1840","primary":"#7C4DFF","onPrimary":"#FFFFFF","text":"#F5F3FF"},
             "backgroundAssetId":"$background",
             "characterAssetId":"$character","characterPlacement":"BOTTOM_LEFT",
             "buttonStyle":"PILL","widgetStyle":"GLASS","fontStyle":"ROUNDED",
             "ambientColor":"#7c4dff"}
            """.trimIndent(),
        )
        val profile = createProfile(kid, """{"name":"밤 드라이브","themeId":"$theme","light":{"brightness":70},"display":{"widgets":["music"]}}""")
        mockMvc.put("/api/v1/vehicles/${car.id}/next-driver") {
            bearer(kid)
            json("""{"profileId":"$profile"}""")
        }

        val reservationId = mockMvc.get("/vehicle-api/v1/me/next-driver") { bearer(car.token) }.andExpect {
            jsonPath("$.skin") { doesNotExist() }
        }.andReturn().response.contentAsString.read("$.reservationId")

        mockMvc.post("/vehicle-api/v1/me/next-driver/$reservationId/applied") { bearer(car.token) }.andExpect {
            status { isOk() }
            jsonPath("$.skin.name") { value("고양이 밤") }
            jsonPath("$.skin.colors.primary") { value("#7C4DFF") }
            jsonPath("$.skin.backgroundImageUrl") { value("/files/$background") }
            jsonPath("$.skin.character.imageUrl") { value("/files/$character") }
            jsonPath("$.skin.character.placement") { value("BOTTOM_LEFT") }
            jsonPath("$.skin.buttonStyle") { value("PILL") }
            jsonPath("$.skin.widgetStyle") { value("GLASS") }
            jsonPath("$.ambientLight.color") { value("#7C4DFF") }
            jsonPath("$.ambientLight.brightness") { value(70) }
            jsonPath("$.widgets[0]") { value("music") }
        }

        // 테마를 지우면 프로필은 기본 테마로 돌아간다.
        mockMvc.delete("/api/v1/themes/$theme") { bearer(kid) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/profiles/$profile") { bearer(kid) }.andExpect { jsonPath("$.themeId") { doesNotExist() } }
        mockMvc.post("/vehicle-api/v1/me/profiles/$profile/applied") { bearer(car.token) }.andExpect {
            jsonPath("$.skin.name") { value("기본") }
        }
    }

    @Test
    fun `공유 코드로 테마를 주고받고 마켓에 인기순으로 보인다`() {
        val creator = signUp("꾸미기장인")
        val fan = signUp("팬")
        val character = upload(creator)
        val popular = createTheme(creator, """{"name":"고양이","characterAssetId":"$character","buttonStyle":"PILL"}""")
        val quiet = createTheme(creator, """{"name":"조용한"}""")

        val code = mockMvc.post("/api/v1/themes/$popular/share") { bearer(creator) }.andExpect {
            status { isOk() }
            jsonPath("$.shareCode") { value(matchesPattern("[A-Z2-9]{8}")) }
        }.andReturn().response.contentAsString.read("$.shareCode")
        // 이미 공유 중이면 같은 코드
        mockMvc.post("/api/v1/themes/$popular/share") { bearer(creator) }.andExpect { jsonPath("$.shareCode") { value(code) } }
        mockMvc.post("/api/v1/themes/$quiet/share") { bearer(creator) }

        mockMvc.get("/api/v1/themes/shared/${code.lowercase()}") { bearer(fan) }.andExpect {
            status { isOk() }
            jsonPath("$.authorNickname") { value("꾸미기장인") }
            jsonPath("$.skin.name") { value("고양이") }
        }

        val copy = mockMvc.post("/api/v1/themes/import") {
            bearer(fan)
            json("""{"shareCode":"$code"}""")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.imported") { value(true) }
            jsonPath("$.shareCode") { doesNotExist() }
            jsonPath("$.skin.character.imageUrl") { value("/files/$character") }
        }.andReturn().response.contentAsString.read("$.id")

        // 받은 테마는 내 프로필에 쓸 수 있고, 원작자 이미지를 유지한 채 고칠 수도 있다.
        createProfile(fan, """{"name":"내 출근","themeId":"$copy"}""")
        mockMvc.put("/api/v1/themes/$copy") {
            bearer(fan)
            json("""{"name":"내 고양이","characterAssetId":"$character"}""")
        }.andExpect { status { isOk() } }
        // 원작자 보호: 받은 테마는 다시 공유할 수 없다.
        mockMvc.post("/api/v1/themes/$copy/share") { bearer(fan) }.andExpect { status { isConflict() } }

        mockMvc.get("/api/v1/themes/market") { bearer(fan) }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(2))
            jsonPath("$[0].skin.name") { value("고양이") }
            jsonPath("$[0].importCount") { value(1) }
            jsonPath("$[1].importCount") { value(0) }
        }

        // 공유를 멈추면 코드로 더는 못 받지만, 이미 받은 복사본은 남는다.
        mockMvc.delete("/api/v1/themes/$popular/share") { bearer(creator) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/themes/shared/$code") { bearer(fan) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/v1/themes/$copy") { bearer(fan) }.andExpect { status { isOk() } }
    }
}

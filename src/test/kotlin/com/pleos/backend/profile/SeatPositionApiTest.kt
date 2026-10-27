package com.pleos.backend.profile

import com.pleos.backend.ApiTestSupport
import com.pleos.backend.bearer
import com.pleos.backend.json
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import kotlin.test.assertEquals

class SeatPositionApiTest : ApiTestSupport() {

    @Test
    fun `차량에서 저장한 자세와 폰에서 고른 메모리 슬롯이 그 차량에만 내려간다`() {
        val home = registerVehicle("IONIQ 5")
        val share = registerVehicle("KONA EV")
        val kid = signUp("자녀")
        pair(kid, home.pairingCode)
        pair(kid, share.pairingCode)
        val profile = createProfile(kid, """{"name":"밤 드라이브","seat":{"heatingLevel":2,"ventilationLevel":1}}""")

        // 차 안에서 "지금 자세 저장": 차량 앱이 SDK로 읽은 현재 위치(차량 단위)
        mockMvc.put("/vehicle-api/v1/me/profiles/$profile/seat-position") {
            bearer(home.token)
            json("""{"backrestPosition":-12,"foreAftPosition":340}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.backrestPosition") { value(-12) }
            jsonPath("$.memorySlot") { doesNotExist() }
        }

        // 다른 차에는 폰에서 메모리 슬롯만 지정
        mockMvc.put("/api/v1/profiles/$profile/seat-positions/${share.id}") {
            bearer(kid)
            json("""{"memorySlot":2}""")
        }.andExpect { jsonPath("$.memorySlot") { value(2) } }

        mockMvc.get("/api/v1/profiles/$profile/seat-positions") { bearer(kid) }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(2))
            jsonPath("$[0].vehicleModel") { value("IONIQ 5") }
            jsonPath("$[0].position.foreAftPosition") { value(340) }
            jsonPath("$[1].position.memorySlot") { value(2) }
        }

        // 1단계 차량 설정에 그 차량의 자세가 들어간다
        mockMvc.put("/api/v1/vehicles/${home.id}/next-driver") {
            bearer(kid)
            json("""{"profileId":"$profile"}""")
        }
        mockMvc.get("/vehicle-api/v1/me/next-driver") { bearer(home.token) }.andExpect {
            jsonPath("$.vehicleSettings.seat.heatingLevel") { value(2) }
            jsonPath("$.vehicleSettings.seat.ventilationLevel") { value(1) }
            jsonPath("$.vehicleSettings.seatPosition.backrestPosition") { value(-12) }
            jsonPath("$.vehicleSettings.seatPosition.foreAftPosition") { value(340) }
        }
        mockMvc.get("/vehicle-api/v1/me/profiles") { bearer(share.token) }.andExpect {
            jsonPath("$[0].profiles[0].vehicleSettings.seatPosition.memorySlot") { value(2) }
            jsonPath("$[0].profiles[0].vehicleSettings.seatPosition.backrestPosition") { doesNotExist() }
        }

        // 지우면 그 차에서는 시트를 움직이지 않는다
        mockMvc.delete("/api/v1/profiles/$profile/seat-positions/${home.id}") { bearer(kid) }
            .andExpect { status { isNoContent() } }
        mockMvc.get("/vehicle-api/v1/me/next-driver") { bearer(home.token) }.andExpect {
            jsonPath("$.vehicleSettings.seatPosition") { doesNotExist() }
        }
    }

    @Test
    fun `빈 자세, 범위 밖 슬롯, 남의 차·남의 프로필은 거부한다`() {
        val car = registerVehicle()
        val other = registerVehicle("KONA EV")
        val kid = signUp("자녀")
        pair(kid, car.pairingCode)
        val profile = createProfile(kid, """{"name":"출근"}""")
        val strangersProfile = createProfile(signUp("남"), """{"name":"남의 것"}""")

        mockMvc.put("/api/v1/profiles/$profile/seat-positions/${car.id}") {
            bearer(kid)
            json("""{}""")
        }.andExpect { status { isBadRequest() } }
        mockMvc.put("/api/v1/profiles/$profile/seat-positions/${car.id}") {
            bearer(kid)
            json("""{"memorySlot":11}""")
        }.andExpect { status { isBadRequest() } }
        // 멤버가 아닌 차량
        mockMvc.put("/api/v1/profiles/$profile/seat-positions/${other.id}") {
            bearer(kid)
            json("""{"memorySlot":1}""")
        }.andExpect { status { isNotFound() } }
        // 차량 멤버가 아닌 사람의 프로필
        mockMvc.put("/vehicle-api/v1/me/profiles/$strangersProfile/seat-position") {
            bearer(car.token)
            json("""{"memorySlot":1}""")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `프로필을 지우면 차량별 자세도 지워진다`() {
        val car = registerVehicle()
        val kid = signUp("자녀")
        pair(kid, car.pairingCode)
        val profile = createProfile(kid, """{"name":"출근"}""")
        mockMvc.put("/api/v1/profiles/$profile/seat-positions/${car.id}") {
            bearer(kid)
            json("""{"memorySlot":1}""")
        }

        mockMvc.delete("/api/v1/profiles/$profile") { bearer(kid) }.andExpect { status { isNoContent() } }
        assertEquals(0, seatPositions.count())
    }
}

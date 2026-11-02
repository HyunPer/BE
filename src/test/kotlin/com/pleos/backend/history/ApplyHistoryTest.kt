package com.pleos.backend.history

import com.pleos.backend.ApiTestSupport
import com.pleos.backend.bearer
import com.pleos.backend.json
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

class ApplyHistoryTest : ApiTestSupport() {

    @Test
    fun `예약 적용과 1탭 선택 적용이 최신순으로 한 번씩 기록된다`() {
        val car = registerVehicle()
        val mom = signUp("엄마")
        val kid = signUp("자녀")
        pair(mom, car.pairingCode)
        pair(kid, car.pairingCode)
        val night = createProfile(kid, """{"name":"밤 드라이브"}""")
        val commute = createProfile(mom, """{"name":"출근"}""")

        mockMvc.put("/api/v1/vehicles/${car.id}/next-driver") { bearer(kid); json("""{"profileId":"$night"}""") }
        val reservationId = mockMvc.get("/vehicle-api/v1/me/next-driver") { bearer(car.token) }
            .andReturn().response.contentAsString.read("$.reservationId")
        repeat(2) { // 재시도해도 기록은 한 번
            mockMvc.post("/vehicle-api/v1/me/next-driver/$reservationId/applied") { bearer(car.token) }
        }
        repeat(2) { // 1분 안의 같은 선택도 한 번
            mockMvc.post("/vehicle-api/v1/me/profiles/$commute/applied") { bearer(car.token) }
        }

        mockMvc.get("/api/v1/vehicles/${car.id}/history") { bearer(mom) }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(2))
            jsonPath("$[0].profileName") { value("출근") }
            jsonPath("$[0].source") { value("SELECTED") }
            jsonPath("$[1].driverNickname") { value("자녀") }
            jsonPath("$[1].source") { value("RESERVATION") }
        }
    }

    @Test
    fun `프로필을 지워도 기록은 남고, 멤버가 아니면 볼 수 없다`() {
        val car = registerVehicle()
        val mom = signUp("엄마")
        pair(mom, car.pairingCode)
        val profile = createProfile(mom, """{"name":"주말"}""")
        mockMvc.post("/vehicle-api/v1/me/profiles/$profile/applied") { bearer(car.token) }

        mockMvc.delete("/api/v1/profiles/$profile") { bearer(mom) }
        mockMvc.get("/api/v1/vehicles/${car.id}/history") { bearer(mom) }.andExpect {
            jsonPath("$", hasSize<Any>(1))
            jsonPath("$[0].profileName") { value("주말") }
        }
        mockMvc.get("/api/v1/vehicles/${car.id}/history") { bearer(signUp("남")) }
            .andExpect { status { isNotFound() } }
    }
}
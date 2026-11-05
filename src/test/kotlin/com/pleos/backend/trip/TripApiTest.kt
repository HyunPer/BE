package com.pleos.backend.trip

import com.pleos.backend.ApiTestSupport
import com.pleos.backend.bearer
import com.pleos.backend.json
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

class TripApiTest : ApiTestSupport() {

    @Autowired lateinit var trips: TripRepository
    @Autowired lateinit var checkIns: TripCheckInRepository
    @Autowired lateinit var conditions: RiderConditionRepository

    private val summary = """{"drivingMinutes":44,"congestionMinutes":18,"sharpTurns":6,"peakRisk":72}"""

    private fun startTrip(vehicleToken: String): String =
        mockMvc.post("/vehicle-api/v1/trips") { bearer(vehicleToken) }
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString.read("$.id")

    @Test
    fun `탑승하면 시작하고 도착하면 요약과 함께 끝나며, 가족이 체크인을 남긴다`() {
        checkIns.deleteAll(); trips.deleteAll()
        val car = registerVehicle()
        val mom = signUp("엄마")
        pair(mom, car.pairingCode)

        val tripId = startTrip(car.token)
        repeat(2) { // 재시도해도 처음 요약 그대로
            mockMvc.post("/vehicle-api/v1/trips/$tripId/finish") { bearer(car.token); json(summary) }.andExpect {
                status { isOk() }
                jsonPath("$.peakRisk") { value(72) }
                jsonPath("$.endedAt") { exists() }
            }
        }

        mockMvc.put("/api/v1/trips/$tripId/check-in") { bearer(mom); json("""{"mood":"TIRED","note":"막혀서 지침"}""") }
            .andExpect { status { isOk() } }
        mockMvc.put("/api/v1/trips/$tripId/check-in") { bearer(mom); json("""{"mood":"CALM"}""") }
            .andExpect { jsonPath("$.myMood") { value("CALM") } }

        mockMvc.get("/api/v1/vehicles/${car.id}/trips") { bearer(mom) }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(1))
            jsonPath("$[0].congestionMinutes") { value(18) }
            jsonPath("$[0].myMood") { value("CALM") }
        }
    }

    @Test
    fun `끝내지 못한 트립은 다음 탑승 때 닫히고, 남의 차 트립은 보이지 않는다`() {
        checkIns.deleteAll(); trips.deleteAll()
        val car = registerVehicle()
        val other = registerVehicle("CASPER Electric")
        val mom = signUp("엄마")
        pair(mom, car.pairingCode)

        val first = startTrip(car.token)
        startTrip(car.token)
        assertClosed(first)

        mockMvc.post("/vehicle-api/v1/trips/$first/finish") { bearer(other.token); json(summary) }
            .andExpect { status { isNotFound() } }
        mockMvc.put("/api/v1/trips/$first/check-in") { bearer(signUp("남")); json("""{"mood":"OK"}""") }
            .andExpect { status { isNotFound() } }
        mockMvc.get("/api/v1/vehicles/${other.id}/trips") { bearer(mom) }.andExpect { status { isNotFound() } }
        mockMvc.post("/vehicle-api/v1/trips/${UUID.randomUUID()}/finish") { bearer(car.token); json("""{"drivingMinutes":-1,"congestionMinutes":0,"sharpTurns":0,"peakRisk":101}""") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `컨디션을 저장하면 차량 앱이 가족별로 받는다`() {
        conditions.deleteAll()
        val car = registerVehicle()
        val kid = signUp("자녀")
        val dad = signUp("아빠")
        pair(dad, car.pairingCode)
        pair(kid, car.pairingCode)

        mockMvc.get("/api/v1/users/me/condition") { bearer(kid) }.andExpect { jsonPath("$.motionSensitivity") { value("MEDIUM") } }
        mockMvc.put("/api/v1/users/me/condition") { bearer(kid); json("""{"motionSensitivity":"HIGH","restStyle":"FRESH_AIR"}""") }
            .andExpect { status { isOk() } }

        mockMvc.get("/vehicle-api/v1/me/riders") { bearer(car.token) }.andExpect {
            jsonPath("$", hasSize<Any>(2))
            jsonPath("$[?(@.nickname == '자녀')].condition.motionSensitivity") { value("HIGH") }
            jsonPath("$[?(@.nickname == '아빠')].condition.restStyle") { value("QUIET") }
        }
    }

    private fun assertClosed(tripId: String) {
        check(trips.findById(UUID.fromString(tripId)).get().endedAt != null) { "이전 트립이 닫히지 않았습니다" }
    }
}

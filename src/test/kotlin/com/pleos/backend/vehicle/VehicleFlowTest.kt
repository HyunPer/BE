package com.pleos.backend.vehicle

import com.pleos.backend.ApiTestSupport
import com.pleos.backend.bearer
import com.pleos.backend.json
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 데모 시나리오 A(가족 공유차)를 API로 따라간다. */
class VehicleFlowTest : ApiTestSupport() {

    @Test
    fun `차량 등록 후 페어링 코드로 가족이 차례로 합류한다`() {
        val car = registerVehicle("IONIQ 5")
        assertTrue(car.pairingCode.matches(Regex("\\d{6}")))
        val mom = signUp("엄마")
        val dad = signUp("아빠")

        mockMvc.post("/api/v1/vehicles/pair") {
            bearer(mom)
            json("""{"pairingCode":"${car.pairingCode}","nickname":"우리집 아이오닉"}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.myRole") { value("OWNER") }
            jsonPath("$.nickname") { value("우리집 아이오닉") }
        }
        mockMvc.post("/api/v1/vehicles/pair") {
            bearer(dad)
            json("""{"pairingCode":"${car.pairingCode}","nickname":"무시됨"}""")
        }.andExpect {
            jsonPath("$.myRole") { value("MEMBER") }
            jsonPath("$.nickname") { value("우리집 아이오닉") }
        }
        // 같은 사람이 다시 입력해도 멤버가 늘지 않는다.
        pair(dad, car.pairingCode)

        mockMvc.get("/api/v1/vehicles/${car.id}") { bearer(mom) }.andExpect {
            status { isOk() }
            jsonPath("$.members", hasSize<Any>(2))
            jsonPath("$.members[0].nickname") { value("엄마") }
            jsonPath("$.members[1].role") { value("MEMBER") }
        }
        mockMvc.get("/api/v1/vehicles") { bearer(dad) }.andExpect { jsonPath("$", hasSize<Any>(1)) }
        mockMvc.get("/vehicle-api/v1/me") { bearer(car.token) }.andExpect {
            jsonPath("$.memberCount") { value(2) }
            jsonPath("$.pairingCode") { value(car.pairingCode) }
        }
    }

    @Test
    fun `틀린 페어링 코드는 404, 멤버가 아니면 차량이 보이지 않는다`() {
        val car = registerVehicle()
        val stranger = signUp("남")

        mockMvc.post("/api/v1/vehicles/pair") {
            bearer(stranger)
            json("""{"pairingCode":"${if (car.pairingCode == "000000") "000001" else "000000"}"}""")
        }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/v1/vehicles/${car.id}") { bearer(stranger) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `사용자 토큰과 차량 토큰은 서로의 API에 쓸 수 없다`() {
        val car = registerVehicle()
        val mom = signUp("엄마")

        mockMvc.get("/vehicle-api/v1/me") { bearer(mom) }.andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/vehicles") { bearer(car.token) }.andExpect { status { isUnauthorized() } }
        assertEquals(1, vehicles.count())
    }
}

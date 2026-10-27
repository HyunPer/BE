package com.pleos.backend.vehicle

import com.pleos.backend.ApiTestSupport
import com.pleos.backend.bearer
import com.pleos.backend.json
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MemberManagementTest : ApiTestSupport() {

    private fun userIdOf(token: String) =
        mockMvc.get("/api/v1/users/me") { bearer(token) }.andReturn().response.contentAsString.read("$.id")

    @Test
    fun `두 사람이 동시에 첫 페어링해도 OWNER는 한 명`() {
        repeat(3) {
            val car = registerVehicle()
            val tokens = (1..4).map { n -> signUp("가족$n") }
            val pool = Executors.newFixedThreadPool(tokens.size)
            pool.invokeAll(tokens.map { t ->
                Callable { mockMvc.post("/api/v1/vehicles/pair") { bearer(t); json("""{"pairingCode":"${car.pairingCode}"}""") } }
            }).forEach { it.get() }
            pool.shutdown()

            val roles = members.findAll().filter { it.vehicleId.toString() == car.id }.map { it.role }
            assertEquals(4, roles.size)
            assertEquals(1, roles.count { it == VehicleRole.OWNER }, "역할: $roles")
        }
    }

    @Test
    fun `MEMBER가 나가면 그 사람의 대기 예약과 이 차 시트 자세가 정리된다`() {
        val car = registerVehicle()
        val mom = signUp("엄마")
        val kid = signUp("자녀")
        pair(mom, car.pairingCode)
        pair(kid, car.pairingCode)
        val profile = createProfile(kid, """{"name":"밤"}""")
        mockMvc.put("/api/v1/profiles/$profile/seat-positions/${car.id}") { bearer(kid); json("""{"memorySlot":2}""") }
        mockMvc.put("/api/v1/vehicles/${car.id}/next-driver") { bearer(kid); json("""{"profileId":"$profile"}""") }

        mockMvc.delete("/api/v1/vehicles/${car.id}/members/me") { bearer(kid) }.andExpect { status { isNoContent() } }

        mockMvc.get("/vehicle-api/v1/me/next-driver") { bearer(car.token) }.andExpect { status { isNoContent() } }
        mockMvc.get("/vehicle-api/v1/me/profiles") { bearer(car.token) }.andExpect { jsonPath("$", hasSize<Any>(1)) }
        assertEquals(0, seatPositions.count())
        mockMvc.get("/api/v1/vehicles/${car.id}") { bearer(kid) }.andExpect { status { isNotFound() } }
        // 프로필 자체는 남는다(다른 차에서 쓸 수 있음)
        mockMvc.get("/api/v1/profiles/$profile") { bearer(kid) }.andExpect { status { isOk() } }
    }

    @Test
    fun `OWNER는 넘긴 뒤에야 나갈 수 있고, 혼자면 바로 나갈 수 있다`() {
        val car = registerVehicle()
        val mom = signUp("엄마")
        val dad = signUp("아빠")
        pair(mom, car.pairingCode)
        pair(dad, car.pairingCode)

        mockMvc.delete("/api/v1/vehicles/${car.id}/members/me") { bearer(mom) }.andExpect { status { isConflict() } }
        mockMvc.post("/api/v1/vehicles/${car.id}/owner") { bearer(dad); json("""{"userId":"${userIdOf(mom)}"}""") }
            .andExpect { status { isForbidden() } }
        mockMvc.post("/api/v1/vehicles/${car.id}/owner") { bearer(mom); json("""{"userId":"${userIdOf(dad)}"}""") }.andExpect {
            status { isOk() }
            jsonPath("$.myRole") { value("MEMBER") }
        }
        mockMvc.delete("/api/v1/vehicles/${car.id}/members/me") { bearer(mom) }.andExpect { status { isNoContent() } }
        mockMvc.delete("/api/v1/vehicles/${car.id}/members/me") { bearer(dad) }.andExpect { status { isNoContent() } }
        mockMvc.get("/vehicle-api/v1/me") { bearer(car.token) }.andExpect { jsonPath("$.memberCount") { value(0) } }
    }

    @Test
    fun `OWNER만 멤버를 내보내고, 차 이름을 바꾸고, 페어링 코드를 재발급한다`() {
        val car = registerVehicle()
        val mom = signUp("엄마")
        val dad = signUp("아빠")
        val guest = signUp("손님")
        pair(mom, car.pairingCode, "우리차")
        pair(dad, car.pairingCode)
        pair(guest, car.pairingCode)

        mockMvc.patch("/api/v1/vehicles/${car.id}") { bearer(dad); json("""{"nickname":"아빠차"}""") }.andExpect { status { isForbidden() } }
        mockMvc.delete("/api/v1/vehicles/${car.id}/members/${userIdOf(guest)}") { bearer(dad) }.andExpect { status { isForbidden() } }
        mockMvc.post("/api/v1/vehicles/${car.id}/pairing-code") { bearer(dad) }.andExpect { status { isForbidden() } }

        mockMvc.patch("/api/v1/vehicles/${car.id}") { bearer(mom); json("""{"nickname":"패밀리카"}""") }
            .andExpect { jsonPath("$.nickname") { value("패밀리카") } }
        mockMvc.delete("/api/v1/vehicles/${car.id}/members/${userIdOf(guest)}") { bearer(mom) }.andExpect { status { isNoContent() } }
        mockMvc.delete("/api/v1/vehicles/${car.id}/members/${userIdOf(mom)}") { bearer(mom) }.andExpect { status { isBadRequest() } }

        val newCode = mockMvc.post("/api/v1/vehicles/${car.id}/pairing-code") { bearer(mom) }
            .andReturn().response.contentAsString.read("$.pairingCode")
        assertNotEquals(car.pairingCode, newCode)
        mockMvc.post("/api/v1/vehicles/pair") { bearer(guest); json("""{"pairingCode":"${car.pairingCode}"}""") }
            .andExpect { status { isNotFound() } }
        mockMvc.get("/vehicle-api/v1/me") { bearer(car.token) }.andExpect {
            jsonPath("$.pairingCode") { value(newCode) }
            jsonPath("$.memberCount") { value(2) }
        }
    }

    @Test
    fun `닉네임을 바꾸면 웰컴 인사말에 바로 반영된다`() {
        val car = registerVehicle()
        val kid = signUp("자녀")
        pair(kid, car.pairingCode)
        val profile = createProfile(kid, """{"name":"밤"}""")

        mockMvc.patch("/api/v1/users/me") { bearer(kid); json("""{"nickname":"재윤"}""") }
            .andExpect { jsonPath("$.nickname") { value("재윤") } }
        mockMvc.patch("/api/v1/users/me") { bearer(kid); json("""{"nickname":"  "}""") }.andExpect { status { isBadRequest() } }

        mockMvc.post("/vehicle-api/v1/me/profiles/$profile/applied") { bearer(car.token) }
            .andExpect { jsonPath("$.greeting") { value("재윤님, 밤 설정을 적용했어요") } }
    }
}

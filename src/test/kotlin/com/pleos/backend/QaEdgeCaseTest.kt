package com.pleos.backend

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 비정상 입력·권한 경계·동시성 QA. 모든 오류는 ProblemDetail로, 500 없이 응답해야 한다. */
class QaEdgeCaseTest : ApiTestSupport() {

    private fun problem(status: Int, block: () -> org.springframework.test.web.servlet.ResultActionsDsl) {
        block().andExpect {
            status { isEqualTo(status) }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
        }
    }

    @Test
    fun `잘못된 형식의 요청은 모두 400 ProblemDetail`() {
        val token = signUp("엄마")
        problem(400) { mockMvc.get("/api/v1/profiles/not-a-uuid") { bearer(token) } }
        problem(400) { mockMvc.post("/api/v1/profiles") { bearer(token); json("""{"name":""") } }
        problem(400) { mockMvc.post("/api/v1/themes") { bearer(token); json("""{"name":"a","buttonStyle":"FOO"}""") } }
        problem(400) { mockMvc.post("/api/v1/users") { json("""{"nickname":"${"가".repeat(21)}"}""") } }
        problem(400) { mockMvc.post("/api/v1/vehicles/pair") { bearer(token); json("""{"pairingCode":"12ab56"}""") } }
        problem(400) { mockMvc.post("/api/v1/profiles") { bearer(token); json("""{"name":"a","display":{"widgets":${(1..13).map { "\"w$it\"" }}}}""") } }
        problem(400) { mockMvc.post("/api/v1/profiles") { bearer(token); json("""{"name":"a","welcomeSoundId":"${"s".repeat(101)}"}""") } }
        problem(400) { mockMvc.post("/api/v1/themes/import") { bearer(token); json("""{"shareCode":"short"}""") } }
        problem(400) { mockMvc.multipart("/api/v1/assets") { bearer(token) } }
        problem(400) { mockMvc.put("/api/v1/vehicles/${UUID.randomUUID()}/next-driver") { bearer(token); json("""{}""") } }
    }

    @Test
    fun `없는 경로·메서드도 ProblemDetail`() {
        val token = signUp("엄마")
        problem(404) { mockMvc.get("/api/v1/nothing-here") { bearer(token) } }
        problem(405) { mockMvc.get("/api/v1/users") }
        problem(404) { mockMvc.put("/api/v1/profiles/${UUID.randomUUID()}") { bearer(token); json("""{"name":"a"}""") } }
        problem(404) { mockMvc.post("/vehicle-api/v1/me/next-driver/${UUID.randomUUID()}/applied") { bearer(registerVehicle().token) } }
    }

    @Test
    fun `Authorization 헤더 변형은 모두 401`() {
        listOf("Bearer ", "Bearer", "Basic abc", "bearer wrong", "").forEach { value ->
            problem(401) { mockMvc.get("/api/v1/profiles") { header(HttpHeaders.AUTHORIZATION, value) } }
        }
    }

    @Test
    fun `예약 취소는 예약한 사람이나 OWNER만 할 수 있다`() {
        val car = registerVehicle()
        val mom = signUp("엄마")
        val dad = signUp("아빠")
        val kid = signUp("자녀")
        pair(mom, car.pairingCode)
        pair(dad, car.pairingCode)
        pair(kid, car.pairingCode)
        val kidProfile = createProfile(kid, """{"name":"밤"}""")
        mockMvc.put("/api/v1/vehicles/${car.id}/next-driver") { bearer(kid); json("""{"profileId":"$kidProfile"}""") }

        // 다른 MEMBER(아빠)는 남의 예약을 지울 수 없다
        problem(403) { mockMvc.delete("/api/v1/vehicles/${car.id}/next-driver") { bearer(dad) } }
        mockMvc.get("/api/v1/vehicles/${car.id}/next-driver") { bearer(kid) }.andExpect { status { isOk() } }
        // OWNER(엄마)는 지울 수 있다
        mockMvc.delete("/api/v1/vehicles/${car.id}/next-driver") { bearer(mom) }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `같은 사람이 동시에 여러 번 페어링해도 500 없이 멤버는 한 번만`() {
        val car = registerVehicle()
        val token = signUp("엄마")
        val pool = Executors.newFixedThreadPool(6)
        val statuses = pool.invokeAll((1..6).map {
            Callable {
                mockMvc.post("/api/v1/vehicles/pair") { bearer(token); json("""{"pairingCode":"${car.pairingCode}"}""") }
                    .andReturn().response.status
            }
        }).map { it.get() }
        pool.shutdown()

        assertTrue(statuses.all { it == 200 }, "응답: $statuses")
        assertEquals(1, members.count())
    }
}

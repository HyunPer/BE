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
        problem(400) { mockMvc.get("/api/v1/vehicles/not-a-uuid") { bearer(token) } }
        problem(400) { mockMvc.post("/api/v1/vehicles/pair") { bearer(token); json("""{"pairingCode":""") } }
        problem(400) { mockMvc.post("/api/v1/users") { json("""{"nickname":"${"가".repeat(21)}"}""") } }
        problem(400) { mockMvc.post("/api/v1/vehicles/pair") { bearer(token); json("""{"pairingCode":"12ab56"}""") } }
    }

    @Test
    fun `없는 경로·메서드도 ProblemDetail`() {
        val token = signUp("엄마")
        problem(404) { mockMvc.get("/api/v1/nothing-here") { bearer(token) } }
        problem(405) { mockMvc.get("/api/v1/users") }
        problem(404) { mockMvc.get("/api/v1/vehicles/${UUID.randomUUID()}") { bearer(token) } }
    }

    @Test
    fun `Authorization 헤더 변형은 모두 401`() {
        listOf("Bearer ", "Bearer", "Basic abc", "bearer wrong", "").forEach { value ->
            problem(401) { mockMvc.get("/api/v1/vehicles") { header(HttpHeaders.AUTHORIZATION, value) } }
        }
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

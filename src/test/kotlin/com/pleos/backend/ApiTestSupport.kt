package com.pleos.backend

import com.jayway.jsonpath.JsonPath
import com.pleos.backend.asset.AssetRepository
import com.pleos.backend.profile.ProfileRepository
import com.pleos.backend.reservation.ReservationRepository
import com.pleos.backend.theme.ThemeRepository
import com.pleos.backend.user.UserRepository
import com.pleos.backend.vehicle.VehicleMemberRepository
import com.pleos.backend.vehicle.VehicleRepository
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/** API 통합 테스트 공통: 매 테스트 전 DB 비우기, 가입·차량 등록 헬퍼. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiTestSupport {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var profiles: ProfileRepository
    @Autowired lateinit var vehicles: VehicleRepository
    @Autowired lateinit var members: VehicleMemberRepository
    @Autowired lateinit var reservations: ReservationRepository
    @Autowired lateinit var themes: ThemeRepository
    @Autowired lateinit var assets: AssetRepository

    @BeforeEach
    fun cleanDatabase() {
        reservations.deleteAll()
        members.deleteAll()
        vehicles.deleteAll()
        profiles.deleteAll()
        themes.deleteAll()
        assets.deleteAll()
        users.deleteAll()
    }

    data class RegisteredVehicle(val id: String, val token: String, val pairingCode: String)

    /** 가입하고 accessToken을 돌려준다. */
    fun signUp(nickname: String): String =
        postJson("/api/v1/users", """{"nickname":"$nickname"}""").read("$.accessToken")

    fun registerVehicle(model: String = "IONIQ 5"): RegisteredVehicle {
        val json = postJson("/vehicle-api/v1/vehicles", """{"model":"$model"}""")
        return RegisteredVehicle(json.read("$.vehicleId"), json.read("$.vehicleToken"), json.read("$.pairingCode"))
    }

    fun createTheme(token: String, body: String): String =
        postJson("/api/v1/themes", body, token).read("$.id")

    fun createProfile(token: String, body: String): String =
        postJson("/api/v1/profiles", body, token).read("$.id")

    fun pair(token: String, pairingCode: String, nickname: String? = null): String {
        val nick = nickname?.let { ""","nickname":"$it"""" }.orEmpty()
        return postJson("/api/v1/vehicles/pair", """{"pairingCode":"$pairingCode"$nick}""", token, expectCreated = false).read("$.id")
    }

    fun postJson(url: String, body: String, token: String? = null, expectCreated: Boolean = true): String =
        mockMvc.post(url) {
            json(body)
            token?.let { bearer(it) }
        }.andExpect {
            status { if (expectCreated) isCreated() else isOk() }
        }.andReturn().response.contentAsString

    fun String.read(path: String): String = JsonPath.read<Any>(this, path).toString()
}

fun MockHttpServletRequestDsl.bearer(token: String) {
    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
}

fun MockHttpServletRequestDsl.json(body: String) {
    contentType = MediaType.APPLICATION_JSON
    content = body
}

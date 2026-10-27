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
        mockMvc.get("/api/v1/vehicles/${car.id}/next-driver") { bearer(stranger) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `차량 설정을 먼저 받고, 적용 완료를 보고해야 테마를 받는다`() {
        val car = registerVehicle()
        val mom = signUp("엄마")
        val kid = signUp("대학생 자녀")
        pair(mom, car.pairingCode)
        pair(kid, car.pairingCode)
        val momProfile = createProfile(mom, """{"name":"엄마 출근"}""")
        val kidProfile = createProfile(
            kid,
            """
            {"name":"밤 드라이브",
             "climate":{"temperature":19.0,"fanSpeed":4},
             "display":{"widgets":["music"]},
             "light":{"brightness":80},
             "welcomeSoundId":"chime-01"}
            """.trimIndent(),
        )

        // 예약 전: 차량 앱은 204
        mockMvc.get("/vehicle-api/v1/me/next-driver") { bearer(car.token) }.andExpect { status { isNoContent() } }

        mockMvc.put("/api/v1/vehicles/${car.id}/next-driver") {
            bearer(mom)
            json("""{"profileId":"$momProfile"}""")
        }.andExpect { jsonPath("$.status") { value("PENDING") } }

        // 자녀가 나중에 예약하면 엄마 예약은 취소되고 자녀 것이 대기한다.
        mockMvc.put("/api/v1/vehicles/${car.id}/next-driver") {
            bearer(kid)
            json("""{"profileId":"$kidProfile"}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.driverNickname") { value("대학생 자녀") }
            jsonPath("$.profileName") { value("밤 드라이브") }
        }
        mockMvc.get("/api/v1/vehicles/${car.id}/next-driver") { bearer(mom) }.andExpect {
            jsonPath("$.driverNickname") { value("대학생 자녀") }
        }

        // 1단계: 차량 설정만 내려온다. 테마·조명·사운드는 없다.
        val next = mockMvc.get("/vehicle-api/v1/me/next-driver") { bearer(car.token) }.andExpect {
            status { isOk() }
            jsonPath("$.driverNickname") { value("대학생 자녀") }
            jsonPath("$.vehicleSettings.climate.temperature") { value(19.0) }
            jsonPath("$.vehicleSettings.seat.heatingLevel") { value(0) }
            jsonPath("$.vehicleSettings.seatPosition") { doesNotExist() }
            jsonPath("$.skin") { doesNotExist() }
            jsonPath("$.vehicleSettings.light") { doesNotExist() }
            jsonPath("$.vehicleSettings.display") { doesNotExist() }
        }.andReturn().response.contentAsString
        val reservationId = next.read("$.reservationId")

        // 2단계: 적용 완료를 보고하면 웰컴 묶음이 온다. 재시도해도 같은 결과.
        repeat(2) {
            mockMvc.post("/vehicle-api/v1/me/next-driver/$reservationId/applied") { bearer(car.token) }.andExpect {
                status { isOk() }
                jsonPath("$.profileId") { value(kidProfile) }
                jsonPath("$.skin.name") { value("기본") }
                jsonPath("$.widgets[0]") { value("music") }
                jsonPath("$.ambientLight.color") { value("#FFFFFF") }
                jsonPath("$.ambientLight.brightness") { value(80) }
                jsonPath("$.welcomeSoundId") { value("chime-01") }
                jsonPath("$.greeting") { value("대학생 자녀님, 밤 드라이브 설정을 적용했어요") }
            }
        }
        mockMvc.get("/api/v1/vehicles/${car.id}/next-driver") { bearer(kid) }.andExpect { status { isNoContent() } }
        mockMvc.get("/vehicle-api/v1/me/next-driver") { bearer(car.token) }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `남의 프로필로는 예약할 수 없고, 취소한 예약은 적용 보고가 409`() {
        val car = registerVehicle()
        val mom = signUp("엄마")
        val dad = signUp("아빠")
        pair(mom, car.pairingCode)
        pair(dad, car.pairingCode)
        val momProfile = createProfile(mom, """{"name":"엄마"}""")

        mockMvc.put("/api/v1/vehicles/${car.id}/next-driver") {
            bearer(dad)
            json("""{"profileId":"$momProfile"}""")
        }.andExpect { status { isNotFound() } }

        mockMvc.put("/api/v1/vehicles/${car.id}/next-driver") {
            bearer(mom)
            json("""{"profileId":"$momProfile"}""")
        }
        val reservationId = mockMvc.get("/vehicle-api/v1/me/next-driver") { bearer(car.token) }
            .andReturn().response.contentAsString.read("$.reservationId")

        mockMvc.delete("/api/v1/vehicles/${car.id}/next-driver") { bearer(dad) }.andExpect { status { isNoContent() } }
        mockMvc.post("/vehicle-api/v1/me/next-driver/$reservationId/applied") { bearer(car.token) }
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `예약된 프로필이 삭제되면 예약이 없는 것으로 본다`() {
        val car = registerVehicle()
        val mom = signUp("엄마")
        pair(mom, car.pairingCode)
        val profile = createProfile(mom, """{"name":"엄마"}""")
        mockMvc.put("/api/v1/vehicles/${car.id}/next-driver") {
            bearer(mom)
            json("""{"profileId":"$profile"}""")
        }

        mockMvc.delete("/api/v1/profiles/$profile") { bearer(mom) }
        mockMvc.get("/vehicle-api/v1/me/next-driver") { bearer(car.token) }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `예약이 없으면 1탭으로 고른 프로필도 같은 순서로 적용한다`() {
        val car = registerVehicle()
        val mom = signUp("엄마")
        val dad = signUp("아빠")
        pair(mom, car.pairingCode)
        pair(dad, car.pairingCode)
        createProfile(mom, """{"name":"출근","light":{"brightness":60}}""")
        createProfile(mom, """{"name":"주말"}""")
        val outsider = createProfile(signUp("남"), """{"name":"보이면 안 됨"}""")

        val list = mockMvc.get("/vehicle-api/v1/me/profiles") { bearer(car.token) }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(2))
            jsonPath("$[0].nickname") { value("엄마") }
            jsonPath("$[0].profiles", hasSize<Any>(2))
            jsonPath("$[0].profiles[0].vehicleSettings.climate.temperature") { value(22.0) }
            jsonPath("$[0].profiles[0].skin") { doesNotExist() }
            jsonPath("$[1].profiles", hasSize<Any>(0))
        }.andReturn().response.contentAsString
        val chosen = list.read("$[0].profiles[0].profileId")

        mockMvc.post("/vehicle-api/v1/me/profiles/$chosen/applied") { bearer(car.token) }.andExpect {
            status { isOk() }
            jsonPath("$.ambientLight.brightness") { value(60) }
            jsonPath("$.greeting") { value("엄마님, 출근 설정을 적용했어요") }
        }
        // 이 차 멤버가 아닌 사람의 프로필은 적용할 수 없다.
        mockMvc.post("/vehicle-api/v1/me/profiles/$outsider/applied") { bearer(car.token) }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `사용자 토큰과 차량 토큰은 서로의 API에 쓸 수 없다`() {
        val car = registerVehicle()
        val mom = signUp("엄마")

        mockMvc.get("/vehicle-api/v1/me") { bearer(mom) }.andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/profiles") { bearer(car.token) }.andExpect { status { isUnauthorized() } }
        assertEquals(1, vehicles.count())
    }
}

package com.pleos.backend.qa

import com.pleos.backend.profile.ClimateDto
import com.pleos.backend.profile.DisplayDto
import com.pleos.backend.profile.LightDto
import com.pleos.backend.profile.ProfileRequest
import com.pleos.backend.profile.ProfileService
import com.pleos.backend.profile.SeatDto
import com.pleos.backend.profile.SeatPositionRequest
import com.pleos.backend.profile.SeatPositionService
import com.pleos.backend.reservation.ReservationService
import com.pleos.backend.reservation.ReserveRequest
import com.pleos.backend.theme.ButtonStyle
import com.pleos.backend.theme.CharacterPlacement
import com.pleos.backend.theme.FontStyle
import com.pleos.backend.theme.ThemeColorsDto
import com.pleos.backend.theme.ThemeRequest
import com.pleos.backend.theme.ThemeService
import com.pleos.backend.theme.WidgetStyle
import com.pleos.backend.user.SignUpRequest
import com.pleos.backend.user.UserService
import com.pleos.backend.vehicle.PairRequest
import com.pleos.backend.vehicle.VehicleRegisterRequest
import com.pleos.backend.vehicle.VehicleService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.UUID

/**
 * 화면이 나오기 전 QA용 임시 콘솔(/qa)과 데모 데이터 시드. 개발 서버 전용이다.
 * 운영 배포에서는 pleos.qa.enabled=false로 끈다(콘솔과 시드 API가 모두 사라진다).
 */
@Configuration
@ConditionalOnProperty(prefix = "pleos.qa", name = ["enabled"], havingValue = "true")
class QaConsoleConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/qa/**").addResourceLocations("classpath:/qa-console/")
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addRedirectViewController("/qa", "/qa/")
        registry.addViewController("/qa/").setViewName("forward:/qa/index.html")
    }
}

data class SeededUser(val nickname: String, val userId: UUID, val token: String)

data class SeededVehicle(val id: UUID, val token: String, val pairingCode: String, val model: String)

data class SeedResponse(
    val vehicle: SeededVehicle,
    val users: List<SeededUser>,
    val reservedProfileId: UUID,
    val sharedThemeCode: String,
)

/** 데모 시나리오 A(가족 공유차)를 한 번에 만든다. 부를 때마다 새 가족·새 차량이 생긴다. */
@Hidden
@RestController
@ConditionalOnProperty(prefix = "pleos.qa", name = ["enabled"], havingValue = "true")
class QaSeedController(
    private val users: UserService,
    private val vehicles: VehicleService,
    private val themes: ThemeService,
    private val profiles: ProfileService,
    private val seats: SeatPositionService,
    private val reservations: ReservationService,
) {

    @PostMapping("/qa/api/seed")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun seed(): SeedResponse {
        val car = vehicles.register(VehicleRegisterRequest("IONIQ 5"))
        val (mom, dad, kid) = listOf("엄마", "아빠", "대학생 자녀").map { users.signUp(SignUpRequest(it)) }
        vehicles.pair(mom.user.id, PairRequest(car.pairingCode, "우리집 아이오닉"))
        vehicles.pair(dad.user.id, PairRequest(car.pairingCode))
        vehicles.pair(kid.user.id, PairRequest(car.pairingCode))

        val sea = themes.create(
            mom.user.id,
            ThemeRequest(
                name = "새벽 바다",
                colors = ThemeColorsDto("#06222B", "#0E3A47", "#00796B", "#FFFFFF", "#E0F2F1"),
                buttonStyle = ButtonStyle.ROUNDED,
                widgetStyle = WidgetStyle.FILLED,
                ambientColor = "#00BCD4",
            ),
        )
        val cat = themes.create(
            kid.user.id,
            ThemeRequest(
                name = "고양이 밤",
                colors = ThemeColorsDto("#0D0B1E", "#1C1840", "#7C4DFF", "#FFFFFF", "#F5F3FF"),
                characterPlacement = CharacterPlacement.BOTTOM_LEFT,
                buttonStyle = ButtonStyle.PILL,
                widgetStyle = WidgetStyle.GLASS,
                fontStyle = FontStyle.ROUNDED,
                ambientColor = "#7C4DFF",
            ),
        )
        val shared = themes.share(kid.user.id, cat.id)

        val momProfile = profiles.create(
            mom.user.id,
            ProfileRequest(
                name = "출근",
                display = DisplayDto(listOf("weather", "music")),
                climate = ClimateDto(22.0, 3),
                themeId = sea.id,
            ),
        )
        profiles.create(
            dad.user.id,
            ProfileRequest(name = "장거리", seat = SeatDto(ventilationLevel = 2), climate = ClimateDto(21.0, 4)),
        )
        val kidProfile = profiles.create(
            kid.user.id,
            ProfileRequest(
                name = "밤 드라이브",
                display = DisplayDto(listOf("music", "weather", "battery")),
                seat = SeatDto(heatingLevel = 2, steeringWheelHeating = true),
                climate = ClimateDto(19.0, 4),
                light = LightDto(70),
                welcomeSoundId = "chime-01",
                themeId = cat.id,
            ),
        )
        seats.saveFromPhone(mom.user.id, momProfile.id, car.vehicleId, SeatPositionRequest(memorySlot = 1))
        seats.saveFromPhone(kid.user.id, kidProfile.id, car.vehicleId, SeatPositionRequest(memorySlot = 2))
        reservations.reserve(kid.user.id, car.vehicleId, ReserveRequest(kidProfile.id))

        return SeedResponse(
            vehicle = SeededVehicle(car.vehicleId, car.vehicleToken, car.pairingCode, "IONIQ 5"),
            users = listOf(mom, dad, kid).map { SeededUser(it.user.nickname, it.user.id, it.accessToken) },
            reservedProfileId = kidProfile.id,
            sharedThemeCode = shared.shareCode,
        )
    }
}

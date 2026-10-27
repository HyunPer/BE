package com.pleos.backend.vehicleapp

import com.pleos.backend.auth.AuthVehicle
import com.pleos.backend.common.notFound
import com.pleos.backend.profile.ApplicableProfileDto
import com.pleos.backend.profile.ProfileRepository
import com.pleos.backend.profile.SeatPositionDto
import com.pleos.backend.profile.SeatPositionRequest
import com.pleos.backend.profile.SeatPositionService
import com.pleos.backend.profile.toApplicable
import com.pleos.backend.reservation.NextDriverResponse
import com.pleos.backend.reservation.ReservationService
import com.pleos.backend.theme.WelcomeResponse
import com.pleos.backend.theme.WelcomeService
import com.pleos.backend.user.UserRepository
import com.pleos.backend.vehicle.VehicleRegisterRequest
import com.pleos.backend.vehicle.VehicleRegisterResponse
import com.pleos.backend.vehicle.VehicleSelfResponse
import com.pleos.backend.vehicle.VehicleService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 1탭 선택 화면용. 예약이 없을 때의 대체안. 테마는 적용 보고 후에 받는다. */
data class DriverProfilesResponse(
    val userId: UUID,
    val nickname: String,
    val profiles: List<ApplicableProfileDto>,
)

@Service
@Transactional(readOnly = true)
class VehicleAppService(
    private val vehicles: VehicleService,
    private val users: UserRepository,
    private val profiles: ProfileRepository,
    private val welcome: WelcomeService,
    private val seatPositions: SeatPositionService,
) {
    fun driverProfiles(vehicleId: UUID): List<DriverProfilesResponse> {
        val userIds = vehicles.memberUserIds(vehicleId)
        val nicknames = users.findAllById(userIds).associate { it.requireId() to it.nickname }
        val all = profiles.findAllByOwnerIdInOrderByCreatedAtAsc(userIds)
        val seats = seatPositions.findForVehicle(vehicleId, all.map { it.requireId() })
        val byOwner = all.groupBy { it.ownerId }
        return userIds.map { id ->
            DriverProfilesResponse(id, nicknames[id].orEmpty(), byOwner[id].orEmpty().map { it.toApplicable(seats[it.requireId()]) })
        }
    }

    /** 1탭으로 고른 프로필의 차량 설정을 적용한 뒤 호출한다. 이 차량 멤버의 프로필만 허용. */
    fun welcomeForSelected(vehicleId: UUID, profileId: UUID): WelcomeResponse {
        val profile = profiles.findById(profileId).orElse(null)
            ?.takeIf { it.ownerId in vehicles.memberUserIds(vehicleId) }
            ?: throw notFound("프로필을 찾을 수 없습니다: $profileId")
        val nickname = users.findById(profile.ownerId).map { it.nickname }.orElse("")
        return welcome.build(profile, nickname)
    }
}

/**
 * 차량 앱(Pleos Connect) 전용 API. 등록을 뺀 모든 요청은 차량 토큰이 필요하다.
 * 흐름: 첫 실행 → 등록·코드 표시
 *       탑승 → next-driver 조회 → 차량 설정 적용 → applied 보고 → 응답의 테마·조명·사운드 적용
 *       예약 없음(204) → 프로필 목록에서 1탭 선택 → 차량 설정 적용 → 선택 프로필 applied 보고 → 테마 적용
 */
@Tag(name = "차량 앱")
@RestController
@RequestMapping("/vehicle-api/v1")
class VehicleAppController(
    private val vehicles: VehicleService,
    private val reservations: ReservationService,
    private val service: VehicleAppService,
    private val seatPositions: SeatPositionService,
) {

    @Operation(summary = "차량 등록", description = "차량 앱 첫 실행 때 한 번. vehicleToken을 저장하고 pairingCode를 화면에 띄운다.")
    @SecurityRequirements
    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: VehicleRegisterRequest): VehicleRegisterResponse = vehicles.register(request)

    @Operation(summary = "내 차량 정보", description = "페어링 코드·멤버 수를 다시 보여줄 때.")
    @GetMapping("/me")
    fun me(vehicle: AuthVehicle): VehicleSelfResponse = vehicles.self(vehicle.id)

    @Operation(summary = "멤버별 프로필 목록", description = "1탭 선택 화면용. 차량 설정만 들어 있고 테마는 적용 보고 후에 받는다.")
    @GetMapping("/me/profiles")
    fun driverProfiles(vehicle: AuthVehicle): List<DriverProfilesResponse> = service.driverProfiles(vehicle.id)

    @Operation(summary = "다음 운전자 조회", description = "앱이 열리거나 탑승을 감지했을 때 호출. 차량 설정만 내려준다. 예약이 없으면 204.")
    @GetMapping("/me/next-driver")
    fun nextDriver(vehicle: AuthVehicle): ResponseEntity<NextDriverResponse> =
        reservations.nextForVehicle(vehicle.id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @Operation(
        summary = "예약 프로필 적용 완료 보고",
        description = "차량 설정을 SDK로 적용한 뒤 호출. 응답의 테마·조명·웰컴 사운드를 이어서 적용한다. 재시도해도 같은 응답.",
    )
    @PostMapping("/me/next-driver/{reservationId}/applied")
    fun applied(vehicle: AuthVehicle, @PathVariable reservationId: UUID): WelcomeResponse =
        reservations.markApplied(vehicle.id, reservationId)

    @Operation(
        summary = "선택 프로필 적용 완료 보고",
        description = "1탭으로 고른 프로필의 차량 설정을 적용한 뒤 호출. 응답의 테마·조명·웰컴 사운드를 이어서 적용한다.",
    )
    @PostMapping("/me/profiles/{profileId}/applied")
    fun appliedSelected(vehicle: AuthVehicle, @PathVariable profileId: UUID): WelcomeResponse =
        service.welcomeForSelected(vehicle.id, profileId)

    @Operation(
        summary = "지금 자세 저장",
        description = "차 안에서 시트를 맞춘 뒤 호출. 차량 앱이 SDK로 읽은 현재 위치(차량 단위)와 메모리 슬롯을 이 차량 기준으로 저장한다.",
    )
    @PutMapping("/me/profiles/{profileId}/seat-position")
    fun saveSeatPosition(
        vehicle: AuthVehicle,
        @PathVariable profileId: UUID,
        @Valid @RequestBody request: SeatPositionRequest,
    ): SeatPositionDto = seatPositions.saveFromVehicle(vehicle.id, profileId, request)
}

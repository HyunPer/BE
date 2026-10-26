package com.pleos.backend.vehicleapp

import com.pleos.backend.auth.AuthVehicle
import com.pleos.backend.profile.ProfileRepository
import com.pleos.backend.profile.ProfileResponse
import com.pleos.backend.profile.toResponse
import com.pleos.backend.reservation.NextDriverResponse
import com.pleos.backend.reservation.ReservationResponse
import com.pleos.backend.reservation.ReservationService
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
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 시동 직후 1탭 선택 화면용. 예약이 없거나 자동 실행이 막힐 때의 대체안. */
data class DriverProfilesResponse(
    val userId: UUID,
    val nickname: String,
    val profiles: List<ProfileResponse>,
)

@Service
@Transactional(readOnly = true)
class VehicleAppService(
    private val vehicles: VehicleService,
    private val users: UserRepository,
    private val profiles: ProfileRepository,
) {
    fun driverProfiles(vehicleId: UUID): List<DriverProfilesResponse> {
        val userIds = vehicles.memberUserIds(vehicleId)
        val nicknames = users.findAllById(userIds).associate { it.requireId() to it.nickname }
        val byOwner = profiles.findAllByOwnerIdInOrderByCreatedAtAsc(userIds).groupBy { it.ownerId }
        return userIds.map { id ->
            DriverProfilesResponse(id, nicknames[id].orEmpty(), byOwner[id].orEmpty().map { it.toResponse() })
        }
    }
}

/**
 * 차량 앱(Pleos Connect) 전용 API. 등록을 뺀 모든 요청은 차량 토큰이 필요하다.
 * 흐름: 첫 실행 → 등록·코드 표시 / 시동 → next-driver 조회 → SDK 적용 → applied 보고
 */
@Tag(name = "차량 앱")
@RestController
@RequestMapping("/vehicle-api/v1")
class VehicleAppController(
    private val vehicles: VehicleService,
    private val reservations: ReservationService,
    private val service: VehicleAppService,
) {

    @Operation(summary = "차량 등록", description = "차량 앱 첫 실행 때 한 번. vehicleToken을 저장하고 pairingCode를 화면에 띄운다.")
    @SecurityRequirements
    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: VehicleRegisterRequest): VehicleRegisterResponse = vehicles.register(request)

    @Operation(summary = "내 차량 정보", description = "페어링 코드·멤버 수를 다시 보여줄 때.")
    @GetMapping("/me")
    fun me(vehicle: AuthVehicle): VehicleSelfResponse = vehicles.self(vehicle.id)

    @Operation(summary = "멤버별 프로필 목록", description = "시동 직후 1탭 선택 화면용.")
    @GetMapping("/me/profiles")
    fun driverProfiles(vehicle: AuthVehicle): List<DriverProfilesResponse> = service.driverProfiles(vehicle.id)

    @Operation(summary = "다음 운전자 조회", description = "시동(IGNITION_STATE) 감지 시 호출. 예약이 없으면 204.")
    @GetMapping("/me/next-driver")
    fun nextDriver(vehicle: AuthVehicle): ResponseEntity<NextDriverResponse> =
        reservations.nextForVehicle(vehicle.id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @Operation(summary = "적용 완료 보고", description = "SDK 적용 후 호출. 재시도해도 안전하다.")
    @PostMapping("/me/next-driver/{reservationId}/applied")
    fun applied(vehicle: AuthVehicle, @PathVariable reservationId: UUID): ReservationResponse =
        reservations.markApplied(vehicle.id, reservationId)
}

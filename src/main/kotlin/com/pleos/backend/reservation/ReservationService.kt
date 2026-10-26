package com.pleos.backend.reservation

import com.pleos.backend.common.conflict
import com.pleos.backend.common.notFound
import com.pleos.backend.profile.Profile
import com.pleos.backend.profile.ProfileRepository
import com.pleos.backend.profile.VehicleSettingsDto
import com.pleos.backend.profile.toVehicleSettings
import com.pleos.backend.theme.WelcomeResponse
import com.pleos.backend.theme.WelcomeService
import com.pleos.backend.user.UserRepository
import com.pleos.backend.vehicle.VehicleService
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface ReservationRepository : JpaRepository<Reservation, UUID> {
    fun findFirstByVehicleIdAndStatus(vehicleId: UUID, status: ReservationStatus): Reservation?
    fun findByIdAndVehicleId(id: UUID, vehicleId: UUID): Reservation?
}

data class ReserveRequest(val profileId: UUID)

data class ReservationResponse(
    val id: UUID,
    val vehicleId: UUID,
    val driverUserId: UUID,
    val driverNickname: String,
    val profileId: UUID,
    val profileName: String,
    val status: ReservationStatus,
    val createdAt: Instant,
    val appliedAt: Instant?,
)

/**
 * 차량 앱이 받는 1단계 값. vehicleSettings를 Vehicle SDK로 적용한 뒤 applied를 보고하면
 * 2단계 웰컴 묶음(테마·조명·사운드)을 받는다. 테마가 차량 설정보다 먼저 적용되는 일이 없도록 나눠서 내려준다.
 */
data class NextDriverResponse(
    val reservationId: UUID,
    val driverUserId: UUID,
    val driverNickname: String,
    val profileId: UUID,
    val profileName: String,
    val vehicleSettings: VehicleSettingsDto,
)

@Service
@Transactional(readOnly = true)
class ReservationService(
    private val reservations: ReservationRepository,
    private val vehicles: VehicleService,
    private val profiles: ProfileRepository,
    private val users: UserRepository,
    private val welcome: WelcomeService,
) {

    // ── 폰 앱 ──

    /** 내 프로필로 다음 운전자를 예약한다. 기존 PENDING 예약은 취소된다. */
    @Transactional
    fun reserve(userId: UUID, vehicleId: UUID, request: ReserveRequest): ReservationResponse {
        vehicles.requireMember(userId, vehicleId)
        val profile = profiles.findByIdAndOwnerId(request.profileId, userId)
            ?: throw notFound("프로필을 찾을 수 없습니다: ${request.profileId}")
        reservations.findFirstByVehicleIdAndStatus(vehicleId, ReservationStatus.PENDING)?.cancel()
        val saved = reservations.saveAndFlush(Reservation(vehicleId = vehicleId, userId = userId, profileId = profile.requireId()))
        return toResponse(saved, profile)
    }

    fun current(userId: UUID, vehicleId: UUID): ReservationResponse? {
        vehicles.requireMember(userId, vehicleId)
        return pendingWithProfile(vehicleId)?.let { (reservation, profile) -> toResponse(reservation, profile) }
    }

    @Transactional
    fun cancel(userId: UUID, vehicleId: UUID) {
        vehicles.requireMember(userId, vehicleId)
        reservations.findFirstByVehicleIdAndStatus(vehicleId, ReservationStatus.PENDING)?.cancel()
    }

    // ── 차량 앱 ──

    fun nextForVehicle(vehicleId: UUID): NextDriverResponse? =
        pendingWithProfile(vehicleId)?.let { (reservation, profile) ->
            NextDriverResponse(
                reservationId = reservation.requireId(),
                driverUserId = reservation.userId,
                driverNickname = nicknameOf(reservation.userId),
                profileId = profile.requireId(),
                profileName = profile.name,
                vehicleSettings = profile.toVehicleSettings(),
            )
        }

    /**
     * 차량 설정 적용 완료를 기록하고 2단계 웰컴 묶음을 돌려준다.
     * 같은 예약을 다시 보고해도 같은 묶음을 돌려준다(재시도·앱 재시작 안전). 취소된 예약은 409.
     */
    @Transactional
    fun markApplied(vehicleId: UUID, reservationId: UUID): WelcomeResponse {
        val reservation = reservations.findByIdAndVehicleId(reservationId, vehicleId)
            ?: throw notFound("예약을 찾을 수 없습니다: $reservationId")
        when (reservation.status) {
            ReservationStatus.PENDING -> reservation.markApplied()
            ReservationStatus.APPLIED -> Unit
            ReservationStatus.CANCELLED -> throw conflict("이미 취소된 예약입니다: $reservationId")
        }
        val profile = profiles.findById(reservation.profileId).orElse(null)
            ?: throw notFound("예약된 프로필이 삭제되었습니다: ${reservation.profileId}")
        return welcome.build(profile, nicknameOf(reservation.userId))
    }

    /** 예약된 프로필이 그 사이 삭제됐다면 예약이 없는 것으로 본다. */
    private fun pendingWithProfile(vehicleId: UUID): Pair<Reservation, Profile>? {
        val reservation = reservations.findFirstByVehicleIdAndStatus(vehicleId, ReservationStatus.PENDING) ?: return null
        val profile = profiles.findById(reservation.profileId).orElse(null) ?: return null
        return reservation to profile
    }

    private fun nicknameOf(userId: UUID): String = users.findById(userId).map { it.nickname }.orElse("")

    private fun toResponse(reservation: Reservation, profile: Profile) = ReservationResponse(
        id = reservation.requireId(),
        vehicleId = reservation.vehicleId,
        driverUserId = reservation.userId,
        driverNickname = nicknameOf(reservation.userId),
        profileId = reservation.profileId,
        profileName = profile.name,
        status = reservation.status,
        createdAt = reservation.createdAt,
        appliedAt = reservation.appliedAt,
    )
}

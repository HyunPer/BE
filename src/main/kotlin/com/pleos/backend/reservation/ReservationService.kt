package com.pleos.backend.reservation

import com.pleos.backend.common.conflict
import com.pleos.backend.common.notFound
import com.pleos.backend.profile.Profile
import com.pleos.backend.profile.ProfileRepository
import com.pleos.backend.profile.ProfileResponse
import com.pleos.backend.profile.toResponse
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
    val profileName: String?,
    val status: ReservationStatus,
    val createdAt: Instant,
    val appliedAt: Instant?,
)

/** 차량 앱이 시동 시 받는 값. profile을 그대로 Vehicle SDK에 적용한다. */
data class NextDriverResponse(
    val reservationId: UUID,
    val driverUserId: UUID,
    val driverNickname: String,
    val profile: ProfileResponse,
)

@Service
@Transactional(readOnly = true)
class ReservationService(
    private val reservations: ReservationRepository,
    private val vehicles: VehicleService,
    private val profiles: ProfileRepository,
    private val users: UserRepository,
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
            NextDriverResponse(reservation.requireId(), reservation.userId, nicknameOf(reservation.userId), profile.toResponse())
        }

    /** 같은 예약을 두 번 보고해도 그대로 성공한다(재시도 안전). 취소된 예약은 409. */
    @Transactional
    fun markApplied(vehicleId: UUID, reservationId: UUID): ReservationResponse {
        val reservation = reservations.findByIdAndVehicleId(reservationId, vehicleId)
            ?: throw notFound("예약을 찾을 수 없습니다: $reservationId")
        when (reservation.status) {
            ReservationStatus.PENDING -> reservation.markApplied()
            ReservationStatus.APPLIED -> Unit
            ReservationStatus.CANCELLED -> throw conflict("이미 취소된 예약입니다: $reservationId")
        }
        return toResponse(reservation, profiles.findById(reservation.profileId).orElse(null))
    }

    /** 예약된 프로필이 그 사이 삭제됐다면 예약이 없는 것으로 본다. */
    private fun pendingWithProfile(vehicleId: UUID): Pair<Reservation, Profile>? {
        val reservation = reservations.findFirstByVehicleIdAndStatus(vehicleId, ReservationStatus.PENDING) ?: return null
        val profile = profiles.findById(reservation.profileId).orElse(null) ?: return null
        return reservation to profile
    }

    private fun nicknameOf(userId: UUID): String = users.findById(userId).map { it.nickname }.orElse("")

    private fun toResponse(reservation: Reservation, profile: Profile?) = ReservationResponse(
        id = reservation.requireId(),
        vehicleId = reservation.vehicleId,
        driverUserId = reservation.userId,
        driverNickname = nicknameOf(reservation.userId),
        profileId = reservation.profileId,
        profileName = profile?.name,
        status = reservation.status,
        createdAt = reservation.createdAt,
        appliedAt = reservation.appliedAt,
    )
}

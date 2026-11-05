package com.pleos.backend.trip

import com.pleos.backend.common.notFound
import com.pleos.backend.user.UserRepository
import com.pleos.backend.vehicle.VehicleService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface TripRepository : JpaRepository<Trip, UUID> {
    fun findByIdAndVehicleId(id: UUID, vehicleId: UUID): Trip?
    fun findAllByVehicleIdAndEndedAtIsNull(vehicleId: UUID): List<Trip>
    fun findByVehicleIdOrderByStartedAtDesc(vehicleId: UUID, limit: Limit): List<Trip>
}

interface TripCheckInRepository : JpaRepository<TripCheckIn, UUID> {
    fun findByTripIdAndUserId(tripId: UUID, userId: UUID): TripCheckIn?
    fun findAllByTripIdInAndUserId(tripIds: Collection<UUID>, userId: UUID): List<TripCheckIn>
}

interface RiderConditionRepository : JpaRepository<RiderCondition, UUID> {
    fun findByUserId(userId: UUID): RiderCondition?
    fun findAllByUserIdIn(userIds: Collection<UUID>): List<RiderCondition>
}

/** 차량 앱이 도착 때 보내는 주행 요약. */
data class TripSummaryRequest(
    @field:Min(0) @field:Max(1440) val drivingMinutes: Int,
    @field:Min(0) @field:Max(1440) val congestionMinutes: Int,
    @field:Min(0) @field:Max(1000) val sharpTurns: Int,
    @field:Min(0) @field:Max(100) val peakRisk: Int,
)

data class TripResponse(
    val id: UUID,
    val vehicleId: UUID,
    val startedAt: Instant,
    val endedAt: Instant?,
    val drivingMinutes: Int,
    val congestionMinutes: Int,
    val sharpTurns: Int,
    val peakRisk: Int,
    /** 폰에서 볼 때 내 체크인. 차량 앱 응답에서는 항상 null. */
    val myMood: Mood?,
    val myNote: String?,
)

data class CheckInRequest(
    val mood: Mood,
    @field:Size(max = 200) val note: String? = null,
)

data class RiderConditionDto(
    val motionSensitivity: MotionSensitivity = MotionSensitivity.MEDIUM,
    val restStyle: RestStyle = RestStyle.QUIET,
)

/** 차량 앱이 이 차의 가족별 컨디션을 받아 멀미 위험도 배율을 정한다. */
data class RiderResponse(val userId: UUID, val nickname: String, val condition: RiderConditionDto)

@Service
@Transactional(readOnly = true)
class TripService(
    private val trips: TripRepository,
    private val checkIns: TripCheckInRepository,
    private val conditions: RiderConditionRepository,
    private val vehicles: VehicleService,
    private val users: UserRepository,
) {

    // ── 차량 앱 ──

    /** 탑승 감지 때. 앱이 꺼지는 등으로 끝내지 못한 트립은 요약 없이 닫는다. */
    @Transactional
    fun start(vehicleId: UUID): TripResponse {
        trips.findAllByVehicleIdAndEndedAtIsNull(vehicleId).forEach { it.finish(0, 0, 0, 0) }
        return trips.save(Trip(vehicleId)).toResponse(null)
    }

    /** 도착 때. 같은 트립을 다시 끝내도 처음 요약을 그대로 돌려준다(재시도 안전). */
    @Transactional
    fun finish(vehicleId: UUID, tripId: UUID, summary: TripSummaryRequest): TripResponse {
        val trip = trips.findByIdAndVehicleId(tripId, vehicleId) ?: throw notFound("트립을 찾을 수 없습니다: $tripId")
        if (trip.isOpen) {
            trip.finish(summary.drivingMinutes, summary.congestionMinutes, summary.sharpTurns, summary.peakRisk)
        }
        return trip.toResponse(null)
    }

    fun riders(vehicleId: UUID): List<RiderResponse> {
        val ids = vehicles.memberUserIds(vehicleId)
        val nicknames = users.findAllById(ids).associate { it.requireId() to it.nickname }
        val byUser = conditions.findAllByUserIdIn(ids).associateBy { it.userId }
        return ids.map { id -> RiderResponse(id, nicknames[id].orEmpty(), byUser[id]?.toDto() ?: RiderConditionDto()) }
    }

    // ── 폰 앱 ──

    /** 이 차의 최근 트립(최신순)과 내 체크인. 멤버가 아니면 404. */
    fun recent(userId: UUID, vehicleId: UUID, limit: Int): List<TripResponse> {
        vehicles.requireMember(userId, vehicleId)
        val recent = trips.findByVehicleIdOrderByStartedAtDesc(vehicleId, Limit.of(limit.coerceIn(1, 50)))
        val mine = checkIns.findAllByTripIdInAndUserId(recent.map { it.requireId() }, userId).associateBy { it.tripId }
        return recent.map { it.toResponse(mine[it.requireId()]) }
    }

    /** 도착 체크인. 이 차의 멤버만 남길 수 있고, 다시 남기면 덮어쓴다. */
    @Transactional
    fun checkIn(userId: UUID, tripId: UUID, request: CheckInRequest): TripResponse {
        val trip = trips.findById(tripId).orElse(null)
            ?.takeIf { userId in vehicles.memberUserIds(it.vehicleId) }
            ?: throw notFound("트립을 찾을 수 없습니다: $tripId")
        val saved = checkIns.findByTripIdAndUserId(tripId, userId)
            ?.apply {
                mood = request.mood
                note = request.note
            }
            ?: checkIns.save(TripCheckIn(tripId, userId, request.mood, request.note))
        return trip.toResponse(saved)
    }

    fun myCondition(userId: UUID): RiderConditionDto = conditions.findByUserId(userId)?.toDto() ?: RiderConditionDto()

    @Transactional
    fun updateMyCondition(userId: UUID, request: RiderConditionDto): RiderConditionDto {
        val condition = conditions.findByUserId(userId) ?: conditions.save(RiderCondition(userId))
        condition.motionSensitivity = request.motionSensitivity
        condition.restStyle = request.restStyle
        return condition.toDto()
    }

    private fun Trip.toResponse(checkIn: TripCheckIn?) = TripResponse(
        id = requireId(),
        vehicleId = vehicleId,
        startedAt = startedAt,
        endedAt = endedAt,
        drivingMinutes = drivingMinutes,
        congestionMinutes = congestionMinutes,
        sharpTurns = sharpTurns,
        peakRisk = peakRisk,
        myMood = checkIn?.mood,
        myNote = checkIn?.note,
    )

    private fun RiderCondition.toDto() = RiderConditionDto(motionSensitivity, restStyle)
}
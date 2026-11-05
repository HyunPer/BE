package com.pleos.backend.trip

import com.pleos.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/** 도착 체크인에서 고르는 기분. 상담이 아니라 스스로 남기는 한 줄 기록이다. */
enum class Mood { CALM, OK, TIRED, STRESSED, QUEASY }

/** 멀미를 얼마나 타는지. 차량 앱의 멀미 위험도 계산에서 배율로 쓴다. */
enum class MotionSensitivity { LOW, MEDIUM, HIGH }

/** 쉼표가 권할 쉬는 방식. */
enum class RestStyle { QUIET, BREATHING, MUSIC, FRESH_AIR }

/**
 * 차를 한 번 타고 내린 기록. 차량 앱이 탑승을 감지하면 시작하고, 도착하면 주행 요약과 함께 끝낸다.
 * 요약 값은 차량 앱이 Vehicle SDK(조향·브레이크)와 NaviHelper(회전·주의 구간)로 계산한다.
 */
@Entity
@Table(name = "trips", indexes = [Index(columnList = "vehicle_id, started_at")])
class Trip(
    @Column(name = "vehicle_id", nullable = false, updatable = false)
    val vehicleId: UUID,

    @Column(name = "started_at", nullable = false, updatable = false)
    val startedAt: Instant = Instant.now(),
) : BaseEntity() {

    @Column(name = "ended_at")
    var endedAt: Instant? = null

    @Column(name = "driving_minutes", nullable = false)
    var drivingMinutes: Int = 0

    /** 가다 서다가 잦았던 시간. */
    @Column(name = "congestion_minutes", nullable = false)
    var congestionMinutes: Int = 0

    /** 지나온 급커브·U턴·회전교차로 수. */
    @Column(name = "sharp_turns", nullable = false)
    var sharpTurns: Int = 0

    /** 가장 높았던 멀미 위험도(0–100). */
    @Column(name = "peak_risk", nullable = false)
    var peakRisk: Int = 0

    val isOpen: Boolean get() = endedAt == null

    fun finish(drivingMinutes: Int, congestionMinutes: Int, sharpTurns: Int, peakRisk: Int) {
        this.drivingMinutes = drivingMinutes
        this.congestionMinutes = congestionMinutes
        this.sharpTurns = sharpTurns
        this.peakRisk = peakRisk
        endedAt = Instant.now()
    }
}

/** 트립 하나에 사람마다 하나. 다시 남기면 덮어쓴다. */
@Entity
@Table(name = "trip_check_ins", uniqueConstraints = [UniqueConstraint(columnNames = ["trip_id", "user_id"])])
class TripCheckIn(
    @Column(name = "trip_id", nullable = false, updatable = false)
    val tripId: UUID,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var mood: Mood,

    @Column(length = 200)
    var note: String? = null,
) : BaseEntity()

/** 탑승자 컨디션 프로필. 사람마다 하나이고 차와는 무관하다. */
@Entity
@Table(name = "rider_conditions")
class RiderCondition(
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "motion_sensitivity", nullable = false, length = 20)
    var motionSensitivity: MotionSensitivity = MotionSensitivity.MEDIUM,

    @Enumerated(EnumType.STRING)
    @Column(name = "rest_style", nullable = false, length = 20)
    var restStyle: RestStyle = RestStyle.QUIET,
) : BaseEntity()
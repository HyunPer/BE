package com.pleos.backend.reservation

import com.pleos.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class ReservationStatus { PENDING, APPLIED, CANCELLED }

/**
 * "다음 운전자" 예약. 차량당 PENDING은 하나만 유지하고, 새 예약이 들어오면 이전 것은 CANCELLED.
 * 차량 앱이 시동 시 PENDING을 받아 적용한 뒤 APPLIED로 보고한다.
 */
@Entity
@Table(name = "reservations", indexes = [Index(columnList = "vehicle_id, status")])
class Reservation(
    @Column(name = "vehicle_id", nullable = false, updatable = false)
    val vehicleId: UUID,

    /** 예약한 사람 = 다음에 운전할 사람. */
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Column(name = "profile_id", nullable = false, updatable = false)
    val profileId: UUID,
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var status: ReservationStatus = ReservationStatus.PENDING
        protected set

    var appliedAt: Instant? = null
        protected set

    fun markApplied() {
        check(status == ReservationStatus.PENDING) { "PENDING 예약만 적용할 수 있습니다: $status" }
        status = ReservationStatus.APPLIED
        appliedAt = Instant.now()
    }

    fun cancel() {
        check(status == ReservationStatus.PENDING) { "PENDING 예약만 취소할 수 있습니다: $status" }
        status = ReservationStatus.CANCELLED
    }
}

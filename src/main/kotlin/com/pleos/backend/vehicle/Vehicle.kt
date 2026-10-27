package com.pleos.backend.vehicle

import com.pleos.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

/**
 * 차량 앱이 설치된 차 1대. 차량 앱이 첫 실행 때 스스로 등록하고, 화면에 페어링 코드를 띄운다.
 */
@Entity
@Table(name = "vehicles")
class Vehicle(
    /** 차종. 데모는 에뮬레이터 차종 프로파일 이름을 그대로 쓴다. */
    @Column(nullable = false, length = 50)
    var model: String,

    @Column(nullable = false, unique = true, length = 64)
    var tokenHash: String,

    @Column(nullable = false, unique = true, length = 6)
    var pairingCode: String,
) : BaseEntity() {
    /** 첫 페어링한 사람(OWNER)이 붙이는 이름. 예: "우리집 아이오닉" */
    @Column(length = 20)
    var nickname: String? = null
}

enum class VehicleRole { OWNER, MEMBER }

/** 차량을 함께 쓰는 사람. 페어링 코드를 입력하면 들어온다. */
@Entity
@Table(
    name = "vehicle_members",
    uniqueConstraints = [UniqueConstraint(columnNames = ["vehicle_id", "user_id"])],
)
class VehicleMember(
    @Column(name = "vehicle_id", nullable = false, updatable = false)
    val vehicleId: UUID,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var role: VehicleRole,
) : BaseEntity()

/** 멤버가 나가거나 내보내졌다. 예약·시트 자세 정리는 각 도메인이 이 이벤트를 받아 처리한다. */
data class MemberLeftEvent(val vehicleId: UUID, val userId: UUID)

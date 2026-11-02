package com.pleos.backend.history

import com.pleos.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.util.UUID

enum class ApplySource{RESERVATION, SELECTED}
@Entity
@Table(name = "apply_logs", indexes = [Index(columnList = "vehicle_id, created_at")])
class ApplyLog(
    @Column(name = "vehicle_id", nullable = false, updatable = false)
    val vehicleId: UUID,
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,
    @Column(name = "profile_id", nullable = false, updatable = false)
    val profileId: UUID,
    @Column(name = "profile_name", nullable = false, updatable = false, length = 20)
    val profileName: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    val source: ApplySource,
): BaseEntity()
data class ProfileAppliedEvent(
    val vehicleId: UUID,
    val userId: UUID,
    val profileId: UUID,
    val profileName: String,
    val source: ApplySource,
)
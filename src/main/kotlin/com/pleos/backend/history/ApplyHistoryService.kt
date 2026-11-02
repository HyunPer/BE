package com.pleos.backend.history

import com.pleos.backend.user.UserRepository
import com.pleos.backend.vehicle.VehicleService
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

interface ApplyLogRepository : JpaRepository<ApplyLog, UUID> {
    fun findByVehicleIdOrderByCreatedAtDesc(vehicleId: UUID, limit: Limit): List<ApplyLog>
    fun findFirstByVehicleIdOrderByCreatedAtDesc(vehicleId: UUID): ApplyLog?
}

data class ApplyLogResponse(
    val driverUserId: UUID,
    val driverNickname: String,
    val profileId: UUID,
    val profileName: String,
    val source: ApplySource,
    val appliedAt: Instant,
)

@Service
@Transactional(readOnly = true)
class ApplyHistoryService(
    private val logs: ApplyLogRepository,
    private val vehicles: VehicleService,
    private val users: UserRepository,
) {

    @EventListener
    @Transactional
    fun onApplied(event: ProfileAppliedEvent) {
        val last = logs.findFirstByVehicleIdOrderByCreatedAtDesc(event.vehicleId)
        val duplicate = last != null && last.profileId == event.profileId && Duration.between(last.createdAt, Instant.now()) < DEDUP_WINDOW
        if (duplicate) return
        logs.save(ApplyLog(event.vehicleId, event.userId, event.profileId, event.profileName, event.source))
    }

    fun recent(userId: UUID, vehicleId: UUID, limit: Int): List<ApplyLogResponse> {
        vehicles.requireMember(userId, vehicleId)
        val recent = logs.findByVehicleIdOrderByCreatedAtDesc(vehicleId, Limit.of(limit.coerceIn(1, 50)))
        val nicknames = users.findAllById(recent.map { it.userId }.toSet()).associate { it.requireId() to it.nickname }
        return recent.map {
            ApplyLogResponse(it.userId, nicknames[it.userId].orEmpty(), it.profileId, it.profileName, it.source, it.createdAt)
        }
    }
    private companion object {
        val DEDUP_WINDOW: Duration = Duration.ofMinutes(1)
    }
}
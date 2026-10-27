package com.pleos.backend.profile

import com.pleos.backend.auth.AuthUser
import com.pleos.backend.common.ApiException
import com.pleos.backend.common.BaseEntity
import com.pleos.backend.common.notFound
import com.pleos.backend.vehicle.MemberLeftEvent
import com.pleos.backend.vehicle.VehicleRepository
import com.pleos.backend.vehicle.VehicleService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.context.event.EventListener
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * 프로필 × 차량별 시트 자세. Pleos Vehicle SDK는 시트를 절대 각도로 지정할 수 없고
 * (moveSeat*는 FORWARD/STOP/BACKWARD), 위치값의 범위·단위도 차종마다 달라서 차량별로 저장한다.
 * - memorySlot: 차량 시트 메모리 호출(selectSeatMemory). 있으면 차량 앱은 이것을 먼저 쓴다.
 * - backrestPosition·foreAftPosition: 차량 앱이 "지금 자세 저장" 때 읽은 현재 위치(차량 단위 그대로).
 *   메모리 슬롯이 없을 때 차량 앱이 위치를 구독하며 목표에서 STOP을 보내는 데 쓴다.
 */
@Entity
@Table(
    name = "seat_positions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["profile_id", "vehicle_id"])],
)
class SeatPosition(
    @Column(name = "profile_id", nullable = false, updatable = false)
    val profileId: UUID,

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    val vehicleId: UUID,
) : BaseEntity() {
    var memorySlot: Int? = null
    var backrestPosition: Int? = null
    var foreAftPosition: Int? = null

    fun replaceWith(request: SeatPositionRequest) {
        memorySlot = request.memorySlot
        backrestPosition = request.backrestPosition
        foreAftPosition = request.foreAftPosition
    }
}

interface SeatPositionRepository : JpaRepository<SeatPosition, UUID> {
    fun findByProfileIdAndVehicleId(profileId: UUID, vehicleId: UUID): SeatPosition?
    fun findAllByProfileIdOrderByCreatedAtAsc(profileId: UUID): List<SeatPosition>
    fun findAllByVehicleIdAndProfileIdIn(vehicleId: UUID, profileIds: Collection<UUID>): List<SeatPosition>
    fun deleteAllByProfileId(profileId: UUID)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "delete from SeatPosition s where s.vehicleId = :vehicleId " +
            "and s.profileId in (select p.id from Profile p where p.ownerId = :ownerId)",
    )
    fun deleteAllForOwnerOnVehicle(ownerId: UUID, vehicleId: UUID): Int
}

/**
 * 전체 교체(PUT). 셋 중 하나 이상 필요하다. 위치값은 차량 단위 그대로라 서버는 범위를 검사하지 않고,
 * 차량 앱이 checkSeatCapabilities의 min/max로 확인한다. 메모리 슬롯 범위는 임시 기준(1–10).
 */
data class SeatPositionRequest(
    @field:Min(1) @field:Max(10)
    val memorySlot: Int? = null,
    val backrestPosition: Int? = null,
    val foreAftPosition: Int? = null,
)

data class SeatPositionDto(
    val memorySlot: Int?,
    val backrestPosition: Int?,
    val foreAftPosition: Int?,
    val updatedAt: Instant,
)

/** 폰 앱에서 보는 차량별 자세 목록. */
data class SeatPositionResponse(
    val vehicleId: UUID,
    val vehicleNickname: String?,
    val vehicleModel: String,
    val position: SeatPositionDto,
)

fun SeatPosition.toDto() = SeatPositionDto(memorySlot, backrestPosition, foreAftPosition, updatedAt)

@Service
@Transactional(readOnly = true)
class SeatPositionService(
    private val repository: SeatPositionRepository,
    private val profiles: ProfileRepository,
    private val vehicles: VehicleService,
    private val vehicleRepository: VehicleRepository,
) {

    fun list(ownerId: UUID, profileId: UUID): List<SeatPositionResponse> {
        ownProfile(ownerId, profileId)
        val positions = repository.findAllByProfileIdOrderByCreatedAtAsc(profileId)
        val byId = vehicleRepository.findAllById(positions.map { it.vehicleId }).associateBy { it.requireId() }
        return positions.mapNotNull { p ->
            byId[p.vehicleId]?.let { SeatPositionResponse(p.vehicleId, it.nickname, it.model, p.toDto()) }
        }
    }

    /** 폰 앱: 내가 멤버인 차량에 대해 내 프로필의 자세(주로 메모리 슬롯)를 정한다. */
    @Transactional
    fun saveFromPhone(ownerId: UUID, profileId: UUID, vehicleId: UUID, request: SeatPositionRequest): SeatPositionDto {
        ownProfile(ownerId, profileId)
        vehicles.requireMember(ownerId, vehicleId)
        return upsert(profileId, vehicleId, request)
    }

    @Transactional
    fun deleteFromPhone(ownerId: UUID, profileId: UUID, vehicleId: UUID) {
        ownProfile(ownerId, profileId)
        repository.findByProfileIdAndVehicleId(profileId, vehicleId)?.let(repository::delete)
    }

    /** 차량 앱: "지금 자세 저장". 이 차량 멤버의 프로필만 허용한다. */
    @Transactional
    fun saveFromVehicle(vehicleId: UUID, profileId: UUID, request: SeatPositionRequest): SeatPositionDto {
        val profile = profiles.findById(profileId).orElse(null)
        if (profile == null || profile.ownerId !in vehicles.memberUserIds(vehicleId)) {
            throw notFound("프로필을 찾을 수 없습니다: $profileId")
        }
        return upsert(profileId, vehicleId, request)
    }

    /** 차량에서 나간 사람의 이 차량 시트 자세를 지운다. */
    @EventListener
    fun onMemberLeft(event: MemberLeftEvent) {
        repository.deleteAllForOwnerOnVehicle(event.userId, event.vehicleId)
    }

    fun find(profileId: UUID, vehicleId: UUID): SeatPosition? = repository.findByProfileIdAndVehicleId(profileId, vehicleId)

    fun findForVehicle(vehicleId: UUID, profileIds: Collection<UUID>): Map<UUID, SeatPosition> =
        repository.findAllByVehicleIdAndProfileIdIn(vehicleId, profileIds).associateBy { it.profileId }

    private fun upsert(profileId: UUID, vehicleId: UUID, request: SeatPositionRequest): SeatPositionDto {
        if (request.memorySlot == null && request.backrestPosition == null && request.foreAftPosition == null) {
            throw ApiException(HttpStatus.BAD_REQUEST, "메모리 슬롯이나 시트 위치 중 하나 이상이 필요합니다. 지우려면 DELETE를 쓰세요")
        }
        val position = repository.findByProfileIdAndVehicleId(profileId, vehicleId)
            ?: SeatPosition(profileId = profileId, vehicleId = vehicleId)
        position.replaceWith(request)
        return repository.saveAndFlush(position).toDto()
    }

    private fun ownProfile(ownerId: UUID, profileId: UUID): Profile =
        profiles.findByIdAndOwnerId(profileId, ownerId) ?: throw notFound("프로필을 찾을 수 없습니다: $profileId")
}

@Tag(name = "프로필")
@RestController
@RequestMapping("/api/v1/profiles/{profileId}/seat-positions")
class SeatPositionController(private val service: SeatPositionService) {

    @Operation(summary = "차량별 시트 자세 목록")
    @GetMapping
    fun list(user: AuthUser, @PathVariable profileId: UUID): List<SeatPositionResponse> = service.list(user.id, profileId)

    @Operation(
        summary = "차량별 시트 자세 저장",
        description = "전체 교체. 폰에서는 보통 메모리 슬롯만 고른다. 위치값은 차량 앱의 '지금 자세 저장'이 채운다.",
    )
    @PutMapping("/{vehicleId}")
    fun save(
        user: AuthUser,
        @PathVariable profileId: UUID,
        @PathVariable vehicleId: UUID,
        @Valid @RequestBody request: SeatPositionRequest,
    ): SeatPositionDto = service.saveFromPhone(user.id, profileId, vehicleId, request)

    @Operation(summary = "차량별 시트 자세 지우기", description = "이 차량에서는 시트를 움직이지 않는다.")
    @DeleteMapping("/{vehicleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(user: AuthUser, @PathVariable profileId: UUID, @PathVariable vehicleId: UUID) =
        service.deleteFromPhone(user.id, profileId, vehicleId)
}

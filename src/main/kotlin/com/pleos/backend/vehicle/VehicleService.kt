package com.pleos.backend.vehicle

import com.pleos.backend.common.ApiException
import com.pleos.backend.common.Tokens
import com.pleos.backend.common.conflict
import com.pleos.backend.common.forbidden
import com.pleos.backend.common.notFound
import com.pleos.backend.user.UserRepository
import jakarta.persistence.LockModeType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface VehicleRepository : JpaRepository<Vehicle, UUID> {
    fun findByTokenHash(tokenHash: String): Vehicle?
    fun existsByPairingCode(pairingCode: String): Boolean

    /** 페어링·멤버 변경은 차량 행을 잠가 순서대로 처리한다(동시 첫 페어링에 OWNER가 둘 생기는 것 방지). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.pairingCode = :pairingCode")
    fun findByPairingCodeForUpdate(pairingCode: String): Vehicle?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id")
    fun findByIdForUpdate(id: UUID): Vehicle?
}

interface VehicleMemberRepository : JpaRepository<VehicleMember, UUID> {
    fun findByVehicleIdAndUserId(vehicleId: UUID, userId: UUID): VehicleMember?
    fun findAllByUserIdOrderByCreatedAtAsc(userId: UUID): List<VehicleMember>
    fun findAllByVehicleIdOrderByCreatedAtAsc(vehicleId: UUID): List<VehicleMember>
    fun existsByVehicleId(vehicleId: UUID): Boolean
    fun countByVehicleId(vehicleId: UUID): Int
}

@Service
@Transactional(readOnly = true)
class VehicleService(
    private val vehicles: VehicleRepository,
    private val members: VehicleMemberRepository,
    private val users: UserRepository,
    private val events: ApplicationEventPublisher,
) {

    @Transactional
    fun register(request: VehicleRegisterRequest): VehicleRegisterResponse {
        val token = Tokens.generate()
        val vehicle = vehicles.save(
            Vehicle(model = request.model.trim(), tokenHash = Tokens.hash(token), pairingCode = newPairingCode()),
        )
        return VehicleRegisterResponse(vehicle.requireId(), token, vehicle.pairingCode)
    }

    /** 첫 페어링한 사람이 OWNER, 이후는 MEMBER. 이미 멤버면 기존 정보를 그대로 돌려준다(여러 번 눌러도 안전). */
    @Transactional
    fun pair(userId: UUID, request: PairRequest): MyVehicleResponse {
        val vehicle = vehicles.findByPairingCodeForUpdate(request.pairingCode)
            ?: throw notFound("페어링 코드가 올바르지 않습니다")
        val vehicleId = vehicle.requireId()
        val member = members.findByVehicleIdAndUserId(vehicleId, userId) ?: run {
            val role = if (members.existsByVehicleId(vehicleId)) VehicleRole.MEMBER else VehicleRole.OWNER
            if (role == VehicleRole.OWNER) vehicle.nickname = request.nickname?.trim()?.ifEmpty { null }
            members.saveAndFlush(VehicleMember(vehicleId = vehicleId, userId = userId, role = role))
        }
        return toMyVehicle(vehicle, member)
    }

    fun myVehicles(userId: UUID): List<MyVehicleResponse> {
        val memberships = members.findAllByUserIdOrderByCreatedAtAsc(userId)
        val byId = vehicles.findAllById(memberships.map { it.vehicleId }).associateBy { it.requireId() }
        return memberships.mapNotNull { m -> byId[m.vehicleId]?.let { toMyVehicle(it, m) } }
    }

    fun detail(userId: UUID, vehicleId: UUID): VehicleDetailResponse {
        val me = requireMember(userId, vehicleId)
        val vehicle = load(vehicleId)
        val all = members.findAllByVehicleIdOrderByCreatedAtAsc(vehicleId)
        val nicknames = users.findAllById(all.map { it.userId }).associate { it.requireId() to it.nickname }
        return VehicleDetailResponse(
            id = vehicleId,
            model = vehicle.model,
            nickname = vehicle.nickname,
            myRole = me.role,
            members = all.map { VehicleMemberResponse(it.userId, nicknames[it.userId].orEmpty(), it.role, it.createdAt) },
        )
    }

    // ── OWNER 관리 기능 ──

    @Transactional
    fun rename(userId: UUID, vehicleId: UUID, request: VehicleUpdateRequest): VehicleDetailResponse {
        val vehicle = lockAsOwner(userId, vehicleId)
        vehicle.nickname = request.nickname?.trim()?.ifEmpty { null }
        return detail(userId, vehicleId)
    }

    /** 코드가 퍼졌을 때. 이전 코드는 바로 쓸 수 없게 되고, 차량 앱 화면(GET /me)에 새 코드가 뜬다. */
    @Transactional
    fun regeneratePairingCode(userId: UUID, vehicleId: UUID): PairingCodeResponse {
        val vehicle = lockAsOwner(userId, vehicleId)
        vehicle.pairingCode = newPairingCode()
        return PairingCodeResponse(vehicle.pairingCode)
    }

    @Transactional
    fun transferOwner(userId: UUID, vehicleId: UUID, request: TransferOwnerRequest): VehicleDetailResponse {
        lockAsOwner(userId, vehicleId)
        if (request.userId == userId) throw ApiException(HttpStatus.BAD_REQUEST, "이미 OWNER입니다")
        val target = members.findByVehicleIdAndUserId(vehicleId, request.userId)
            ?: throw notFound("이 차량의 멤버가 아닙니다: ${request.userId}")
        requireMember(userId, vehicleId).role = VehicleRole.MEMBER
        target.role = VehicleRole.OWNER
        return detail(userId, vehicleId)
    }

    /** 다른 멤버가 남아 있으면 OWNER는 먼저 넘겨야 한다. 혼자 남은 OWNER는 나갈 수 있고, 다음에 페어링한 사람이 OWNER가 된다. */
    @Transactional
    fun leave(userId: UUID, vehicleId: UUID) {
        vehicles.findByIdForUpdate(vehicleId) ?: throw notFound("차량을 찾을 수 없습니다: $vehicleId")
        val me = requireMember(userId, vehicleId)
        if (me.role == VehicleRole.OWNER && members.countByVehicleId(vehicleId) > 1) {
            throw conflict("다른 멤버에게 OWNER를 넘긴 뒤 나갈 수 있습니다")
        }
        removeMembership(me)
    }

    @Transactional
    fun removeMember(userId: UUID, vehicleId: UUID, targetUserId: UUID) {
        lockAsOwner(userId, vehicleId)
        if (targetUserId == userId) throw ApiException(HttpStatus.BAD_REQUEST, "자신은 내보낼 수 없습니다. 나가기를 쓰세요")
        val target = members.findByVehicleIdAndUserId(vehicleId, targetUserId)
            ?: throw notFound("이 차량의 멤버가 아닙니다: $targetUserId")
        removeMembership(target)
    }

    // ── 차량 앱·다른 도메인용 ──

    fun self(vehicleId: UUID): VehicleSelfResponse {
        val vehicle = load(vehicleId)
        return VehicleSelfResponse(vehicleId, vehicle.model, vehicle.nickname, vehicle.pairingCode, members.countByVehicleId(vehicleId))
    }

    fun memberUserIds(vehicleId: UUID): List<UUID> =
        members.findAllByVehicleIdOrderByCreatedAtAsc(vehicleId).map { it.userId }

    /** 멤버가 아니면 차량이 없는 것처럼 404. */
    fun requireMember(userId: UUID, vehicleId: UUID): VehicleMember =
        members.findByVehicleIdAndUserId(vehicleId, userId) ?: throw notFound("차량을 찾을 수 없습니다: $vehicleId")

    private fun removeMembership(member: VehicleMember) {
        members.delete(member)
        events.publishEvent(MemberLeftEvent(member.vehicleId, member.userId))
    }

    /** 멤버가 아니면 404, MEMBER면 403. */
    private fun lockAsOwner(userId: UUID, vehicleId: UUID): Vehicle {
        val vehicle = vehicles.findByIdForUpdate(vehicleId) ?: throw notFound("차량을 찾을 수 없습니다: $vehicleId")
        if (requireMember(userId, vehicleId).role != VehicleRole.OWNER) throw forbidden("차량 OWNER만 할 수 있습니다")
        return vehicle
    }

    private fun load(vehicleId: UUID): Vehicle =
        vehicles.findById(vehicleId).orElseThrow { notFound("차량을 찾을 수 없습니다: $vehicleId") }

    private fun toMyVehicle(vehicle: Vehicle, member: VehicleMember) =
        MyVehicleResponse(vehicle.requireId(), vehicle.model, vehicle.nickname, member.role, member.createdAt)

    private fun newPairingCode(): String =
        generateSequence { Tokens.pairingCode() }.first { !vehicles.existsByPairingCode(it) }
}

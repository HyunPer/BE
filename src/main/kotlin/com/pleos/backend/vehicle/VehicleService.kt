package com.pleos.backend.vehicle

import com.pleos.backend.common.Tokens
import com.pleos.backend.common.notFound
import com.pleos.backend.user.UserRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface VehicleRepository : JpaRepository<Vehicle, UUID> {
    fun findByTokenHash(tokenHash: String): Vehicle?
    fun findByPairingCode(pairingCode: String): Vehicle?
    fun existsByPairingCode(pairingCode: String): Boolean
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
) {

    @Transactional
    fun register(request: VehicleRegisterRequest): VehicleRegisterResponse {
        val token = Tokens.generate()
        val vehicle = vehicles.save(
            Vehicle(model = request.model.trim(), tokenHash = Tokens.hash(token), pairingCode = newPairingCode()),
        )
        return VehicleRegisterResponse(vehicle.requireId(), token, vehicle.pairingCode)
    }

    /** 첫 페어링한 사람이 OWNER, 이후는 MEMBER. 이미 멤버면 기존 정보를 그대로 돌려준다. */
    @Transactional
    fun pair(userId: UUID, request: PairRequest): MyVehicleResponse {
        val vehicle = vehicles.findByPairingCode(request.pairingCode)
            ?: throw notFound("페어링 코드가 올바르지 않습니다")
        val vehicleId = vehicle.requireId()
        val member = members.findByVehicleIdAndUserId(vehicleId, userId) ?: run {
            val role = if (members.existsByVehicleId(vehicleId)) VehicleRole.MEMBER else VehicleRole.OWNER
            if (role == VehicleRole.OWNER) vehicle.nickname = request.nickname?.trim()?.ifEmpty { null }
            members.save(VehicleMember(vehicleId = vehicleId, userId = userId, role = role))
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

    fun self(vehicleId: UUID): VehicleSelfResponse {
        val vehicle = load(vehicleId)
        return VehicleSelfResponse(vehicleId, vehicle.model, vehicle.nickname, vehicle.pairingCode, members.countByVehicleId(vehicleId))
    }

    fun memberUserIds(vehicleId: UUID): List<UUID> =
        members.findAllByVehicleIdOrderByCreatedAtAsc(vehicleId).map { it.userId }

    /** 멤버가 아니면 차량이 없는 것처럼 404. */
    fun requireMember(userId: UUID, vehicleId: UUID): VehicleMember =
        members.findByVehicleIdAndUserId(vehicleId, userId) ?: throw notFound("차량을 찾을 수 없습니다: $vehicleId")

    private fun load(vehicleId: UUID): Vehicle =
        vehicles.findById(vehicleId).orElseThrow { notFound("차량을 찾을 수 없습니다: $vehicleId") }

    private fun toMyVehicle(vehicle: Vehicle, member: VehicleMember) =
        MyVehicleResponse(vehicle.requireId(), vehicle.model, vehicle.nickname, member.role, member.createdAt)

    private fun newPairingCode(): String =
        generateSequence { Tokens.pairingCode() }.first { !vehicles.existsByPairingCode(it) }
}

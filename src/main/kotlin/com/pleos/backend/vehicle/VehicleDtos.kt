package com.pleos.backend.vehicle

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** 차량 앱 → 서버. 첫 실행 때 한 번. */
data class VehicleRegisterRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val model: String,
)

/** vehicleToken은 이 응답에서만 받을 수 있다. 차량 앱이 저장해 두고 Authorization: Bearer 헤더로 보낸다. */
data class VehicleRegisterResponse(
    val vehicleId: UUID,
    val vehicleToken: String,
    val pairingCode: String,
)

/** 폰 앱 → 서버. 차량 화면의 6자리 코드를 입력한다. */
data class PairRequest(
    @field:Pattern(regexp = "^\\d{6}$")
    val pairingCode: String,

    /** 첫 페어링(OWNER)일 때만 차량 이름으로 저장된다. */
    @field:Size(max = 20)
    val nickname: String? = null,
)

/** 폰 앱에서 보는 내 차량. */
data class MyVehicleResponse(
    val id: UUID,
    val model: String,
    val nickname: String?,
    val myRole: VehicleRole,
    val joinedAt: Instant,
)

data class VehicleMemberResponse(
    val userId: UUID,
    val nickname: String,
    val role: VehicleRole,
    val joinedAt: Instant,
)

data class VehicleDetailResponse(
    val id: UUID,
    val model: String,
    val nickname: String?,
    val myRole: VehicleRole,
    val members: List<VehicleMemberResponse>,
)

/** 차량 앱에서 보는 자기 자신. 페어링 코드를 화면에 다시 띄울 때 쓴다. */
data class VehicleSelfResponse(
    val id: UUID,
    val model: String,
    val nickname: String?,
    val pairingCode: String,
    val memberCount: Int,
)

/** OWNER만. 차량 이름 변경. 빈 문자열이면 이름을 지운다. */
data class VehicleUpdateRequest(
    @field:Size(max = 20)
    val nickname: String? = null,
)

data class PairingCodeResponse(val pairingCode: String)

data class TransferOwnerRequest(val userId: UUID)

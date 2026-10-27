package com.pleos.backend.vehicle

import com.pleos.backend.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "차량")
@RestController
@RequestMapping("/api/v1/vehicles")
class VehicleController(private val service: VehicleService) {

    @Operation(summary = "차량 페어링", description = "차량 화면의 6자리 코드를 입력한다. 첫 사람은 OWNER, 이후는 MEMBER.")
    @PostMapping("/pair")
    fun pair(user: AuthUser, @Valid @RequestBody request: PairRequest): MyVehicleResponse = service.pair(user.id, request)

    @Operation(summary = "내 차량 목록")
    @GetMapping
    fun list(user: AuthUser): List<MyVehicleResponse> = service.myVehicles(user.id)

    @Operation(summary = "차량 상세", description = "함께 쓰는 멤버 목록을 포함한다.")
    @GetMapping("/{vehicleId}")
    fun detail(user: AuthUser, @PathVariable vehicleId: UUID): VehicleDetailResponse = service.detail(user.id, vehicleId)

    @Operation(summary = "차량 이름 변경", description = "OWNER만(MEMBER는 403).")
    @PatchMapping("/{vehicleId}")
    fun rename(user: AuthUser, @PathVariable vehicleId: UUID, @Valid @RequestBody request: VehicleUpdateRequest): VehicleDetailResponse =
        service.rename(user.id, vehicleId, request)

    @Operation(summary = "페어링 코드 재발급", description = "OWNER만. 이전 코드는 바로 무효. 차량 화면(GET /vehicle-api/v1/me)에 새 코드가 뜬다.")
    @PostMapping("/{vehicleId}/pairing-code")
    fun regeneratePairingCode(user: AuthUser, @PathVariable vehicleId: UUID): PairingCodeResponse =
        service.regeneratePairingCode(user.id, vehicleId)

    @Operation(summary = "OWNER 넘기기", description = "OWNER만. 대상은 이 차량의 멤버여야 한다. 나는 MEMBER가 된다.")
    @PostMapping("/{vehicleId}/owner")
    fun transferOwner(user: AuthUser, @PathVariable vehicleId: UUID, @RequestBody request: TransferOwnerRequest): VehicleDetailResponse =
        service.transferOwner(user.id, vehicleId, request)

    @Operation(
        summary = "차량에서 나가기",
        description = "내 대기 예약은 취소되고, 이 차량에 저장한 내 시트 자세는 지워진다. 다른 멤버가 있으면 OWNER는 먼저 넘겨야 한다(409).",
    )
    @DeleteMapping("/{vehicleId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave(user: AuthUser, @PathVariable vehicleId: UUID) = service.leave(user.id, vehicleId)

    @Operation(summary = "멤버 내보내기", description = "OWNER만. 그 멤버의 대기 예약 취소, 이 차량 시트 자세 삭제.")
    @DeleteMapping("/{vehicleId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(user: AuthUser, @PathVariable vehicleId: UUID, @PathVariable userId: UUID) =
        service.removeMember(user.id, vehicleId, userId)
}

package com.pleos.backend.vehicle

import com.pleos.backend.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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
}

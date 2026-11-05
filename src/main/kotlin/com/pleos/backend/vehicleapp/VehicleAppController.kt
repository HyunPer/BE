package com.pleos.backend.vehicleapp

import com.pleos.backend.auth.AuthVehicle
import com.pleos.backend.vehicle.VehicleRegisterRequest
import com.pleos.backend.vehicle.VehicleRegisterResponse
import com.pleos.backend.vehicle.VehicleSelfResponse
import com.pleos.backend.vehicle.VehicleService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 차량 앱(Pleos Connect) 전용 API. 등록을 뺀 모든 요청은 차량 토큰이 필요하다.
 * 흐름: 첫 실행 → 등록·연결 코드 표시 → 가족이 폰에서 페어링.
 * 쉼표의 트립·체크인 API는 trip 패키지에 둔다.
 */
@Tag(name = "차량 앱")
@RestController
@RequestMapping("/vehicle-api/v1")
class VehicleAppController(private val vehicles: VehicleService) {

    @Operation(summary = "차량 등록", description = "차량 앱 첫 실행 때 한 번. vehicleToken을 저장하고 pairingCode를 화면에 띄운다.")
    @SecurityRequirements
    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: VehicleRegisterRequest): VehicleRegisterResponse = vehicles.register(request)

    @Operation(summary = "내 차량 정보", description = "연결 코드·멤버 수를 다시 보여줄 때.")
    @GetMapping("/me")
    fun me(vehicle: AuthVehicle): VehicleSelfResponse = vehicles.self(vehicle.id)
}

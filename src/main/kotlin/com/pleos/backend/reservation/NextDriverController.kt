package com.pleos.backend.reservation

import com.pleos.backend.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "다음 운전자 예약")
@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/next-driver")
class NextDriverController(private val service: ReservationService) {

    @Operation(summary = "다음 운전자 예약", description = "내 프로필 하나를 골라 예약한다. 기존 대기 예약은 취소된다.")
    @PutMapping
    fun reserve(user: AuthUser, @PathVariable vehicleId: UUID, @RequestBody request: ReserveRequest): ReservationResponse =
        service.reserve(user.id, vehicleId, request)

    @Operation(summary = "현재 대기 예약 조회", description = "없으면 204.")
    @GetMapping
    fun current(user: AuthUser, @PathVariable vehicleId: UUID): ResponseEntity<ReservationResponse> =
        service.current(user.id, vehicleId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @Operation(summary = "대기 예약 취소")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(user: AuthUser, @PathVariable vehicleId: UUID) = service.cancel(user.id, vehicleId)
}

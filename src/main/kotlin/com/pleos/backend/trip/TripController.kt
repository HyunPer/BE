package com.pleos.backend.trip

import com.pleos.backend.auth.AuthUser
import com.pleos.backend.auth.AuthVehicle
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 쉼표 · 차량 앱: 탑승하면 트립 시작, 도착하면 주행 요약과 함께 끝낸다. */
@Tag(name = "쉼표 · 트립(차량 앱)")
@RestController
@RequestMapping("/vehicle-api/v1")
class TripVehicleController(private val service: TripService) {

    @Operation(summary = "트립 시작", description = "착석+P단 등으로 탑승을 감지했을 때. 끝내지 못한 이전 트립은 닫힌다.")
    @PostMapping("/trips")
    @ResponseStatus(HttpStatus.CREATED)
    fun start(vehicle: AuthVehicle): TripResponse = service.start(vehicle.id)

    @Operation(summary = "트립 종료", description = "도착 때 주행 요약(주행·정체 시간, 급커브 수, 최고 멀미 위험도)을 보낸다. 재시도해도 같은 응답.")
    @PostMapping("/trips/{tripId}/finish")
    fun finish(
        vehicle: AuthVehicle,
        @PathVariable tripId: UUID,
        @Valid @RequestBody summary: TripSummaryRequest,
    ): TripResponse = service.finish(vehicle.id, tripId, summary)

    @Operation(summary = "가족 컨디션", description = "멀미 위험도 배율과 쉬는 방식을 정할 때.")
    @GetMapping("/me/riders")
    fun riders(vehicle: AuthVehicle): List<RiderResponse> = service.riders(vehicle.id)
}

/** 쉼표 · 폰 앱: 트립 기록, 도착 체크인, 내 컨디션. */
@Tag(name = "쉼표 · 트립(폰 앱)")
@RestController
@RequestMapping("/api/v1")
class TripPhoneController(private val service: TripService) {

    @Operation(summary = "최근 트립", description = "최신순, 내 체크인 포함. 멤버만(아니면 404). limit 1–50, 기본 20.")
    @GetMapping("/vehicles/{vehicleId}/trips")
    fun recent(
        user: AuthUser,
        @PathVariable vehicleId: UUID,
        @RequestParam(defaultValue = "20") limit: Int,
    ): List<TripResponse> = service.recent(user.id, vehicleId, limit)

    @Operation(summary = "도착 체크인", description = "기분 하나와 한 줄 메모(200자). 다시 남기면 덮어쓴다.")
    @PutMapping("/trips/{tripId}/check-in")
    fun checkIn(
        user: AuthUser,
        @PathVariable tripId: UUID,
        @Valid @RequestBody request: CheckInRequest,
    ): TripResponse = service.checkIn(user.id, tripId, request)

    @Operation(summary = "내 컨디션")
    @GetMapping("/users/me/condition")
    fun myCondition(user: AuthUser): RiderConditionDto = service.myCondition(user.id)

    @Operation(summary = "내 컨디션 저장", description = "멀미를 얼마나 타는지, 어떻게 쉬는 걸 좋아하는지.")
    @PutMapping("/users/me/condition")
    fun updateMyCondition(user: AuthUser, @RequestBody request: RiderConditionDto): RiderConditionDto =
        service.updateMyCondition(user.id, request)
}
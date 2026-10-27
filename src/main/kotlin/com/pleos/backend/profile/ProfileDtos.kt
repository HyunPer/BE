package com.pleos.backend.profile

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

// 값 범위는 임시 기준이다. Vehicle SDK 검증 후 차종 프로파일에 맞춰 조정한다.

/** 생성(POST)·전체 수정(PUT) 공통 요청. 빠진 설정 묶음은 기본값으로 채운다. */
data class ProfileRequest(
    @field:NotBlank
    @field:Size(max = 20)
    val name: String,

    @field:Valid
    val display: DisplayDto = DisplayDto(),

    @field:Valid
    val seat: SeatDto = SeatDto(),

    @field:Valid
    val climate: ClimateDto = ClimateDto(),

    @field:Valid
    val light: LightDto = LightDto(),

    @field:Size(max = 100)
    val welcomeSoundId: String? = null,

    /** 내 테마 ID(/api/v1/themes). 없으면 기본 테마. */
    val themeId: UUID? = null,
)

data class ProfileResponse(
    val id: UUID,
    val name: String,
    val display: DisplayDto,
    val seat: SeatDto,
    val climate: ClimateDto,
    val light: LightDto,
    val welcomeSoundId: String?,
    val themeId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class DisplayDto(
    @field:Size(max = 12)
    val widgets: List<@NotBlank @Size(max = 50) String> = emptyList(),
)

/** 시트 열선·통풍 단계와 핸들 열선. 시트 위치는 차량별 seat-positions API로 따로 저장한다. */
data class SeatDto(
    @field:Min(0) @field:Max(3)
    val heatingLevel: Int = 0,

    @field:Min(0) @field:Max(3)
    val ventilationLevel: Int = 0,

    val steeringWheelHeating: Boolean = false,
)

data class ClimateDto(
    @field:DecimalMin("16.0") @field:DecimalMax("32.0")
    val temperature: Double = 22.0,

    @field:Min(0) @field:Max(8)
    val fanSpeed: Int = 3,
)

/** 앰비언트 밝기. 색은 테마의 ambientColor. */
data class LightDto(
    @field:Min(0) @field:Max(100)
    val brightness: Int = 50,
)

fun Profile.toResponse() = ProfileResponse(
    id = requireId(),
    name = name,
    display = DisplayDto(display.widgets.toList()),
    seat = seat.toDto(),
    climate = ClimateDto(climate.temperature, climate.fanSpeed),
    light = LightDto(light.brightness),
    welcomeSoundId = welcomeSoundId,
    themeId = themeId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ── 차량 앱 적용 단계 ──
// 1단계 차량 설정(시트·공조·열선)을 먼저 적용하고 보고하면, 2단계 웰컴 묶음(theme.WelcomeResponse)을 받는다.

/**
 * 1단계: Vehicle SDK로 바로 적용하는 차량 설정. 테마·조명은 들어 있지 않다.
 * seatPosition은 이 차량에 저장된 자세가 없으면 null이고, 그때 차량 앱은 시트를 움직이지 않는다.
 */
data class VehicleSettingsDto(
    val seat: SeatDto,
    val climate: ClimateDto,
    val seatPosition: SeatPositionDto?,
)

/** 차량 앱의 프로필 선택 목록에 쓰는 요약. 테마는 적용 보고 후에 받는다. */
data class ApplicableProfileDto(
    val profileId: UUID,
    val name: String,
    val vehicleSettings: VehicleSettingsDto,
)

fun SeatSettings.toDto() = SeatDto(heatingLevel, ventilationLevel, steeringWheelHeating)

fun Profile.toVehicleSettings(seatPosition: SeatPosition?) = VehicleSettingsDto(
    seat = seat.toDto(),
    climate = ClimateDto(climate.temperature, climate.fanSpeed),
    seatPosition = seatPosition?.toDto(),
)

fun Profile.toApplicable(seatPosition: SeatPosition?) = ApplicableProfileDto(requireId(), name, toVehicleSettings(seatPosition))

fun ProfileRequest.toEntity(ownerId: UUID) = Profile(ownerId = ownerId, name = name.trim()).also { it.applyFrom(this) }

fun Profile.applyFrom(request: ProfileRequest) {
    name = request.name.trim()
    display.widgets.clear()
    display.widgets.addAll(request.display.widgets)
    seat.heatingLevel = request.seat.heatingLevel
    seat.ventilationLevel = request.seat.ventilationLevel
    seat.steeringWheelHeating = request.seat.steeringWheelHeating
    climate.temperature = request.climate.temperature
    climate.fanSpeed = request.climate.fanSpeed
    light.brightness = request.light.brightness
    welcomeSoundId = request.welcomeSoundId
    themeId = request.themeId
}

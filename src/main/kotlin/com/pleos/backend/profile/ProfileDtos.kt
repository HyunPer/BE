package com.pleos.backend.profile

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

private const val HEX_COLOR = "^#[0-9A-Fa-f]{6}$"

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
)

data class ProfileResponse(
    val id: UUID,
    val name: String,
    val display: DisplayDto,
    val seat: SeatDto,
    val climate: ClimateDto,
    val light: LightDto,
    val welcomeSoundId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class DisplayDto(
    @field:Pattern(regexp = HEX_COLOR)
    val themeColor: String = "#1E88E5",

    @field:Size(max = 100)
    val wallpaperId: String? = null,

    @field:Size(max = 12)
    val widgets: List<@NotBlank @Size(max = 50) String> = emptyList(),
)

data class SeatDto(
    @field:Min(80) @field:Max(150)
    val reclineAngle: Int = 100,

    @field:Min(0) @field:Max(100)
    val slidePosition: Int = 50,

    @field:Min(0) @field:Max(3)
    val heatingLevel: Int = 0,

    val steeringWheelHeating: Boolean = false,
)

data class ClimateDto(
    @field:DecimalMin("16.0") @field:DecimalMax("32.0")
    val temperature: Double = 22.0,

    @field:Min(0) @field:Max(8)
    val fanSpeed: Int = 3,
)

data class LightDto(
    @field:Pattern(regexp = HEX_COLOR)
    val color: String = "#FFFFFF",

    @field:Min(0) @field:Max(100)
    val brightness: Int = 50,
)

fun Profile.toResponse() = ProfileResponse(
    id = requireId(),
    name = name,
    display = DisplayDto(display.themeColor, display.wallpaperId, display.widgets.toList()),
    seat = SeatDto(seat.reclineAngle, seat.slidePosition, seat.heatingLevel, seat.steeringWheelHeating),
    climate = ClimateDto(climate.temperature, climate.fanSpeed),
    light = LightDto(light.color, light.brightness),
    welcomeSoundId = welcomeSoundId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ProfileRequest.toEntity(ownerId: UUID) = Profile(ownerId = ownerId, name = name.trim()).also { it.applyFrom(this) }

fun Profile.applyFrom(request: ProfileRequest) {
    name = request.name.trim()
    display.themeColor = request.display.themeColor.uppercase()
    display.wallpaperId = request.display.wallpaperId
    display.widgets.clear()
    display.widgets.addAll(request.display.widgets)
    seat.reclineAngle = request.seat.reclineAngle
    seat.slidePosition = request.seat.slidePosition
    seat.heatingLevel = request.seat.heatingLevel
    seat.steeringWheelHeating = request.seat.steeringWheelHeating
    climate.temperature = request.climate.temperature
    climate.fanSpeed = request.climate.fanSpeed
    light.color = request.light.color.uppercase()
    light.brightness = request.light.brightness
    welcomeSoundId = request.welcomeSoundId
}

package com.pleos.backend.profile

import com.pleos.backend.common.BaseEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import java.util.UUID

/**
 * 한 사람의 차량 커스텀 설정 묶음. 한 사용자가 여러 개를 만들 수 있다(예: 평일 출근 / 주말 캠핑).
 * 폰 앱에서 편집하고, 차량 앱이 시동 시 받아서 Vehicle SDK로 적용한다.
 */
@Entity
@Table(name = "profiles", indexes = [Index(columnList = "owner_id")])
class Profile(
    @Column(name = "owner_id", nullable = false, updatable = false)
    val ownerId: UUID,

    @Column(nullable = false, length = 20)
    var name: String,

    @Embedded
    var display: DisplaySettings = DisplaySettings(),

    @Embedded
    var seat: SeatSettings = SeatSettings(),

    @Embedded
    var climate: ClimateSettings = ClimateSettings(),

    @Embedded
    var light: LightSettings = LightSettings(),

    @Column(length = 100)
    var welcomeSoundId: String? = null,

    /** 차량 앱 화면 테마. 없으면 기본 테마. 색·배경·캐릭터·버튼 모양과 앰비언트 색은 테마가 정한다. */
    @Column(name = "theme_id")
    var themeId: UUID? = null,
) : BaseEntity()

/** 차량 앱 홈에 띄울 위젯과 순서. 모양은 테마(widgetStyle)가 정한다. */
@Embeddable
class DisplaySettings(
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_widgets", joinColumns = [JoinColumn(name = "profile_id")])
    @OrderColumn(name = "position")
    @Column(name = "widget", nullable = false, length = 50)
    var widgets: MutableList<String> = mutableListOf(),
)

/**
 * 차종과 무관한 시트 설정: 열선·통풍 단계, 핸들 열선 (Vehicle SDK HVAC 도메인).
 * 시트 위치는 차량마다 범위·단위가 달라 프로필×차량별 SeatPosition에 따로 둔다.
 */
@Embeddable
class SeatSettings(
    @Column(name = "seat_heating_level", nullable = false)
    var heatingLevel: Int = 0,

    @Column(name = "seat_ventilation_level", nullable = false)
    var ventilationLevel: Int = 0,

    @Column(name = "steering_wheel_heating", nullable = false)
    var steeringWheelHeating: Boolean = false,
)

/** Vehicle SDK HVAC. */
@Embeddable
class ClimateSettings(
    @Column(name = "climate_temperature", nullable = false)
    var temperature: Double = 22.0,

    @Column(name = "climate_fan_speed", nullable = false)
    var fanSpeed: Int = 3,
)

/** Vehicle SDK Light. 색은 테마(ambientColor)를 따르고, 밝기만 사람마다 다르게 둔다. */
@Embeddable
class LightSettings(
    @Column(name = "light_brightness", nullable = false)
    var brightness: Int = 50,
)

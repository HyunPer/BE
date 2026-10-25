package com.pleos.backend.profile

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * 한 사람의 차량 커스텀 설정 묶음.
 * 폰 앱에서 편집하고, 차량 앱이 시동 시 받아서 Vehicle SDK로 적용한다.
 */
@Entity
@Table(name = "profiles")
class Profile(
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
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.EPOCH
        protected set

    @Column(nullable = false)
    var updatedAt: Instant = Instant.EPOCH
        protected set

    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}

/** 차량 앱 안의 커스텀 홈 화면. 시스템 전체 테마는 불확실하므로 앱 내 범위로 한정. */
@Embeddable
class DisplaySettings(
    @Column(name = "display_theme_color", nullable = false, length = 7)
    var themeColor: String = "#1E88E5",

    @Column(name = "display_wallpaper_id", length = 100)
    var wallpaperId: String? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_widgets", joinColumns = [JoinColumn(name = "profile_id")])
    @OrderColumn(name = "position")
    @Column(name = "widget", nullable = false, length = 50)
    var widgets: MutableList<String> = mutableListOf(),
)

/** Vehicle SDK Seat · Steeringwheel. 지원 범위는 차종별로 다르다. */
@Embeddable
class SeatSettings(
    @Column(name = "seat_recline_angle", nullable = false)
    var reclineAngle: Int = 100,

    @Column(name = "seat_slide_position", nullable = false)
    var slidePosition: Int = 50,

    @Column(name = "seat_heating_level", nullable = false)
    var heatingLevel: Int = 0,

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

/** Vehicle SDK Light. 제어 가능 여부 미확정 → 불가 시 앱 화면 컬러로 대체. */
@Embeddable
class LightSettings(
    @Column(name = "light_color", nullable = false, length = 7)
    var color: String = "#FFFFFF",

    @Column(name = "light_brightness", nullable = false)
    var brightness: Int = 50,
)

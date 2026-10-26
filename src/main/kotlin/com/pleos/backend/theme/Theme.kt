package com.pleos.backend.theme

import com.pleos.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.util.UUID

/**
 * 차량 앱 화면의 겉모습 묶음. 카카오톡 테마처럼 우리 앱 안의 색·배경·캐릭터·버튼·위젯 모양만 바꾼다
 * (차량 런처·계기판은 OS 영역이라 바꿀 수 없다). 프로필이 테마 하나를 가리키고, 공유 코드로 다른 사람에게 줄 수 있다.
 */
@Entity
@Table(name = "themes", indexes = [Index(columnList = "owner_id")])
class Theme(
    @Column(name = "owner_id", nullable = false, updatable = false)
    val ownerId: UUID,

    @Column(nullable = false, length = 20)
    var name: String,

    @Embedded
    var colors: ThemeColors = ThemeColors(),

    @Column(name = "background_asset_id")
    var backgroundAssetId: UUID? = null,

    @Column(name = "character_asset_id")
    var characterAssetId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var characterPlacement: CharacterPlacement = CharacterPlacement.BOTTOM_RIGHT,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var buttonStyle: ButtonStyle = ButtonStyle.ROUNDED,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var widgetStyle: WidgetStyle = WidgetStyle.FILLED,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var fontStyle: FontStyle = FontStyle.DEFAULT,

    /** 테마와 같이 바뀌는 실내 앰비언트 라이트 색. Vehicle SDK Light로 적용한다. */
    @Column(nullable = false, length = 7)
    var ambientColor: String = "#FFFFFF",

    /** 공유받아 복사한 테마라면 원본 테마 ID. 원작자 보호를 위해 다시 공유할 수 없다. */
    @Column(name = "source_theme_id", updatable = false)
    val sourceThemeId: UUID? = null,
) : BaseEntity() {

    /** 공유 중일 때만 값이 있다. 마켓에도 이 테마들만 보인다. */
    @Column(unique = true, length = 8)
    var shareCode: String? = null

    @Column(nullable = false)
    var importCount: Int = 0
        protected set

    fun countImport() {
        importCount += 1
    }
}

/** 차량 앱 화면의 색 토큰. 저장 시 글자 대비를 검사해 운전 중 가독성을 지킨다. */
@Embeddable
class ThemeColors(
    @Column(name = "color_background", nullable = false, length = 7)
    var background: String = "#121212",

    @Column(name = "color_surface", nullable = false, length = 7)
    var surface: String = "#1E1E1E",

    @Column(name = "color_primary", nullable = false, length = 7)
    var primary: String = "#1E88E5",

    @Column(name = "color_on_primary", nullable = false, length = 7)
    var onPrimary: String = "#FFFFFF",

    @Column(name = "color_text", nullable = false, length = 7)
    var text: String = "#FFFFFF",
)

// 아래 프리셋은 차량 앱이 화면을 그리는 규격이다. 값을 추가하면 차량 앱도 같이 대응해야 한다.

/** 버튼 모양. */
enum class ButtonStyle { ROUNDED, PILL, SQUARE, OUTLINED }

/** 위젯 카드 모양. GLASS는 배경 이미지 위 반투명 카드. */
enum class WidgetStyle { FILLED, OUTLINED, GLASS }

/** 앱에 내장한 글꼴 세트. */
enum class FontStyle { DEFAULT, ROUNDED, BOLD }

/** 캐릭터 이미지 위치. WELCOME_ONLY는 탑승 웰컴 화면에만 크게 띄운다. */
enum class CharacterPlacement { BOTTOM_RIGHT, BOTTOM_LEFT, TOP_RIGHT, WELCOME_ONLY }

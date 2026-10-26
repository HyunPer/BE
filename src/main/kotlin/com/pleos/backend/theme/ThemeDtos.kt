package com.pleos.backend.theme

import com.pleos.backend.asset.assetUrl
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

private const val HEX_COLOR = "^#[0-9A-Fa-f]{6}$"

data class ThemeColorsDto(
    @field:Pattern(regexp = HEX_COLOR) val background: String = "#121212",
    @field:Pattern(regexp = HEX_COLOR) val surface: String = "#1E1E1E",
    @field:Pattern(regexp = HEX_COLOR) val primary: String = "#1E88E5",
    @field:Pattern(regexp = HEX_COLOR) val onPrimary: String = "#FFFFFF",
    @field:Pattern(regexp = HEX_COLOR) val text: String = "#FFFFFF",
)

/** 생성(POST)·전체 수정(PUT) 공통. name만 필수, 나머지는 기본 테마 값. 이미지는 먼저 /api/v1/assets로 올린 ID를 넣는다. */
data class ThemeRequest(
    @field:NotBlank
    @field:Size(max = 20)
    val name: String,

    @field:Valid
    val colors: ThemeColorsDto = ThemeColorsDto(),

    val backgroundAssetId: UUID? = null,
    val characterAssetId: UUID? = null,
    val characterPlacement: CharacterPlacement = CharacterPlacement.BOTTOM_RIGHT,
    val buttonStyle: ButtonStyle = ButtonStyle.ROUNDED,
    val widgetStyle: WidgetStyle = WidgetStyle.FILLED,
    val fontStyle: FontStyle = FontStyle.DEFAULT,

    @field:Pattern(regexp = HEX_COLOR)
    val ambientColor: String = "#FFFFFF",
)

data class CharacterDto(val imageUrl: String, val placement: CharacterPlacement)

/**
 * 화면을 그리는 데 필요한 값만 담은 테마. 폰 앱 미리보기·마켓·차량 앱이 모두 이 모양을 그대로 쓴다.
 * 이미지 주소는 서버 기본 주소 뒤에 붙인다.
 */
data class ThemeSkinDto(
    val name: String,
    val colors: ThemeColorsDto,
    val backgroundImageUrl: String?,
    val character: CharacterDto?,
    val buttonStyle: ButtonStyle,
    val widgetStyle: WidgetStyle,
    val fontStyle: FontStyle,
    val ambientColor: String,
)

/** 내 테마. 편집 화면이 다시 PUT 할 수 있도록 이미지 ID도 담는다. */
data class ThemeResponse(
    val id: UUID,
    val skin: ThemeSkinDto,
    val backgroundAssetId: UUID?,
    val characterAssetId: UUID?,
    val shareCode: String?,
    /** 공유받은 테마면 true. 다시 공유할 수 없다. */
    val imported: Boolean,
    val importCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** 마켓·공유 코드 미리보기. */
data class SharedThemeResponse(
    val shareCode: String,
    val authorNickname: String,
    val importCount: Int,
    val skin: ThemeSkinDto,
    val createdAt: Instant,
)

data class ShareResponse(val shareCode: String)

data class ImportThemeRequest(
    @field:Pattern(regexp = "^[A-Za-z0-9]{8}$")
    val shareCode: String,
)

/** 테마를 고르지 않은 프로필에 쓰는 기본 테마. */
val DEFAULT_SKIN = ThemeSkinDto(
    name = "기본",
    colors = ThemeColorsDto(),
    backgroundImageUrl = null,
    character = null,
    buttonStyle = ButtonStyle.ROUNDED,
    widgetStyle = WidgetStyle.FILLED,
    fontStyle = FontStyle.DEFAULT,
    ambientColor = "#FFFFFF",
)

fun Theme.toSkin() = ThemeSkinDto(
    name = name,
    colors = ThemeColorsDto(colors.background, colors.surface, colors.primary, colors.onPrimary, colors.text),
    backgroundImageUrl = backgroundAssetId?.let(::assetUrl),
    character = characterAssetId?.let { CharacterDto(assetUrl(it), characterPlacement) },
    buttonStyle = buttonStyle,
    widgetStyle = widgetStyle,
    fontStyle = fontStyle,
    ambientColor = ambientColor,
)

fun Theme.toResponse() = ThemeResponse(
    id = requireId(),
    skin = toSkin(),
    backgroundAssetId = backgroundAssetId,
    characterAssetId = characterAssetId,
    shareCode = shareCode,
    imported = sourceThemeId != null,
    importCount = importCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Theme.applyFrom(request: ThemeRequest) {
    name = request.name.trim()
    colors.background = request.colors.background.uppercase()
    colors.surface = request.colors.surface.uppercase()
    colors.primary = request.colors.primary.uppercase()
    colors.onPrimary = request.colors.onPrimary.uppercase()
    colors.text = request.colors.text.uppercase()
    backgroundAssetId = request.backgroundAssetId
    characterAssetId = request.characterAssetId
    characterPlacement = request.characterPlacement
    buttonStyle = request.buttonStyle
    widgetStyle = request.widgetStyle
    fontStyle = request.fontStyle
    ambientColor = request.ambientColor.uppercase()
}

/** 공유받은 테마를 내 소유로 복사한다. 이미지는 바뀌지 않으므로 같은 파일을 참조한다. */
fun Theme.copyFor(ownerId: UUID) = Theme(
    ownerId = ownerId,
    name = name,
    colors = ThemeColors(colors.background, colors.surface, colors.primary, colors.onPrimary, colors.text),
    backgroundAssetId = backgroundAssetId,
    characterAssetId = characterAssetId,
    characterPlacement = characterPlacement,
    buttonStyle = buttonStyle,
    widgetStyle = widgetStyle,
    fontStyle = fontStyle,
    ambientColor = ambientColor,
    sourceThemeId = requireId(),
)

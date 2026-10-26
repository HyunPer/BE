package com.pleos.backend.theme

import com.pleos.backend.profile.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class AmbientLightDto(val color: String, val brightness: Int)

/**
 * 차량 설정(1단계) 적용 보고의 응답 = 2단계 웰컴 묶음.
 * 차량 앱은 skin으로 화면을 다시 그리고 → ambientLight를 Vehicle SDK로 적용하고 → 사운드와 greeting(TTS)을 재생한다.
 */
data class WelcomeResponse(
    val profileId: UUID,
    val driverNickname: String,
    val skin: ThemeSkinDto,
    val widgets: List<String>,
    val ambientLight: AmbientLightDto,
    val welcomeSoundId: String?,
    val greeting: String,
)

@Service
@Transactional(readOnly = true)
class WelcomeService(private val themes: ThemeRepository) {

    /** 프로필이 고른 테마가 없거나 삭제됐으면 기본 테마. 앰비언트 색은 테마, 밝기는 프로필을 따른다. */
    fun build(profile: Profile, driverNickname: String): WelcomeResponse {
        val skin = profile.themeId?.let { themes.findById(it).orElse(null) }?.toSkin() ?: DEFAULT_SKIN
        return WelcomeResponse(
            profileId = profile.requireId(),
            driverNickname = driverNickname,
            skin = skin,
            widgets = profile.display.widgets.toList(),
            ambientLight = AmbientLightDto(skin.ambientColor, profile.light.brightness),
            welcomeSoundId = profile.welcomeSoundId,
            greeting = "${driverNickname}님, ${profile.name} 설정을 적용했어요",
        )
    }
}

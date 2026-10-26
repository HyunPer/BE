package com.pleos.backend.theme

import com.pleos.backend.asset.AssetRepository
import com.pleos.backend.common.ApiException
import com.pleos.backend.common.Tokens
import com.pleos.backend.common.conflict
import com.pleos.backend.common.notFound
import com.pleos.backend.profile.ProfileRepository
import com.pleos.backend.user.UserRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ThemeRepository : JpaRepository<Theme, UUID> {
    fun findAllByOwnerIdOrderByCreatedAtAsc(ownerId: UUID): List<Theme>
    fun findByIdAndOwnerId(id: UUID, ownerId: UUID): Theme?
    fun findByShareCode(shareCode: String): Theme?
    fun existsByShareCode(shareCode: String): Boolean
    fun findTop50ByShareCodeIsNotNullOrderByImportCountDescCreatedAtDesc(): List<Theme>
}

@Service
@Transactional(readOnly = true)
class ThemeService(
    private val themes: ThemeRepository,
    private val assets: AssetRepository,
    private val profiles: ProfileRepository,
    private val users: UserRepository,
) {

    // ── 내 테마 ──

    fun findAll(ownerId: UUID): List<ThemeResponse> =
        themes.findAllByOwnerIdOrderByCreatedAtAsc(ownerId).map { it.toResponse() }

    fun findById(ownerId: UUID, id: UUID): ThemeResponse = load(ownerId, id).toResponse()

    @Transactional
    fun create(ownerId: UUID, request: ThemeRequest): ThemeResponse {
        validate(ownerId, request, current = null)
        val theme = Theme(ownerId = ownerId, name = request.name.trim()).also { it.applyFrom(request) }
        return themes.saveAndFlush(theme).toResponse()
    }

    @Transactional
    fun update(ownerId: UUID, id: UUID, request: ThemeRequest): ThemeResponse {
        val theme = load(ownerId, id)
        validate(ownerId, request, current = theme)
        theme.applyFrom(request)
        return themes.saveAndFlush(theme).toResponse()
    }

    /** 이 테마를 쓰던 프로필은 기본 테마로 돌아간다. 이미 공유받아 간 복사본은 그대로 남는다. */
    @Transactional
    fun delete(ownerId: UUID, id: UUID) {
        val theme = load(ownerId, id)
        profiles.clearTheme(id)
        themes.delete(theme)
    }

    // ── 공유·마켓 ──

    /** 공유 코드를 발급한다. 이미 공유 중이면 같은 코드. 공유받은 테마는 다시 공유할 수 없다. */
    @Transactional
    fun share(ownerId: UUID, id: UUID): ShareResponse {
        val theme = load(ownerId, id)
        if (theme.sourceThemeId != null) throw conflict("공유받은 테마는 다시 공유할 수 없습니다")
        val code = theme.shareCode ?: newShareCode().also { theme.shareCode = it }
        return ShareResponse(code)
    }

    /** 공유를 멈춘다. 이미 받아 간 사람의 복사본은 남는다. */
    @Transactional
    fun unshare(ownerId: UUID, id: UUID) {
        load(ownerId, id).shareCode = null
    }

    fun preview(shareCode: String): SharedThemeResponse = toShared(loadShared(shareCode))

    /** 공유 코드로 받은 테마를 내 테마로 복사한다. */
    @Transactional
    fun importShared(ownerId: UUID, request: ImportThemeRequest): ThemeResponse {
        val original = loadShared(request.shareCode)
        val copy = themes.save(original.copyFor(ownerId))
        original.countImport()
        return copy.toResponse()
    }

    /** 공유 중인 테마를 많이 받아 간 순서로. */
    fun market(): List<SharedThemeResponse> {
        val shared = themes.findTop50ByShareCodeIsNotNullOrderByImportCountDescCreatedAtDesc()
        val nicknames = users.findAllById(shared.map { it.ownerId }.toSet()).associate { it.requireId() to it.nickname }
        return shared.map { toShared(it, nicknames[it.ownerId].orEmpty()) }
    }

    // ── 내부 ──

    private fun load(ownerId: UUID, id: UUID): Theme =
        themes.findByIdAndOwnerId(id, ownerId) ?: throw notFound("테마를 찾을 수 없습니다: $id")

    private fun loadShared(shareCode: String): Theme =
        themes.findByShareCode(shareCode.uppercase()) ?: throw notFound("공유 코드가 올바르지 않거나 공유가 끝난 테마입니다")

    private fun toShared(theme: Theme, authorNickname: String? = null) = SharedThemeResponse(
        shareCode = checkNotNull(theme.shareCode),
        authorNickname = authorNickname ?: users.findById(theme.ownerId).map { it.nickname }.orElse(""),
        importCount = theme.importCount,
        skin = theme.toSkin(),
        createdAt = theme.createdAt,
    )

    private fun newShareCode(): String = generateSequence { Tokens.shareCode() }.first { !themes.existsByShareCode(it) }

    /**
     * 1) 운전 중 가독성: 글자/배경·카드 4.5:1, 버튼 글자/버튼 3:1 이상.
     * 2) 이미지: 내가 올린 것이거나, 이 테마가 이미 쓰고 있던 것(공유받은 테마의 원작자 이미지)만.
     */
    private fun validate(ownerId: UUID, request: ThemeRequest, current: Theme?) {
        val c = request.colors
        requireContrast("글자색과 배경색", c.text, c.background, Contrast.TEXT_MIN)
        requireContrast("글자색과 카드색", c.text, c.surface, Contrast.TEXT_MIN)
        requireContrast("버튼 글자색과 버튼색", c.onPrimary, c.primary, Contrast.BUTTON_MIN)

        val alreadyUsed = setOfNotNull(current?.backgroundAssetId, current?.characterAssetId)
        listOfNotNull(request.backgroundAssetId, request.characterAssetId).forEach { assetId ->
            if (assetId in alreadyUsed) return@forEach
            val asset = assets.findById(assetId).orElse(null)
            if (asset == null || asset.ownerId != ownerId) throw notFound("이미지를 찾을 수 없습니다: $assetId")
        }
    }

    private fun requireContrast(label: String, foreground: String, background: String, min: Double) {
        val ratio = Contrast.ratio(foreground, background)
        if (ratio < min) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "$label 대비가 부족합니다 (%.1f:1, 최소 %.1f:1). 운전 중에도 읽히도록 색을 조정해주세요".format(ratio, min),
            )
        }
    }
}

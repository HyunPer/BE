package com.pleos.backend.profile

import com.pleos.backend.common.notFound
import com.pleos.backend.theme.ThemeRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ProfileRepository : JpaRepository<Profile, UUID> {
    fun findAllByOwnerIdOrderByCreatedAtAsc(ownerId: UUID): List<Profile>
    fun findAllByOwnerIdInOrderByCreatedAtAsc(ownerIds: Collection<UUID>): List<Profile>
    fun findByIdAndOwnerId(id: UUID, ownerId: UUID): Profile?

    /** 테마가 삭제되면 그 테마를 쓰던 프로필은 기본 테마로 돌아간다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Profile p set p.themeId = null where p.themeId = :themeId")
    fun clearTheme(themeId: UUID): Int
}

/** 프로필은 만든 사람만 보고 고칠 수 있다. 남의 프로필은 404로 응답한다. */
@Service
@Transactional(readOnly = true)
class ProfileService(
    private val repository: ProfileRepository,
    private val themes: ThemeRepository,
) {

    fun findAll(ownerId: UUID): List<ProfileResponse> =
        repository.findAllByOwnerIdOrderByCreatedAtAsc(ownerId).map { it.toResponse() }

    fun findById(ownerId: UUID, id: UUID): ProfileResponse = load(ownerId, id).toResponse()

    @Transactional
    fun create(ownerId: UUID, request: ProfileRequest): ProfileResponse {
        requireOwnTheme(ownerId, request.themeId)
        return repository.saveAndFlush(request.toEntity(ownerId)).toResponse()
    }

    @Transactional
    fun update(ownerId: UUID, id: UUID, request: ProfileRequest): ProfileResponse {
        val profile = load(ownerId, id)
        requireOwnTheme(ownerId, request.themeId)
        profile.applyFrom(request)
        return repository.saveAndFlush(profile).toResponse()
    }

    @Transactional
    fun delete(ownerId: UUID, id: UUID) {
        repository.delete(load(ownerId, id))
    }

    /** 내 테마(직접 만들었거나 공유받아 복사한 것)만 연결할 수 있다. */
    private fun requireOwnTheme(ownerId: UUID, themeId: UUID?) {
        if (themeId != null && themes.findByIdAndOwnerId(themeId, ownerId) == null) {
            throw notFound("테마를 찾을 수 없습니다: $themeId")
        }
    }

    private fun load(ownerId: UUID, id: UUID): Profile =
        repository.findByIdAndOwnerId(id, ownerId) ?: throw notFound("프로필을 찾을 수 없습니다: $id")
}

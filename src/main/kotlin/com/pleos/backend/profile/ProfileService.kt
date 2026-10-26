package com.pleos.backend.profile

import com.pleos.backend.common.notFound
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ProfileRepository : JpaRepository<Profile, UUID> {
    fun findAllByOwnerIdOrderByCreatedAtAsc(ownerId: UUID): List<Profile>
    fun findAllByOwnerIdInOrderByCreatedAtAsc(ownerIds: Collection<UUID>): List<Profile>
    fun findByIdAndOwnerId(id: UUID, ownerId: UUID): Profile?
}

/** 프로필은 만든 사람만 보고 고칠 수 있다. 남의 프로필은 404로 응답한다. */
@Service
@Transactional(readOnly = true)
class ProfileService(private val repository: ProfileRepository) {

    fun findAll(ownerId: UUID): List<ProfileResponse> =
        repository.findAllByOwnerIdOrderByCreatedAtAsc(ownerId).map { it.toResponse() }

    fun findById(ownerId: UUID, id: UUID): ProfileResponse = load(ownerId, id).toResponse()

    @Transactional
    fun create(ownerId: UUID, request: ProfileRequest): ProfileResponse =
        repository.saveAndFlush(request.toEntity(ownerId)).toResponse()

    @Transactional
    fun update(ownerId: UUID, id: UUID, request: ProfileRequest): ProfileResponse {
        val profile = load(ownerId, id)
        profile.applyFrom(request)
        return repository.saveAndFlush(profile).toResponse()
    }

    @Transactional
    fun delete(ownerId: UUID, id: UUID) {
        repository.delete(load(ownerId, id))
    }

    private fun load(ownerId: UUID, id: UUID): Profile =
        repository.findByIdAndOwnerId(id, ownerId) ?: throw notFound("프로필을 찾을 수 없습니다: $id")
}

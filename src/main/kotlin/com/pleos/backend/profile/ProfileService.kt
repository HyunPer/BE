package com.pleos.backend.profile

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.ErrorResponseException
import java.util.UUID

interface ProfileRepository : JpaRepository<Profile, UUID> {
    fun findAllByOrderByCreatedAtAsc(): List<Profile>
}

class ProfileNotFoundException(id: UUID) : ErrorResponseException(
    HttpStatus.NOT_FOUND,
    ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다: $id"),
    null,
)

@Service
@Transactional(readOnly = true)
class ProfileService(private val repository: ProfileRepository) {

    fun findAll(): List<ProfileResponse> =
        repository.findAllByOrderByCreatedAtAsc().map { it.toResponse() }

    fun findById(id: UUID): ProfileResponse = load(id).toResponse()

    @Transactional
    fun create(request: ProfileRequest): ProfileResponse =
        repository.saveAndFlush(request.toEntity()).toResponse()

    @Transactional
    fun update(id: UUID, request: ProfileRequest): ProfileResponse {
        val profile = load(id)
        profile.applyFrom(request)
        return repository.saveAndFlush(profile).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        repository.delete(load(id))
    }

    private fun load(id: UUID): Profile =
        repository.findById(id).orElseThrow { ProfileNotFoundException(id) }
}

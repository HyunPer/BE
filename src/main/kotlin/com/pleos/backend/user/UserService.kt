package com.pleos.backend.user

import com.pleos.backend.common.Tokens
import com.pleos.backend.common.notFound
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByTokenHash(tokenHash: String): User?
}

data class SignUpRequest(
    @field:NotBlank
    @field:Size(max = 20)
    val nickname: String,
)

data class UserResponse(val id: UUID, val nickname: String, val createdAt: Instant)

/** accessToken은 이 응답에서만 받을 수 있다. 폰 앱은 기기에 저장하고 Authorization: Bearer 헤더로 보낸다. */
data class SignUpResponse(val user: UserResponse, val accessToken: String)

fun User.toResponse() = UserResponse(requireId(), nickname, createdAt)

@Service
@Transactional(readOnly = true)
class UserService(private val repository: UserRepository) {

    @Transactional
    fun signUp(request: SignUpRequest): SignUpResponse {
        val token = Tokens.generate()
        val user = repository.save(User(nickname = request.nickname.trim(), tokenHash = Tokens.hash(token)))
        return SignUpResponse(user.toResponse(), token)
    }

    fun me(userId: UUID): UserResponse =
        repository.findById(userId).orElseThrow { notFound("사용자를 찾을 수 없습니다: $userId") }.toResponse()
}

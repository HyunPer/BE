package com.pleos.backend.profile

import com.pleos.backend.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID

@Tag(name = "프로필")
@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController(private val service: ProfileService) {

    @Operation(summary = "내 프로필 목록", description = "생성 순으로 돌려준다. 개수 제한 없음.")
    @GetMapping
    fun list(user: AuthUser): List<ProfileResponse> = service.findAll(user.id)

    @Operation(summary = "프로필 조회")
    @GetMapping("/{id}")
    fun get(user: AuthUser, @PathVariable id: UUID): ProfileResponse = service.findById(user.id, id)

    @Operation(summary = "프로필 생성", description = "name만 필수. 빠진 설정 묶음은 기본값으로 채운다.")
    @PostMapping
    fun create(user: AuthUser, @Valid @RequestBody request: ProfileRequest): ResponseEntity<ProfileResponse> {
        val created = service.create(user.id, request)
        val location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(created.id).toUri()
        return ResponseEntity.created(location).body(created)
    }

    @Operation(summary = "프로필 전체 교체", description = "보내지 않은 묶음은 기본값으로 돌아간다. 프로필 전체를 보낸다.")
    @PutMapping("/{id}")
    fun update(user: AuthUser, @PathVariable id: UUID, @Valid @RequestBody request: ProfileRequest): ProfileResponse =
        service.update(user.id, id, request)

    @Operation(summary = "프로필 삭제")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(user: AuthUser, @PathVariable id: UUID) = service.delete(user.id, id)
}

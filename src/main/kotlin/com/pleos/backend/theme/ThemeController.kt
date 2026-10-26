package com.pleos.backend.theme

import com.pleos.backend.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "테마")
@RestController
@RequestMapping("/api/v1/themes")
class ThemeController(private val service: ThemeService) {

    @Operation(summary = "내 테마 목록", description = "직접 만든 테마와 공유받은 테마.")
    @GetMapping
    fun list(user: AuthUser): List<ThemeResponse> = service.findAll(user.id)

    @Operation(summary = "테마 조회")
    @GetMapping("/{id}")
    fun get(user: AuthUser, @PathVariable id: UUID): ThemeResponse = service.findById(user.id, id)

    @Operation(
        summary = "테마 생성",
        description = "name만 필수. 글자 대비가 부족하면 400. 이미지는 /api/v1/assets로 먼저 올린 ID.",
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(user: AuthUser, @Valid @RequestBody request: ThemeRequest): ThemeResponse = service.create(user.id, request)

    @Operation(summary = "테마 전체 교체")
    @PutMapping("/{id}")
    fun update(user: AuthUser, @PathVariable id: UUID, @Valid @RequestBody request: ThemeRequest): ThemeResponse =
        service.update(user.id, id, request)

    @Operation(summary = "테마 삭제", description = "이 테마를 쓰던 프로필은 기본 테마로 돌아간다.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(user: AuthUser, @PathVariable id: UUID) = service.delete(user.id, id)

    @Operation(summary = "공유 시작", description = "8자리 공유 코드 발급. 공유받은 테마는 409.")
    @PostMapping("/{id}/share")
    fun share(user: AuthUser, @PathVariable id: UUID): ShareResponse = service.share(user.id, id)

    @Operation(summary = "공유 중지", description = "이미 받아 간 복사본은 남는다.")
    @DeleteMapping("/{id}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unshare(user: AuthUser, @PathVariable id: UUID) = service.unshare(user.id, id)

    @Operation(summary = "공유 코드 미리보기", description = "받기 전에 모양을 보여줄 때.")
    @GetMapping("/shared/{shareCode}")
    fun preview(@PathVariable shareCode: String): SharedThemeResponse = service.preview(shareCode)

    @Operation(summary = "공유 코드로 받기", description = "내 테마로 복사된다.")
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    fun import(user: AuthUser, @Valid @RequestBody request: ImportThemeRequest): ThemeResponse =
        service.importShared(user.id, request)

    @Operation(summary = "테마 마켓", description = "공유 중인 테마를 많이 받아 간 순서로 최대 50개.")
    @GetMapping("/market")
    fun market(): List<SharedThemeResponse> = service.market()
}

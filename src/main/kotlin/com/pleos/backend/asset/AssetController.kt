package com.pleos.backend.asset

import com.pleos.backend.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Duration
import java.util.UUID

@Tag(name = "이미지")
@RestController
class AssetController(private val service: AssetService) {

    @Operation(summary = "이미지 업로드", description = "테마 배경·캐릭터용. PNG·JPEG·WEBP, 크기 제한 있음. GIF 불가.")
    @PostMapping("/api/v1/assets", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(user: AuthUser, @RequestParam("file") file: MultipartFile): AssetResponse = service.upload(user.id, file)

    /**
     * 차량 앱·폰 앱 이미지 로더가 헤더 없이 바로 받을 수 있게 토큰 없이 연다.
     * 추측할 수 없는 UUID 주소로만 접근되고, 내용이 바뀌지 않으므로 오래 캐시한다.
     */
    @Operation(summary = "이미지 내려받기", description = "토큰 불필요. 응답은 1년 캐시.")
    @SecurityRequirements
    @GetMapping("/files/{id}")
    fun download(@PathVariable id: UUID): ResponseEntity<Resource> {
        val file = service.load(id)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .body(FileSystemResource(file.path))
    }
}

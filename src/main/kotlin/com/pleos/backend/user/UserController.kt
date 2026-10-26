package com.pleos.backend.user

import com.pleos.backend.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "사용자")
@RestController
@RequestMapping("/api/v1/users")
class UserController(private val service: UserService) {

    @Operation(summary = "가입", description = "폰 앱 첫 실행 때 호출한다. 응답의 accessToken을 기기에 저장한다.")
    @SecurityRequirements
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@Valid @RequestBody request: SignUpRequest): SignUpResponse = service.signUp(request)

    @Operation(summary = "내 정보")
    @GetMapping("/me")
    fun me(user: AuthUser): UserResponse = service.me(user.id)
}

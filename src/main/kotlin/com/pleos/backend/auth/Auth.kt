package com.pleos.backend.auth

import com.pleos.backend.common.Tokens
import com.pleos.backend.common.unauthorized
import com.pleos.backend.user.UserRepository
import com.pleos.backend.vehicle.VehicleRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.cors.CorsUtils
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.HandlerInterceptor
import java.util.UUID

/** 폰 앱 사용자. 컨트롤러 파라미터로 선언하면 토큰으로 확인된 사용자가 들어온다. */
data class AuthUser(val id: UUID)

/** 차량 앱(차량 1대). 차량 등록 때 받은 토큰으로 확인된다. */
data class AuthVehicle(val id: UUID)

/**
 * Authorization: Bearer 토큰을 해시해 주체를 찾고 요청 속성에 담는다.
 * 토큰이 없거나 맞지 않으면 401 ProblemDetail로 끝난다.
 */
class BearerTokenInterceptor(
    private val principalType: Class<*>,
    private val findPrincipal: (tokenHash: String) -> Any?,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (CorsUtils.isPreFlightRequest(request)) return true
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
            ?: throw unauthorized("Authorization 헤더가 필요합니다")
        val token = header.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw unauthorized("Bearer 토큰 형식이 아닙니다")
        val principal = findPrincipal(Tokens.hash(token)) ?: throw unauthorized("유효하지 않은 토큰입니다")
        request.setAttribute(principalType.name, principal)
        return true
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}

@Component
class AuthInterceptors(userRepository: UserRepository, vehicleRepository: VehicleRepository) {
    val user = BearerTokenInterceptor(AuthUser::class.java) { hash ->
        userRepository.findByTokenHash(hash)?.let { AuthUser(it.requireId()) }
    }
    val vehicle = BearerTokenInterceptor(AuthVehicle::class.java) { hash ->
        vehicleRepository.findByTokenHash(hash)?.let { AuthVehicle(it.requireId()) }
    }
}

/** 인터셉터가 담아둔 AuthUser·AuthVehicle을 컨트롤러 파라미터로 넘긴다. */
class PrincipalArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == AuthUser::class.java || parameter.parameterType == AuthVehicle::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any = webRequest.getAttribute(parameter.parameterType.name, RequestAttributes.SCOPE_REQUEST)
        ?: throw unauthorized("인증이 필요한 요청입니다")
}

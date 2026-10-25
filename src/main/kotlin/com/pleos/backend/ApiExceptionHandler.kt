package com.pleos.backend

import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * 모든 API 오류를 RFC 9457 ProblemDetail(application/problem+json)로 응답한다.
 * 404(ErrorResponseException), 400(검증 실패·잘못된 JSON) 등이 모두 여기로 모인다.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler()

package com.pleos.backend.common

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * 모든 API 오류를 RFC 9457 ProblemDetail(application/problem+json)로 응답한다.
 * ApiException(401·404·409), 검증 실패·잘못된 JSON(400) 등이 모두 여기로 모인다.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler()

/** 서비스에서 던지는 업무 오류. 상태 코드와 사람이 읽을 수 있는 설명만 담는다. */
class ApiException(status: HttpStatus, detail: String) :
    ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null)

fun unauthorized(detail: String) = ApiException(HttpStatus.UNAUTHORIZED, detail)

/** 남의 리소스도 존재를 드러내지 않도록 403 대신 404로 응답한다. */
fun notFound(detail: String) = ApiException(HttpStatus.NOT_FOUND, detail)

fun conflict(detail: String) = ApiException(HttpStatus.CONFLICT, detail)

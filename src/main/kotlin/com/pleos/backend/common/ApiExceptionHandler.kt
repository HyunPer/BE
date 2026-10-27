package com.pleos.backend.common

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * 모든 API 오류를 RFC 9457 ProblemDetail(application/problem+json)로 응답한다.
 * ApiException(401·403·404·409), 검증 실패·잘못된 JSON(400) 등이 모두 여기로 모인다.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 동시에 같은 요청이 들어와 유일 제약에 걸린 경우. 클라이언트는 다시 시도하면 된다. */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun conflictOnConstraint(e: DataIntegrityViolationException): ProblemDetail {
        log.warn("데이터 제약 위반: {}", e.mostSpecificCause.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "동시에 같은 요청이 처리되었습니다. 다시 시도해주세요")
    }

    /** 예상하지 못한 오류도 내부 정보를 숨긴 ProblemDetail로. 원인은 서버 로그에만 남긴다. */
    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ProblemDetail {
        log.error("처리하지 못한 오류", e)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요")
    }
}

/** 서비스에서 던지는 업무 오류. 상태 코드와 사람이 읽을 수 있는 설명만 담는다. */
class ApiException(status: HttpStatus, detail: String) :
    ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null)

fun unauthorized(detail: String) = ApiException(HttpStatus.UNAUTHORIZED, detail)

/** 존재는 드러나도 되지만 권한이 없는 경우(같은 차 멤버가 남의 예약을 지우려는 등). */
fun forbidden(detail: String) = ApiException(HttpStatus.FORBIDDEN, detail)

/** 남의 리소스도 존재를 드러내지 않도록 403 대신 404로 응답한다. */
fun notFound(detail: String) = ApiException(HttpStatus.NOT_FOUND, detail)

fun conflict(detail: String) = ApiException(HttpStatus.CONFLICT, detail)

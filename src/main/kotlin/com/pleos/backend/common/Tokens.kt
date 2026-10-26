package com.pleos.backend.common

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

/**
 * 접근 토큰 발급과 해시. 원문 토큰은 발급 응답에서 한 번만 내려주고, DB에는 SHA-256 해시만 저장한다.
 */
object Tokens {
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    /** 차량 화면에 띄우는 6자리 숫자 코드. */
    fun pairingCode(): String = "%06d".format(random.nextInt(1_000_000))
}

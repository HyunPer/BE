package com.pleos.backend.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.x 명도 대비. 운전 중 한눈에 읽혀야 하므로 테마 저장 때 검사한다.
 * 본문 글자는 4.5:1, 버튼 글자(큰 글자)는 3:1 이상.
 */
object Contrast {
    const val TEXT_MIN = 4.5
    const val BUTTON_MIN = 3.0

    fun ratio(hexA: String, hexB: String): Double {
        val a = luminance(hexA)
        val b = luminance(hexB)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun luminance(hex: String): Double {
        val rgb = hex.removePrefix("#").toInt(16)
        val (r, g, b) = listOf(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF).map { channel(it) }
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}

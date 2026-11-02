package com.pleos.backend.history

import com.pleos.backend.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "탑승기록")
@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/history")
class ApplyHistoryController(private val service: ApplyHistoryService){
    @Operation(summary = "최근 탑승 기록", description = "누가 어떤 프로필로 탔는지 최신순, 멤버만(아니면 404), limit 1-50, 기본 20")
    @GetMapping
    fun recent(
        user: AuthUser,
        @PathVariable vehicleId: UUID,
        @RequestParam(defaultValue = "20") limit: Int,
    ): List<ApplyLogResponse> = service.recent(user.id, vehicleId, limit)
}
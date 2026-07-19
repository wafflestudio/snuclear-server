package com.wafflestudio.team8server.tour.contoller

import com.wafflestudio.team8server.common.auth.LoggedInUserId
import com.wafflestudio.team8server.tour.dto.TourCompletionRequest
import com.wafflestudio.team8server.tour.dto.TourStatusResponse
import com.wafflestudio.team8server.tour.service.TourService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "서비스 투어 API", description = "투어 표시 필요 여부 조회 및 완료 처리를 제공합니다.")
@RestController
@RequestMapping("/api/tour")
@SecurityRequirement(name = "Bearer Authentication")
class TourController(
    private val tourService: TourService,
) {
    @Operation(
        summary = "투어 표시 필요 여부 조회",
        description = "현재 버전의 투어를 로그인한 유저에게 표시해야 하는지 조회합니다.",
    )
    @GetMapping("/status")
    @ResponseStatus(HttpStatus.OK)
    fun getStatus(
        @Parameter(hidden = true) @LoggedInUserId userId: Long,
    ): TourStatusResponse = tourService.getStatus(userId)

    @Operation(
        summary = "투어 완료",
        description = "사용자가 현재 공개된 투어를 완료했음을 기록합니다.",
    )
    @PutMapping("/completion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun completeTour(
        @Parameter(hidden = true) @LoggedInUserId userId: Long,
        @RequestBody request: TourCompletionRequest,
    ) {
        tourService.completeTour(
            userId = userId,
            requestedPublishedAt = request.publishedAt,
        )
    }
}

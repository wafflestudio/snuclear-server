package com.wafflestudio.team8server.tour.contoller

import com.wafflestudio.team8server.common.auth.LoggedInUserId
import com.wafflestudio.team8server.tour.dto.TourPublishResponse
import com.wafflestudio.team8server.tour.service.TourService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin", description = "관리자 API")
@RestController
@RequestMapping("/api/admin/tour")
@SecurityRequirement(name = "Bearer Authentication")
class TourAdminController(
    private val tourService: TourService,
) {
    @Operation(
        summary = "투어 업데이트 공개",
        description = "현재 서버 시각을 최신 투어 공개 시각으로 설정합니다. (관리자 전용)",
    )
    @PostMapping("/publish")
    @ResponseStatus(HttpStatus.OK)
    fun publishTour(
        @Parameter(hidden = true) @LoggedInUserId userId: Long,
    ): TourPublishResponse =
        TourPublishResponse(
            publishedAt = tourService.publishTour(),
        )
}

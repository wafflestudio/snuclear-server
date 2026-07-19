package com.wafflestudio.team8server.tour.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "투어 업데이트 공개 응답")
data class TourPublishResponse(
    @Schema(description = "투어 업데이트 공개 시각", example = "2026-07-19T12:00:00.000000")
    val publishedAt: LocalDateTime,
)

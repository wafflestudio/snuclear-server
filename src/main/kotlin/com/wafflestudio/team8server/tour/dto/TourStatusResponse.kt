package com.wafflestudio.team8server.tour.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "투어 표시 필요 여부 응답")
data class TourStatusResponse(
    @Schema(description = "투어 표시 필요 여부", example = "true")
    val shouldShow: Boolean,
    @Schema(description = "최신 투어 공개 시각", example = "2026-07-19T12:00:00.000000")
    val publishedAt: LocalDateTime,
    @Schema(description = "유저의 최근 투어 완료 시각", example = "2026-07-19T12:00:00.000000", nullable = true)
    val completedAt: LocalDateTime?,
)

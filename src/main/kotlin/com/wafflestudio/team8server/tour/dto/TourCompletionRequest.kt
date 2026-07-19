package com.wafflestudio.team8server.tour.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "투어 완료 요청")
data class TourCompletionRequest(
    @Schema(description = "완료한 투어의 공개 시각", example = "2026-07-19T12:00:00.000000")
    val publishedAt: LocalDateTime,
)

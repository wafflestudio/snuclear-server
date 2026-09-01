package com.wafflestudio.team8server.syncwithsite.controller

import com.wafflestudio.team8server.common.exception.ErrorResponse
import com.wafflestudio.team8server.syncwithsite.dto.SugangPeriodResponse
import com.wafflestudio.team8server.syncwithsite.service.SyncWithSiteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@Tag(name = "수강신청 기간 정보 연동 API", description = "수강신청 사이트의 기간 정보를 가져옵니다")
@RestController
@RequestMapping("/api/v1/syncwithsite")
class SyncWithSiteController(
    private val syncWithSiteService: SyncWithSiteService,
) {
    @Operation(
        summary = "보존된 학기별 수강신청 기간 정보 조회",
        description =
            "이전에 크롤링되어 보존된 학기별 일정 정보를 가져옵니다." +
                "실패 시 크롤링을 수행하여 수강신청 기간 정보를 가져옵니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "수강신청 기간 정보 크롤링 또는 파싱 성공",
            ),
            ApiResponse(
                responseCode = "404",
                description = "캐시에 저장된 값이 없으며, 수강신청 사이트 접속 실패 또는 대상 요소를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    @GetMapping("/sugang-period")
    fun getSugangPeriod(): SugangPeriodResponse = syncWithSiteService.getSugangPeriod()

    @Operation(summary = "수강신청 기간 정보 자동 동기화 ON")
    @PostMapping("/auto/enable")
    fun enableAuto(): ResponseEntity<Unit> {
        syncWithSiteService.enableAuto()
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "수강신청 기간 정보 자동 동기화 OFF")
    @PostMapping("/auto/disable")
    fun disableAuto(): ResponseEntity<Unit> {
        syncWithSiteService.disableAuto()
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "수강신청 기간 정보 자동 동기화 상태 및 최근 덤프 이력 조회")
    @GetMapping("/auto")
    fun getAutoStatus(): ResponseEntity<Map<String, Any?>> {
        val setting = syncWithSiteService.getSetting()
        val lastRun = syncWithSiteService.getLastRun()

        return ResponseEntity.ok(
            mapOf(
                "enabled" to setting.enabled,
                "updatedAt" to setting.updatedAt,
                "lastRun" to
                    lastRun?.let {
                        mapOf(
                            "status" to it.status.name,
                            "startedAt" to it.startedAt,
                            "finishedAt" to it.finishedAt,
                            "message" to it.message,
                            "hasDumpData" to (it.dumpedData != null),
                        )
                    },
            ),
        )
    }

    @Operation(summary = "수강신청 기간 정보 동기화 & 덤핑 즉시 실행")
    @PostMapping("/run")
    fun runOnce(): ResponseEntity<Map<String, Any>> {
        val startedAt = LocalDateTime.now()
        syncWithSiteService.runOnce()
        return ResponseEntity.accepted().body(
            mapOf(
                "accepted" to true,
                "startedAt" to startedAt,
            ),
        )
    }
}

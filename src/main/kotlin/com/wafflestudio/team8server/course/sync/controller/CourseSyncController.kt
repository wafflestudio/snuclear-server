package com.wafflestudio.team8server.course.sync.controller

import com.wafflestudio.team8server.course.sync.CourseSyncService
import com.wafflestudio.team8server.course.sync.dto.CourseSyncAutoStatusResponse
import com.wafflestudio.team8server.course.sync.dto.CourseSyncLastRunResponse
import com.wafflestudio.team8server.course.sync.dto.CourseSyncRunAcceptedResponse
import com.wafflestudio.team8server.course.sync.dto.CourseSyncRunRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@Tag(name = "강의 동기화 제어 API", description = "강의 동기화 자동/수동 제어 기능을 제공합니다.")
@RestController
@RequestMapping("/api/courses/course-sync")
class CourseSyncController(
    private val service: CourseSyncService,
) {
    @Operation(summary = "자동 동기화 ON")
    @PostMapping("/auto/enable")
    fun enableAuto(): ResponseEntity<Unit> {
        service.enableAuto()
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "자동 동기화 OFF")
    @PostMapping("/auto/disable")
    fun disableAuto(): ResponseEntity<Unit> {
        service.disableAuto()
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "자동 동기화 상태 조회")
    @GetMapping("/auto")
    fun getAutoStatus(): ResponseEntity<CourseSyncAutoStatusResponse> {
        val setting = service.getSetting()
        val last = service.getLastRun()
        val intervalMinutes = service.getFixedDelayMinutes()

        val lastRun =
            last?.let {
                CourseSyncLastRunResponse(
                    status = it.status.name,
                    startedAt = it.startedAt,
                    finishedAt = it.finishedAt,
                    year = it.year,
                    semester = it.semester,
                    rowsUpserted = it.rowsUpserted,
                    message = it.message,
                )
            }

        return ResponseEntity.ok(
            CourseSyncAutoStatusResponse(
                enabled = setting.enabled,
                intervalMinutes = intervalMinutes,
                lastRun = lastRun,
                updatedAt = setting.updatedAt,
            ),
        )
    }

    @Operation(summary = "강의 동기화 즉시 실행")
    @PostMapping("/run")
    fun runOnce(
        @RequestBody request: CourseSyncRunRequest,
    ): ResponseEntity<CourseSyncRunAcceptedResponse> {
        val startedAt = LocalDateTime.now()
        service.runOnce(request.year, request.semester)
        return ResponseEntity.accepted().body(
            CourseSyncRunAcceptedResponse(
                accepted = true,
                startedAt = startedAt,
            ),
        )
    }

    @Operation(summary = "장바구니 스냅샷 수동 수집")
    @PostMapping("/cart-snapshot/run")
    fun runCartSnapshot(
        @RequestBody request: CourseSyncRunRequest,
    ): ResponseEntity<CourseSyncRunAcceptedResponse> {
        val startedAt = LocalDateTime.now()
        service.runCartSnapshotOnce(request.year, request.semester)
        return ResponseEntity.ok().body(
            CourseSyncRunAcceptedResponse(
                accepted = true,
                startedAt = startedAt,
            ),
        )
    }
}

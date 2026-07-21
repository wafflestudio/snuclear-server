package com.wafflestudio.team8server.course.sync

import com.wafflestudio.team8server.common.exception.CourseSyncAlreadyRunningException
import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.course.service.CourseCartSnapshotService
import com.wafflestudio.team8server.course.service.CourseService
import com.wafflestudio.team8server.course.sync.model.CourseSyncRun
import com.wafflestudio.team8server.course.sync.model.CourseSyncRunStatus
import com.wafflestudio.team8server.course.sync.model.CourseSyncSetting
import com.wafflestudio.team8server.course.sync.repository.CourseSyncRunRepository
import com.wafflestudio.team8server.course.sync.repository.CourseSyncSettingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

@Service
class CourseSyncService(
    private val props: CourseSyncProperties,
    private val courseService: CourseService,
    private val courseCartSnapshotService: CourseCartSnapshotService,
    private val excelClient: SugangCourseExcelClient,
    private val settingRepository: CourseSyncSettingRepository,
    private val runRepository: CourseSyncRunRepository,
) {
    private val log = LoggerFactory.getLogger(CourseSyncService::class.java)
    private val running = AtomicBoolean(false)

    @Transactional
    fun enableAuto(): CourseSyncSetting {
        val cur = settingRepository.findById(1L).orElse(CourseSyncSetting(id = 1L))
        val updated = CourseSyncSetting(id = 1L, enabled = true, updatedAt = LocalDateTime.now())
        return settingRepository.save(updated.copyFrom(cur))
    }

    @Transactional
    fun disableAuto(): CourseSyncSetting {
        val cur = settingRepository.findById(1L).orElse(CourseSyncSetting(id = 1L))
        val updated = CourseSyncSetting(id = 1L, enabled = false, updatedAt = LocalDateTime.now())
        return settingRepository.save(updated.copyFrom(cur))
    }

    fun getSetting(): CourseSyncSetting =
        settingRepository.findById(1L).orElse(CourseSyncSetting(id = 1L, enabled = false, updatedAt = LocalDateTime.now()))

    fun getLastRun(): CourseSyncRun? = runRepository.findTopByOrderByStartedAtDesc()

    fun getFixedDelayMinutes(): Long = props.auto.fixedDelayMillis / 60_000L

    fun isEnabled(): Boolean = getSetting().enabled

    fun defaultTarget(): Pair<Int, Semester>? {
        val y = props.defaultTarget.year
        val s = props.defaultTarget.semester
        if (y == null || s == null) return null
        return y to s
    }

    fun runOnce(
        year: Int,
        semester: Semester,
    ) {
        if (!running.compareAndSet(false, true)) {
            throw CourseSyncAlreadyRunningException()
        }

        val startedAt = LocalDateTime.now()
        try {
            log.info("Course sync started (year={}, semester={})", year, semester)

            val bytes = excelClient.downloadExcel(year, semester)
            val mf =
                ByteArrayMultipartFile(
                    bytes = bytes,
                    name = "file",
                    originalFilename = "courses_${year}_${semester.name}.xls",
                )

            courseService.import(year, semester, mf)

            runRepository.save(
                CourseSyncRun(
                    status = CourseSyncRunStatus.SUCCESS,
                    startedAt = startedAt,
                    finishedAt = LocalDateTime.now(),
                    year = year,
                    semester = semester,
                    rowsUpserted = null,
                    message = null,
                ),
            )

            log.info("Course sync success (year={}, semester={})", year, semester)
        } catch (e: Exception) {
            runRepository.save(
                CourseSyncRun(
                    status = CourseSyncRunStatus.FAILED,
                    startedAt = startedAt,
                    finishedAt = LocalDateTime.now(),
                    year = year,
                    semester = semester,
                    rowsUpserted = null,
                    message = (e.message ?: e.javaClass.simpleName).take(500),
                ),
            )
            throw e
        } finally {
            running.set(false)
        }
    }

    fun runCartSnapshotOnce(
        year: Int,
        semester: Semester,
    ): Int {
        if (!running.compareAndSet(false, true)) {
            throw CourseSyncAlreadyRunningException()
        }

        try {
            log.info("Course cart snapshot started (year={}, semester={})", year, semester)

            val bytes = excelClient.downloadExcel(year, semester)
            val mf =
                ByteArrayMultipartFile(
                    bytes = bytes,
                    name = "file",
                    originalFilename = "courses_${year}_${semester.name}.xls",
                )
            val captured = courseCartSnapshotService.capture(year, semester, mf)

            log.info(
                "Course cart snapshot success (year={}, semester={}, rows={})",
                year,
                semester,
                captured,
            )
            return captured
        } finally {
            running.set(false)
        }
    }

    private fun CourseSyncSetting.copyFrom(prev: CourseSyncSetting): CourseSyncSetting =
        CourseSyncSetting(
            id = prev.id,
            enabled = this.enabled,
            updatedAt = this.updatedAt,
        )
}

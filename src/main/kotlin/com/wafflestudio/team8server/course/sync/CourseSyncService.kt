package com.wafflestudio.team8server.course.sync

import com.wafflestudio.team8server.common.exception.CourseSyncAlreadyRunningException
import com.wafflestudio.team8server.course.model.CourseCartSnapshotRunStatus
import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.course.repository.CourseCartSnapshotRunRepository
import com.wafflestudio.team8server.course.service.CourseCartSnapshotService
import com.wafflestudio.team8server.course.service.CourseService
import com.wafflestudio.team8server.course.sync.model.CourseSyncRun
import com.wafflestudio.team8server.course.sync.model.CourseSyncRunStatus
import com.wafflestudio.team8server.course.sync.model.CourseSyncSetting
import com.wafflestudio.team8server.course.sync.repository.CourseSyncRunRepository
import com.wafflestudio.team8server.course.sync.repository.CourseSyncSettingRepository
import com.wafflestudio.team8server.syncwithsite.service.ParsedSugangPeriod
import com.wafflestudio.team8server.syncwithsite.service.SugangPeriodParser
import com.wafflestudio.team8server.syncwithsite.service.SyncWithSiteService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Service
class CourseSyncService(
    private val props: CourseSyncProperties,
    private val courseService: CourseService,
    private val courseCartSnapshotService: CourseCartSnapshotService,
    private val courseCartSnapshotRunRepository: CourseCartSnapshotRunRepository,
    private val excelClient: SugangCourseExcelClient,
    private val settingRepository: CourseSyncSettingRepository,
    private val runRepository: CourseSyncRunRepository,
    private val syncWithSiteService: SyncWithSiteService,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(CourseSyncService::class.java)
    private val running = AtomicBoolean(false)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    companion object {
        private const val CART_SNAPSHOT_CLAIM_TIMEOUT_MINUTES = 30L
        private const val AUTOMATIC_RETRY_GRACE_HOURS = 24L
    }

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

    fun automaticTarget(): ParsedSugangPeriod? = syncWithSiteService.getSavedSugangPeriod()?.let(SugangPeriodParser::parse)

    fun shouldRunAutomatically(
        target: ParsedSugangPeriod,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean {
        val retryDeadline = target.lastCourseChangeAt?.plusHours(AUTOMATIC_RETRY_GRACE_HOURS) ?: return false
        if (now.isAfter(retryDeadline)) return false

        return target.isCourseSyncActiveAt(now) ||
            !runRepository.existsByStatusAndYearAndSemester(
                CourseSyncRunStatus.SUCCESS,
                target.year,
                target.semester,
            )
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
    ): Int = runCartSnapshot(year, semester) {}

    private fun runCartSnapshot(
        year: Int,
        semester: Semester,
        afterCapture: (Int) -> Unit,
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
            val captured =
                requireNotNull(
                    transactionTemplate.execute {
                        val count = courseCartSnapshotService.capture(year, semester, mf)
                        afterCapture(count)
                        count
                    },
                )

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

    fun captureCartSnapshotIfDue(
        target: ParsedSugangPeriod,
        now: LocalDateTime = LocalDateTime.now(),
    ): Int? {
        val firstCourseChangeAt = target.firstCourseChangeAt ?: return null
        val retryDeadline = target.lastCourseChangeAt?.plusHours(AUTOMATIC_RETRY_GRACE_HOURS) ?: return null
        if (now.isBefore(firstCourseChangeAt) || now.isAfter(retryDeadline)) return null
        val claimToken = UUID.randomUUID().toString()
        if (!claimCartSnapshot(target.year, target.semester, claimToken, now)) return null

        return try {
            runCartSnapshot(target.year, target.semester) {
                check(
                    courseCartSnapshotRunRepository.complete(
                        year = target.year,
                        semester = target.semester,
                        claimToken = claimToken,
                        capturedAt = now,
                        pending = CourseCartSnapshotRunStatus.PENDING,
                        success = CourseCartSnapshotRunStatus.SUCCESS,
                    ) == 1,
                ) { "Course cart snapshot claim was lost" }
            }
        } catch (e: Exception) {
            courseCartSnapshotRunRepository.fail(
                year = target.year,
                semester = target.semester,
                claimToken = claimToken,
                message = (e.message ?: e.javaClass.simpleName).take(500),
                pending = CourseCartSnapshotRunStatus.PENDING,
                failed = CourseCartSnapshotRunStatus.FAILED,
            )
            throw e
        }
    }

    private fun claimCartSnapshot(
        year: Int,
        semester: Semester,
        claimToken: String,
        now: LocalDateTime,
    ): Boolean {
        if (
            courseCartSnapshotRunRepository.insertPendingClaim(
                year = year,
                semester = semester.name,
                claimToken = claimToken,
                claimedAt = now,
            ) == 1
        ) {
            return true
        }

        return courseCartSnapshotRunRepository.reclaim(
            year = year,
            semester = semester,
            claimToken = claimToken,
            claimedAt = now,
            staleBefore = now.minusMinutes(CART_SNAPSHOT_CLAIM_TIMEOUT_MINUTES),
            pending = CourseCartSnapshotRunStatus.PENDING,
            failed = CourseCartSnapshotRunStatus.FAILED,
        ) == 1
    }

    private fun CourseSyncSetting.copyFrom(prev: CourseSyncSetting): CourseSyncSetting =
        CourseSyncSetting(
            id = prev.id,
            enabled = this.enabled,
            updatedAt = this.updatedAt,
        )
}

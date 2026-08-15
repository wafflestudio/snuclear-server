package com.wafflestudio.team8server.course.sync

import com.wafflestudio.team8server.course.model.CourseCartSnapshotRun
import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.course.repository.CourseCartSnapshotRunRepository
import com.wafflestudio.team8server.course.repository.CourseCartSnapshotRepository
import com.wafflestudio.team8server.course.repository.CourseRepository
import com.wafflestudio.team8server.course.service.CourseCartSnapshotService
import com.wafflestudio.team8server.course.service.CourseExcelParser
import com.wafflestudio.team8server.course.service.CourseService
import com.wafflestudio.team8server.course.sync.model.CourseSyncRunStatus
import com.wafflestudio.team8server.course.sync.repository.CourseSyncRunRepository
import com.wafflestudio.team8server.course.sync.repository.CourseSyncSettingRepository
import com.wafflestudio.team8server.syncwithsite.service.ParsedSugangPeriod
import com.wafflestudio.team8server.syncwithsite.service.SyncWithSiteService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime

class CourseSyncServiceTest {
    private val cartSnapshotService = mock(CourseCartSnapshotService::class.java)
    private val cartSnapshotRunRepository = mock(CourseCartSnapshotRunRepository::class.java)
    private val excelClient = mock(SugangCourseExcelClient::class.java)
    private val runRepository = mock(CourseSyncRunRepository::class.java)
    private val service = createService(cartSnapshotService)

    private fun createService(snapshotService: CourseCartSnapshotService) =
        CourseSyncService(
            props = CourseSyncProperties(),
            courseService = mock(CourseService::class.java),
            courseCartSnapshotService = snapshotService,
            courseCartSnapshotRunRepository = cartSnapshotRunRepository,
            excelClient = excelClient,
            settingRepository = mock(CourseSyncSettingRepository::class.java),
            runRepository = runRepository,
            syncWithSiteService = mock(SyncWithSiteService::class.java),
        )

    @Test
    fun `automatic sync runs for a new term or inside the active window`() {
        val target = target()
        val beforeSchedule = LocalDateTime.of(2026, 7, 31, 23, 59)
        val duringSchedule = LocalDateTime.of(2026, 8, 15, 0, 0)

        `when`(runRepository.existsByStatusAndYearAndSemester(CourseSyncRunStatus.SUCCESS, 2026, Semester.FALL))
            .thenReturn(false, true, true)

        assertThat(service.shouldRunAutomatically(target, beforeSchedule)).isTrue()
        assertThat(service.shouldRunAutomatically(target, beforeSchedule)).isFalse()
        assertThat(service.shouldRunAutomatically(target, duringSchedule)).isTrue()
    }

    @Test
    fun `cart snapshot waits for change period and only records a successful capture`() {
        val target = target()
        val beforeChange = LocalDateTime.of(2026, 8, 27, 23, 59)
        val afterChange = LocalDateTime.of(2026, 8, 28, 9, 0)

        assertThat(service.captureCartSnapshotIfDue(target, beforeChange)).isNull()
        verify(excelClient, never()).downloadExcel(2026, Semester.FALL)

        `when`(cartSnapshotRunRepository.existsByYearAndSemester(2026, Semester.FALL)).thenReturn(false)
        `when`(excelClient.downloadExcel(2026, Semester.FALL)).thenReturn(byteArrayOf(1))
        val successfulSnapshotService =
            object : CourseCartSnapshotService(
                mock(CourseRepository::class.java),
                mock(CourseCartSnapshotRepository::class.java),
                mock(CourseExcelParser::class.java),
            ) {
                override fun capture(
                    year: Int,
                    semester: Semester,
                    file: MultipartFile,
                ): Int = 12
            }

        assertThat(createService(successfulSnapshotService).captureCartSnapshotIfDue(target, afterChange)).isEqualTo(12)
        val runCaptor = ArgumentCaptor.forClass(CourseCartSnapshotRun::class.java)
        verify(cartSnapshotRunRepository).save(runCaptor.capture())
        assertThat(runCaptor.value.capturedAt).isEqualTo(afterChange)
    }

    @Test
    fun `cart snapshot is skipped when the term was already captured`() {
        `when`(cartSnapshotRunRepository.existsByYearAndSemester(2026, Semester.FALL)).thenReturn(true)

        assertThat(service.captureCartSnapshotIfDue(target(), LocalDateTime.of(2026, 8, 28, 9, 0))).isNull()
        verify(excelClient, never()).downloadExcel(2026, Semester.FALL)
    }

    @Test
    fun `failed cart snapshot does not record the term as captured`() {
        val failingSnapshotService =
            object : CourseCartSnapshotService(
                mock(CourseRepository::class.java),
                mock(CourseCartSnapshotRepository::class.java),
                mock(CourseExcelParser::class.java),
            ) {
                override fun capture(
                    year: Int,
                    semester: Semester,
                    file: MultipartFile,
                ): Int = throw IllegalStateException("capture failed")
            }
        `when`(cartSnapshotRunRepository.existsByYearAndSemester(2026, Semester.FALL)).thenReturn(false)
        `when`(excelClient.downloadExcel(2026, Semester.FALL)).thenReturn(byteArrayOf(1))

        assertThatThrownBy {
            createService(failingSnapshotService).captureCartSnapshotIfDue(
                target(),
                LocalDateTime.of(2026, 8, 28, 9, 0),
            )
        }.isInstanceOf(IllegalStateException::class.java)
        assertThat(org.mockito.Mockito.mockingDetails(cartSnapshotRunRepository).invocations.map { it.method.name })
            .doesNotContain("save")
    }

    private fun target() =
        ParsedSugangPeriod(
            year = 2026,
            semester = Semester.FALL,
            firstScheduleAt = LocalDateTime.of(2026, 8, 1, 9, 0),
            firstCourseChangeAt = LocalDateTime.of(2026, 8, 28, 9, 0),
            lastCourseChangeAt = LocalDateTime.of(2026, 9, 7, 23, 59),
        )
}

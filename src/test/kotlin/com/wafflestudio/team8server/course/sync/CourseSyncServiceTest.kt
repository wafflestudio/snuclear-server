package com.wafflestudio.team8server.course.sync

import com.wafflestudio.team8server.config.EnrollmentPeriodProperties
import com.wafflestudio.team8server.course.dto.CourseSearchRequest
import com.wafflestudio.team8server.course.model.Course
import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.course.repository.CourseCartSnapshotRepository
import com.wafflestudio.team8server.course.repository.CourseCartSnapshotRunRepository
import com.wafflestudio.team8server.course.repository.CourseRepository
import com.wafflestudio.team8server.course.service.CourseCartSnapshotService
import com.wafflestudio.team8server.course.service.CourseExcelParser
import com.wafflestudio.team8server.course.service.CourseService
import com.wafflestudio.team8server.course.sync.model.CourseSyncRunStatus
import com.wafflestudio.team8server.course.sync.repository.CourseSyncRunRepository
import com.wafflestudio.team8server.course.sync.repository.CourseSyncSettingRepository
import com.wafflestudio.team8server.syncwithsite.dto.SugangPeriodResponse
import com.wafflestudio.team8server.syncwithsite.service.ParsedSugangPeriod
import com.wafflestudio.team8server.syncwithsite.service.SyncWithSiteService
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime

class CourseSyncServiceTest {
    private val cartSnapshotService = mock(CourseCartSnapshotService::class.java)
    private var insertClaimResult = 1
    private var reclaimClaimResult = 0
    private val cartSnapshotRunRepository =
        mock(CourseCartSnapshotRunRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "insertPendingClaim" -> insertClaimResult
                "reclaim" -> reclaimClaimResult
                "complete", "fail" -> 1
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        }
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
            transactionManager = transactionManager(),
        )

    @Test
    fun `automatic sync runs for a new term or inside the active window until the retry deadline`() {
        val target = target()
        val beforeSchedule = LocalDateTime.of(2026, 7, 31, 23, 59)
        val duringSchedule = LocalDateTime.of(2026, 8, 15, 0, 0)
        val afterDeadline = LocalDateTime.of(2026, 9, 9, 0, 0)

        `when`(runRepository.existsByStatusAndYearAndSemester(CourseSyncRunStatus.SUCCESS, 2026, Semester.FALL))
            .thenReturn(false, true, true)

        assertThat(service.shouldRunAutomatically(target, beforeSchedule)).isTrue()
        assertThat(service.shouldRunAutomatically(target, beforeSchedule)).isFalse()
        assertThat(service.shouldRunAutomatically(target, duringSchedule)).isTrue()
        assertThat(service.shouldRunAutomatically(target, afterDeadline)).isFalse()
    }

    @Test
    fun `cart snapshot waits for change period and only records a successful capture`() {
        val target = target()
        val beforeChange = LocalDateTime.of(2026, 8, 27, 23, 59)
        val afterChange = LocalDateTime.of(2026, 8, 28, 9, 0)

        assertThat(service.captureCartSnapshotIfDue(target, beforeChange)).isNull()
        verify(excelClient, never()).downloadExcel(2026, Semester.FALL)

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
        assertThat(mockingDetails(cartSnapshotRunRepository).invocations.map { it.method.name })
            .contains("insertPendingClaim", "complete")
    }

    @Test
    fun `cart snapshot is skipped when the term was already captured`() {
        insertClaimResult = 0
        reclaimClaimResult = 0

        assertThat(service.captureCartSnapshotIfDue(target(), LocalDateTime.of(2026, 8, 28, 9, 0))).isNull()
        verify(excelClient, never()).downloadExcel(2026, Semester.FALL)
    }

    @Test
    fun `automatic retries stop after the change period grace window`() {
        val afterDeadline = LocalDateTime.of(2026, 9, 9, 0, 0)

        assertThat(service.shouldRunAutomatically(target(), afterDeadline)).isFalse()
        assertThat(service.captureCartSnapshotIfDue(target(), afterDeadline)).isNull()
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
        `when`(excelClient.downloadExcel(2026, Semester.FALL)).thenReturn(byteArrayOf(1))

        assertThatThrownBy {
            createService(failingSnapshotService).captureCartSnapshotIfDue(
                target(),
                LocalDateTime.of(2026, 8, 28, 9, 0),
            )
        }.isInstanceOf(IllegalStateException::class.java)
        assertThat(mockingDetails(cartSnapshotRunRepository).invocations.map { it.method.name })
            .contains("fail")
            .doesNotContain("complete")
    }

    private fun target() =
        ParsedSugangPeriod(
            year = 2026,
            semester = Semester.FALL,
            firstScheduleAt = LocalDateTime.of(2026, 8, 1, 9, 0),
            firstCourseChangeAt = LocalDateTime.of(2026, 8, 28, 9, 0),
            lastCourseChangeAt = LocalDateTime.of(2026, 9, 7, 23, 59),
        )

    private fun transactionManager() =
        object : PlatformTransactionManager {
            override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()

            override fun commit(status: TransactionStatus) = Unit

            override fun rollback(status: TransactionStatus) = Unit
        }
}

class CourseServiceSearchTargetTest {
    private val defaultAnswer = org.mockito.Mockito.RETURNS_DEFAULTS

    @Test
    fun `search prefers the saved schedule term`() {
        assertThat(
            searchFilterValues(
                period = period("2026학년도 2학기 수강신청 기간안내"),
                defaultTarget = CourseSyncProperties.DefaultTarget(2025, Semester.SPRING),
            ),
        ).contains(2026, Semester.FALL)
    }

    @Test
    fun `search falls back to the configured term without a saved schedule`() {
        assertThat(
            searchFilterValues(
                period = null,
                defaultTarget = CourseSyncProperties.DefaultTarget(2025, Semester.SPRING),
            ),
        ).contains(2025, Semester.SPRING)
    }

    @Suppress("UNCHECKED_CAST")
    private fun searchFilterValues(
        period: SugangPeriodResponse?,
        defaultTarget: CourseSyncProperties.DefaultTarget,
    ): List<Any?> {
        val repository =
            mock(CourseRepository::class.java) { invocation ->
                if (
                    invocation.method.name == "findAll" &&
                    invocation.arguments.size == 2 &&
                    invocation.arguments[1] is Pageable
                ) {
                    Page.empty<Course>(invocation.arguments[1] as Pageable)
                } else {
                    defaultAnswer.answer(invocation)
                }
            }
        val syncWithSiteService =
            mock(SyncWithSiteService::class.java) { invocation ->
                if (invocation.method.name == "getSavedSugangPeriod") {
                    period
                } else {
                    defaultAnswer.answer(invocation)
                }
            }
        val courseService =
            CourseService(
                repository,
                mock(CourseExcelParser::class.java),
                mock(EntityManager::class.java),
                500,
                EnrollmentPeriodProperties(),
                CourseSyncProperties(defaultTarget = defaultTarget),
                syncWithSiteService,
            )

        courseService.search(CourseSearchRequest())

        val specification =
            mockingDetails(repository)
                .invocations
                .single { it.method.name == "findAll" && it.arguments.size == 2 }
                .arguments[0] as Specification<Course>
        val root = mock(Root::class.java) as Root<Course>
        val query = mock(CriteriaQuery::class.java) as CriteriaQuery<*>
        val criteriaBuilder =
            mock(CriteriaBuilder::class.java) { invocation ->
                when (invocation.method.name) {
                    "equal", "and" -> mock(Predicate::class.java)
                    else -> defaultAnswer.answer(invocation)
                }
            }
        specification.toPredicate(root, query, criteriaBuilder)
        return mockingDetails(criteriaBuilder)
            .invocations
            .filter { it.method.name == "equal" }
            .map { it.arguments[1] }
    }

    private fun period(header: String) = SugangPeriodResponse(header, emptyList())
}

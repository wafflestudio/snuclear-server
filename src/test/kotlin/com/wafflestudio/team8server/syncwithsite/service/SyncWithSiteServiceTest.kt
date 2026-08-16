package com.wafflestudio.team8server.syncwithsite.service

import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.syncwithsite.dto.SugangPeriodDto
import com.wafflestudio.team8server.syncwithsite.dto.SugangPeriodResponse
import com.wafflestudio.team8server.syncwithsite.model.SugangPeriodSnapshot
import com.wafflestudio.team8server.syncwithsite.model.SyncWithSiteRun
import com.wafflestudio.team8server.syncwithsite.model.SyncWithSiteRunStatus
import com.wafflestudio.team8server.syncwithsite.repository.SugangPeriodSnapshotRepository
import com.wafflestudio.team8server.syncwithsite.repository.SyncWithSiteRunRepository
import com.wafflestudio.team8server.syncwithsite.repository.SyncWithSiteSettingRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.LocalDateTime

class SyncWithSiteServiceTest {
    private val settingRepository = mock(SyncWithSiteSettingRepository::class.java)
    private val runRepository = mock(SyncWithSiteRunRepository::class.java)
    private val snapshotRepository = mock(SugangPeriodSnapshotRepository::class.java)
    private val objectMapper: ObjectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    @Test
    fun `saved period uses the canonical snapshot for the latest term`() {
        val latest = period("수강신청변경")
        val canonical = period("장바구니", "수강신청변경")
        `when`(runRepository.findFirstByStatusOrderByStartedAtDesc(SyncWithSiteRunStatus.SUCCESS))
            .thenReturn(run(latest))
        `when`(snapshotRepository.findByYearAndSemester(2026, Semester.FALL))
            .thenReturn(
                SugangPeriodSnapshot(
                    year = 2026,
                    semester = Semester.FALL,
                    dumpedData = objectMapper.writeValueAsString(canonical),
                    updatedAt = LocalDateTime.now(),
                ),
            )

        assertThat(service().getSavedSugangPeriod()).isEqualTo(canonical)
    }

    @Test
    fun `first snapshot rebuilds the term from successful runs in chronological order`() {
        val full = period("장바구니", "최초신청", "수강신청변경")
        val current = period("최초신청", "수강신청변경")
        `when`(snapshotRepository.findByYearAndSemester(2026, Semester.FALL)).thenReturn(null)
        `when`(runRepository.findAllByStatusOrderByStartedAtAsc(SyncWithSiteRunStatus.SUCCESS))
            .thenReturn(listOf(run(full), run(current)))

        service(current).runOnce()

        val captor = ArgumentCaptor.forClass(SugangPeriodSnapshot::class.java)
        verify(snapshotRepository).save(captor.capture())
        val saved = objectMapper.readValue(captor.value.dumpedData, SugangPeriodResponse::class.java)
        assertThat(saved.body.map { it.category }).containsExactly("장바구니", "최초신청", "수강신청변경")
    }

    @Test
    fun `later crawl updates only the changed canonical tail`() {
        val canonical = period("장바구니", "최초신청", "수강신청변경")
        val crawled = period("최초신청", "수강신청변경(정정)")
        val snapshot =
            SugangPeriodSnapshot(
                year = 2026,
                semester = Semester.FALL,
                dumpedData = objectMapper.writeValueAsString(canonical),
                updatedAt = LocalDateTime.now(),
            )
        `when`(snapshotRepository.findByYearAndSemester(2026, Semester.FALL)).thenReturn(snapshot)

        service(crawled).runOnce()

        verify(snapshotRepository).save(snapshot)
        val saved = objectMapper.readValue(snapshot.dumpedData, SugangPeriodResponse::class.java)
        assertThat(saved.body.map { it.category }).containsExactly("장바구니", "최초신청", "수강신청변경(정정)")
    }

    @Test
    fun `unchanged suffix does not rewrite the canonical snapshot`() {
        val canonical = period("장바구니", "최초신청", "수강신청변경")
        val snapshot =
            SugangPeriodSnapshot(
                year = 2026,
                semester = Semester.FALL,
                dumpedData = objectMapper.writeValueAsString(canonical),
                updatedAt = LocalDateTime.now(),
            )
        `when`(snapshotRepository.findByYearAndSemester(2026, Semester.FALL)).thenReturn(snapshot)

        service(period("최초신청", "수강신청변경")).runOnce()

        assertThat(mockingDetails(snapshotRepository).invocations.map { it.method.name })
            .doesNotContain("save")
    }

    @Test
    fun `snapshot failure rolls back success before recording failure`() {
        val crawled = period("장바구니", "수강신청변경")
        val transactionManager = RecordingTransactionManager()
        val localRunRepository =
            mock(SyncWithSiteRunRepository::class.java) { invocation ->
                when (invocation.method.name) {
                    "save" -> invocation.arguments[0]
                    "findAllByStatusOrderByStartedAtAsc" -> listOf(run(crawled))
                    else -> RETURNS_DEFAULTS.answer(invocation)
                }
            }
        val failingSnapshotRepository =
            mock(SugangPeriodSnapshotRepository::class.java) { invocation ->
                when (invocation.method.name) {
                    "save" -> throw IllegalStateException("snapshot failed")
                    else -> RETURNS_DEFAULTS.answer(invocation)
                }
            }
        val service =
            object : SyncWithSiteService(
                settingRepository,
                localRunRepository,
                failingSnapshotRepository,
                objectMapper,
                transactionManager,
            ) {
                override fun crawlSugangPeriod(): SugangPeriodResponse = crawled
            }

        assertThatThrownBy { service.runOnce() }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(transactionManager.rollbacks).isEqualTo(1)
        assertThat(
            mockingDetails(localRunRepository)
                .invocations
                .filter { it.method.name == "save" }
                .map { (it.arguments[0] as SyncWithSiteRun).status },
        ).containsExactly(SyncWithSiteRunStatus.SUCCESS, SyncWithSiteRunStatus.FAILED)
    }

    private fun service(crawled: SugangPeriodResponse? = null): SyncWithSiteService =
        if (crawled == null) {
            SyncWithSiteService(settingRepository, runRepository, snapshotRepository, objectMapper, transactionManager())
        } else {
            object : SyncWithSiteService(
                settingRepository,
                runRepository,
                snapshotRepository,
                objectMapper,
                transactionManager(),
            ) {
                override fun crawlSugangPeriod(): SugangPeriodResponse = crawled
            }
        }

    private fun transactionManager(): PlatformTransactionManager = RecordingTransactionManager()

    private fun period(vararg categories: String): SugangPeriodResponse =
        SugangPeriodResponse(
            header = "2026학년도 2학기 수강신청 기간안내",
            body =
                categories.map {
                    SugangPeriodDto(
                        category = it,
                        date = "2026-08-28(금)",
                        time = "09:00 ~ 18:30",
                        remark = "전체 학생",
                    )
                },
        )

    private fun run(period: SugangPeriodResponse): SyncWithSiteRun {
        val now = LocalDateTime.now()
        return SyncWithSiteRun(
            status = SyncWithSiteRunStatus.SUCCESS,
            startedAt = now,
            finishedAt = now,
            dumpedData = objectMapper.writeValueAsString(period),
        )
    }

    private class RecordingTransactionManager : PlatformTransactionManager {
        var rollbacks = 0

        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()

        override fun commit(status: TransactionStatus) = Unit

        override fun rollback(status: TransactionStatus) {
            rollbacks++
        }
    }
}

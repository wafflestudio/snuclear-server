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
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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

    private fun service(crawled: SugangPeriodResponse? = null): SyncWithSiteService =
        if (crawled == null) {
            SyncWithSiteService(settingRepository, runRepository, snapshotRepository, objectMapper)
        } else {
            object : SyncWithSiteService(settingRepository, runRepository, snapshotRepository, objectMapper) {
                override fun crawlSugangPeriod(): SugangPeriodResponse = crawled
            }
        }

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
}

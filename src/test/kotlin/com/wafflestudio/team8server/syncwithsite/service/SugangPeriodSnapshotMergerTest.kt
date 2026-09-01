package com.wafflestudio.team8server.syncwithsite.service

import com.wafflestudio.team8server.syncwithsite.dto.SugangPeriodDto
import com.wafflestudio.team8server.syncwithsite.dto.SugangPeriodResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SugangPeriodSnapshotMergerTest {
    @Test
    fun `keeps saved rows when the site only removes rows from the front`() {
        val saved = period("장바구니", "최초신청", "변경")
        val crawled = period("최초신청", "변경")

        assertThat(SugangPeriodSnapshotMerger.merge(saved, crawled)).isEqualTo(saved)
    }

    @Test
    fun `replaces the active tail when the site changes it`() {
        val saved = period("장바구니", "최초신청", "변경")
        val crawled = period("최초신청", "변경(정정)")

        assertThat(SugangPeriodSnapshotMerger.merge(saved, crawled).body.map { it.category })
            .containsExactly("장바구니", "최초신청", "변경(정정)")
    }

    private fun period(vararg categories: String): SugangPeriodResponse =
        SugangPeriodResponse(
            header = "2026학년도 2학기 수강신청 기간안내",
            body = categories.map { SugangPeriodDto(it, "2026-08-28", "09:00", "전체 학생") },
        )
}

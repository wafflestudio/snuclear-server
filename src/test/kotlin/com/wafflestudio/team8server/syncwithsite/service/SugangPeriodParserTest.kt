package com.wafflestudio.team8server.syncwithsite.service

import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.syncwithsite.dto.SugangPeriodDto
import com.wafflestudio.team8server.syncwithsite.dto.SugangPeriodResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SugangPeriodParserTest {
    @Test
    fun `parses fall term and first course change time`() {
        val parsed =
            SugangPeriodParser.parse(
                SugangPeriodResponse(
                    header = "2026학년도 2학기 수강신청 기간안내",
                    body =
                        listOf(
                            SugangPeriodDto(
                                "수강신청변경",
                                "2026-09-02(수) ~ 2026-09-02(수)",
                                "09 : 00 ~ 18 : 30",
                                "전체 학생",
                            ),
                            SugangPeriodDto(
                                "수강신청변경(개강전)",
                                "2026-08-28(금) ~ 2026-08-28(금)",
                                "09 : 00 ~ 18 : 30",
                                "전체 학생",
                            ),
                        ),
                ),
            )

        assertThat(parsed).isEqualTo(
            ParsedSugangPeriod(2026, Semester.FALL, LocalDateTime.of(2026, 8, 28, 9, 0)),
        )
    }

    @Test
    fun `parses seasonal terms and skips unknown headers`() {
        val summer =
            SugangPeriodParser.parse(
                SugangPeriodResponse("2026학년도 여름학기 수강신청 기간안내", emptyList()),
            )
        val unknown = SugangPeriodParser.parse(SugangPeriodResponse("수강신청 기간안내", emptyList()))

        assertThat(summer?.semester).isEqualTo(Semester.SUMMER)
        assertThat(unknown).isNull()
    }
}

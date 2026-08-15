package com.wafflestudio.team8server.syncwithsite.service

import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.syncwithsite.dto.SugangPeriodResponse
import java.time.LocalDateTime

object SugangPeriodParser {
    private val termPattern =
        Regex("""(\d{4})\s*학년도\s*(?:(1|2)\s*학기|(여름|겨울)\s*학기)""")
    private val datePattern = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val timePattern = Regex("""(\d{1,2})\s*:\s*(\d{2})""")

    fun parse(period: SugangPeriodResponse): ParsedSugangPeriod? {
        val match = termPattern.find(period.header) ?: return null
        val semester =
            when (match.groupValues[2]) {
                "1" -> Semester.SPRING
                "2" -> Semester.FALL
                else ->
                    when (match.groupValues[3]) {
                        "여름" -> Semester.SUMMER
                        "겨울" -> Semester.WINTER
                        else -> return null
                    }
            }

        return ParsedSugangPeriod(
            year = match.groupValues[1].toInt(),
            semester = semester,
            firstCourseChangeAt =
                period.body
                    .asSequence()
                    .filter { it.category.contains("수강신청변경") }
                    .mapNotNull { row ->
                        val date = datePattern.find(row.date)?.groupValues ?: return@mapNotNull null
                        val time = timePattern.find(row.time)?.groupValues ?: return@mapNotNull null
                        LocalDateTime.of(
                            date[1].toInt(),
                            date[2].toInt(),
                            date[3].toInt(),
                            time[1].toInt(),
                            time[2].toInt(),
                        )
                    }.minOrNull(),
        )
    }
}

data class ParsedSugangPeriod(
    val year: Int,
    val semester: Semester,
    val firstCourseChangeAt: LocalDateTime?,
)

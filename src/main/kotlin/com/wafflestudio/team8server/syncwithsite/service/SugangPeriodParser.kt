package com.wafflestudio.team8server.syncwithsite.service

import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.syncwithsite.dto.SugangPeriodDto
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

        val rows = period.body.mapNotNull { row -> parseRange(row)?.let { row to it } }
        val courseChanges = rows.filter { (row) -> row.category.contains("수강신청변경") }

        return ParsedSugangPeriod(
            year = match.groupValues[1].toInt(),
            semester = semester,
            firstScheduleAt = rows.minOfOrNull { (_, range) -> range.start },
            firstCourseChangeAt = courseChanges.minOfOrNull { (_, range) -> range.start },
            lastCourseChangeAt = courseChanges.maxOfOrNull { (_, range) -> range.end },
        )
    }

    private fun parseRange(row: SugangPeriodDto): PeriodRange? {
        val dates = datePattern.findAll(row.date).map { it.groupValues }.toList()
        val times = timePattern.findAll(row.time).map { it.groupValues }.toList()
        if (dates.isEmpty() || times.isEmpty()) return null

        fun at(
            date: List<String>,
            time: List<String>,
        ) = LocalDateTime.of(
            date[1].toInt(),
            date[2].toInt(),
            date[3].toInt(),
            time[1].toInt(),
            time[2].toInt(),
        )

        return PeriodRange(
            start = at(dates.first(), times.first()),
            end = at(dates.last(), times.last()),
        )
    }

    private data class PeriodRange(
        val start: LocalDateTime,
        val end: LocalDateTime,
    )
}

data class ParsedSugangPeriod(
    val year: Int,
    val semester: Semester,
    val firstScheduleAt: LocalDateTime?,
    val firstCourseChangeAt: LocalDateTime?,
    val lastCourseChangeAt: LocalDateTime?,
) {
    fun isCourseSyncActiveAt(now: LocalDateTime): Boolean =
        firstScheduleAt?.let { start -> !now.isBefore(start) } == true &&
            lastCourseChangeAt?.let { end -> !now.isAfter(end) } == true
}

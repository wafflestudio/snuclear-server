package com.wafflestudio.team8server.course.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.wafflestudio.team8server.common.exception.BadRequestException
import com.wafflestudio.team8server.course.model.Course
import com.wafflestudio.team8server.course.model.Semester
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

@Component
class CourseExcelParser {
    private companion object {
        const val HEADER_ROW_INDEX = 2
    }

    private val log = LoggerFactory.getLogger(CourseExcelParser::class.java)
    private val formatter = DataFormatter()
    private val objectMapper = ObjectMapper()

    fun parse(
        file: MultipartFile,
        year: Int,
        semester: Semester,
        onCourse: (Course) -> Unit,
    ): Int =
        parseWithCartCounts(file, year, semester) { parsed ->
            onCourse(parsed.course)
        }

    fun parseWithCartCounts(
        file: MultipartFile,
        year: Int,
        semester: Semester,
        onCourse: (ParsedCourseRow) -> Unit,
    ): Int {
        if (file.isEmpty) throw BadRequestException("빈 파일입니다.")

        requireXlsOrXlsx(file)

        var count = 0

        file.inputStream.use { input ->
            WorkbookFactory.create(input).use { workbook ->
                val sheet = workbook.getSheetAt(0) ?: return 0

                var headerIndex: Map<String, Int>? = null
                val rowIterator = sheet.iterator()

                while (rowIterator.hasNext()) {
                    val row = rowIterator.next()
                    val rowNum = row.rowNum

                    if (rowNum == HEADER_ROW_INDEX) {
                        headerIndex = buildHeaderIndex(row)
                        log.info("Detected excel headers: {}", headerIndex!!.keys)
                        continue
                    }

                    if (rowNum < HEADER_ROW_INDEX + 1) continue
                    if (headerIndex == null) continue
                    if (isRowEmpty(row)) continue

                    val parsed =
                        parseRow(
                            row = row,
                            headerIndex = headerIndex!!,
                            year = year,
                            semester = semester,
                            rowNumForLog = rowNum + 1,
                        ) ?: continue

                    onCourse(parsed)
                    count++
                }
            }
        }

        return count
    }

    private fun parseRow(
        row: Row,
        headerIndex: Map<String, Int>,
        year: Int,
        semester: Semester,
        rowNumForLog: Int,
    ): ParsedCourseRow? {
        val courseNumber = stringCell(row, headerIndex, "교과목번호")
        val lectureNumber = stringCell(row, headerIndex, "강좌번호")
        val courseTitle = stringCell(row, headerIndex, "교과목명")
        val quotaPair = parseQuotaCell(row, headerIndex)
        val quota = quotaPair?.first
        val freshmanQuota = quotaPair?.second
        val registrationCount = intCell(row, headerIndex, "수강신청인원")

        if (courseNumber.isNullOrBlank() || lectureNumber.isNullOrBlank() || courseTitle.isNullOrBlank() || quota == null) {
            log.debug(
                "Skip row {} due to missing required fields (courseNumber={}, lectureNumber={}, courseTitle={}, quota={})",
                rowNumForLog,
                courseNumber,
                lectureNumber,
                courseTitle,
                quota,
            )
            return null
        }

        val academicCourse = stringCell(row, headerIndex, "이수과정")
        val academicYear = stringCell(row, headerIndex, "학년")
        val classification = stringCell(row, headerIndex, "교과구분")
        val college = stringCell(row, headerIndex, "개설대학")
        val department = stringCell(row, headerIndex, "개설학과")
        val credit = intCell(row, headerIndex, "학점")
        val instructor = stringCell(row, headerIndex, "주담당교수")
        val placeRaw = stringCell(row, headerIndex, "강의실(동-호)(#연건, *평창)")
        val timeRaw = stringCell(row, headerIndex, "수업교시")

        val place = placeRaw?.trim()?.takeIf { it.isNotBlank() }
        val time = timeRaw?.trim()?.takeIf { it.isNotBlank() }

        val placeAndTime =
            if (place == null && time == null) {
                null
            } else {
                objectMapper.writeValueAsString(PlaceAndTimePayload(place = place, time = time))
            }

        return ParsedCourseRow(
            course =
                Course(
                    year = year,
                    semester = semester,
                    classification = classification,
                    college = college,
                    department = department,
                    academicCourse = academicCourse,
                    academicYear = academicYear,
                    courseNumber = courseNumber.trim(),
                    lectureNumber = lectureNumber.trim(),
                    courseTitle = courseTitle.trim(),
                    credit = credit,
                    instructor = instructor,
                    placeAndTime = placeAndTime,
                    quota = quota,
                    freshmanQuota = freshmanQuota,
                    registrationCount = registrationCount,
                ),
            regularCartCount = intCell(row, headerIndex, "재학생장바구니"),
            freshmanCartCount = intCell(row, headerIndex, "신입생장바구니신청"),
        )
    }

    private fun buildHeaderIndex(headerRow: Row): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (i in headerRow.firstCellNum.toInt()..headerRow.lastCellNum.toInt()) {
            if (i < 0) continue
            val cell = headerRow.getCell(i) ?: continue
            val name = formatter.formatCellValue(cell).trim()
            if (name.isNotEmpty()) {
                map[name] = i
            }
        }
        return map
    }

    private fun stringCell(
        row: Row,
        headerIndex: Map<String, Int>,
        header: String,
    ): String? {
        val idx = headerIndex[header] ?: return null
        val cell = row.getCell(idx) ?: return null
        val value = formatter.formatCellValue(cell).trim()
        return value.ifEmpty { null }
    }

    private fun intCell(
        row: Row,
        headerIndex: Map<String, Int>,
        header: String,
    ): Int? {
        val raw = stringCell(row, headerIndex, header) ?: return null
        val normalized = raw.removeSuffix(".0").trim()
        return normalized.toIntOrNull()
    }

    private fun isRowEmpty(row: Row): Boolean {
        val first = row.firstCellNum.toInt()
        val last = row.lastCellNum.toInt()
        if (first < 0 || last < 0) return true

        for (i in first..last) {
            val cell = row.getCell(i) ?: continue
            val value = formatter.formatCellValue(cell).trim()
            if (value.isNotEmpty()) return false
        }
        return true
    }

    private fun parseQuotaCell(
        row: Row,
        headerIndex: Map<String, Int>,
    ): Pair<Int, Int?>? {
        val raw = stringCell(row, headerIndex, "정원") ?: return null

        val trimmed = raw.trim()
        val totalMatch = Regex("""^(\d+)""").find(trimmed)
        val enrolledMatch = Regex("""\((\d+)\)""").find(trimmed)

        val total =
            totalMatch?.groupValues?.get(1)?.toIntOrNull()
                ?: return null

        val enrolled = enrolledMatch?.groupValues?.get(1)?.toIntOrNull()

        val freshmanQuota =
            if (enrolled != null) {
                total - enrolled
            } else {
                null
            }

        return total to freshmanQuota
    }

    private fun requireXlsOrXlsx(file: MultipartFile) {
        val name = file.originalFilename?.lowercase()
        if (name == null || (!name.endsWith(".xls") && !name.endsWith(".xlsx"))) {
            throw BadRequestException(".xls 또는 .xlsx 파일만 지원합니다.")
        }
    }
}

data class PlaceAndTimePayload(
    val place: String?,
    val time: String?,
)

data class ParsedCourseRow(
    val course: Course,
    val regularCartCount: Int?,
    val freshmanCartCount: Int?,
)

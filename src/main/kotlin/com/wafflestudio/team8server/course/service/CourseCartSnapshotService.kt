package com.wafflestudio.team8server.course.service

import com.wafflestudio.team8server.common.exception.BadRequestException
import com.wafflestudio.team8server.course.model.Course
import com.wafflestudio.team8server.course.model.CourseCartSnapshot
import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.course.repository.CourseCartSnapshotRepository
import com.wafflestudio.team8server.course.repository.CourseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime

@Service
class CourseCartSnapshotService(
    private val courseRepository: CourseRepository,
    private val courseCartSnapshotRepository: CourseCartSnapshotRepository,
    private val courseExcelParser: CourseExcelParser,
) {
    @Transactional
    fun capture(
        year: Int,
        semester: Semester,
        file: MultipartFile,
    ): Int {
        val coursesByKey =
            courseRepository
                .findAllByYearAndSemester(year, semester)
                .associateBy { NaturalKey.of(it) }

        val cartCountsByCourseId = mutableMapOf<Long, CartCounts>()
        courseExcelParser.parseWithCartCounts(file, year, semester) { parsed ->
            val course =
                coursesByKey[NaturalKey.of(parsed.course)]
                    ?: throw BadRequestException(
                        "장바구니 스냅샷 대상 강의를 찾을 수 없습니다: " +
                            "${parsed.course.courseNumber}-${parsed.course.lectureNumber}",
                    )
            val regularCartCount =
                parsed.regularCartCount
                    ?: throw BadRequestException("재학생장바구니 값이 없습니다: ${course.courseNumber}-${course.lectureNumber}")
            val freshmanCartCount =
                parsed.freshmanCartCount
                    ?: throw BadRequestException("신입생장바구니신청 값이 없습니다: ${course.courseNumber}-${course.lectureNumber}")

            cartCountsByCourseId[requireNotNull(course.id)] =
                CartCounts(
                    course = course,
                    regularCartCount = regularCartCount,
                    freshmanCartCount = freshmanCartCount,
                )
        }

        if (cartCountsByCourseId.isEmpty()) {
            throw BadRequestException("장바구니 데이터가 없습니다.")
        }

        val existingByCourseId =
            courseCartSnapshotRepository
                .findAllByCourseIdIn(cartCountsByCourseId.keys)
                .associateBy { requireNotNull(it.course.id) }
        val capturedAt = LocalDateTime.now()

        val snapshots =
            cartCountsByCourseId.map { (courseId, counts) ->
                existingByCourseId[courseId]?.apply {
                    regularCartCount = counts.regularCartCount
                    freshmanCartCount = counts.freshmanCartCount
                    this.capturedAt = capturedAt
                } ?: CourseCartSnapshot(
                    course = counts.course,
                    regularCartCount = counts.regularCartCount,
                    freshmanCartCount = counts.freshmanCartCount,
                    capturedAt = capturedAt,
                )
            }

        courseCartSnapshotRepository.saveAll(snapshots)
        return snapshots.size
    }

    private data class CartCounts(
        val course: Course,
        val regularCartCount: Int,
        val freshmanCartCount: Int,
    )

    private data class NaturalKey(
        val year: Int,
        val semester: Semester,
        val courseNumber: String,
        val lectureNumber: String,
    ) {
        companion object {
            fun of(course: Course): NaturalKey =
                NaturalKey(
                    year = course.year,
                    semester = course.semester,
                    courseNumber = course.courseNumber,
                    lectureNumber = course.lectureNumber,
                )
        }
    }
}

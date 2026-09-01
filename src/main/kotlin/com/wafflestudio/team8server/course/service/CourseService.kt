package com.wafflestudio.team8server.course.service

import com.wafflestudio.team8server.common.dto.PageInfo
import com.wafflestudio.team8server.config.EnrollmentPeriodProperties
import com.wafflestudio.team8server.course.dto.CourseDetailResponse
import com.wafflestudio.team8server.course.dto.CourseSearchRequest
import com.wafflestudio.team8server.course.dto.CourseSearchResponse
import com.wafflestudio.team8server.course.model.Course
import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.course.model.getDisplayedRegistrationCount
import com.wafflestudio.team8server.course.model.withId
import com.wafflestudio.team8server.course.repository.CourseRepository
import com.wafflestudio.team8server.course.repository.CourseSpecification
import com.wafflestudio.team8server.course.sync.CourseSyncProperties
import com.wafflestudio.team8server.syncwithsite.service.SugangPeriodParser
import com.wafflestudio.team8server.syncwithsite.service.SyncWithSiteService
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class CourseService(
    private val courseRepository: CourseRepository,
    private val courseExcelParser: CourseExcelParser,
    private val entityManager: EntityManager,
    @Value("\${course.import.batch-size:500}")
    private val courseImportBatchSize: Int,
    private val enrollmentPeriodProperties: EnrollmentPeriodProperties,
    private val courseSyncProperties: CourseSyncProperties,
    private val syncWithSiteService: SyncWithSiteService,
) {
    private val log = LoggerFactory.getLogger(CourseExcelParser::class.java)

    fun search(request: CourseSearchRequest): CourseSearchResponse {
        val pageable =
            PageRequest.of(
                request.page,
                request.size,
                Sort.by("courseTitle").ascending().and(Sort.by("id").ascending()),
            )

        val scheduleTarget =
            syncWithSiteService.getSavedSugangPeriod()?.let(SugangPeriodParser::parse)
        val defaultTarget = courseSyncProperties.defaultTarget

        val specification =
            CourseSpecification.search(
                query = request.query,
                courseNumber = request.courseNumber,
                academicCourse = request.academicCourse,
                academicYear = request.academicYear,
                college = request.college,
                department = request.department,
                classification = request.classification,
                year = scheduleTarget?.year ?: defaultTarget.year,
                semester = scheduleTarget?.semester ?: defaultTarget.semester,
            )

        val page = courseRepository.findAll(specification, pageable)

        val periodType = enrollmentPeriodProperties.type

        val items =
            page.content.map { course ->
                CourseDetailResponse(
                    id = course.id!!,
                    year = course.year,
                    semester = course.semester,
                    classification = course.classification,
                    college = course.college,
                    department = course.department,
                    academicCourse = course.academicCourse,
                    academicYear = course.academicYear,
                    courseNumber = course.courseNumber,
                    lectureNumber = course.lectureNumber,
                    courseTitle = course.courseTitle,
                    credit = course.credit,
                    instructor = course.instructor,
                    placeAndTime = course.placeAndTime,
                    quota = course.quota,
                    freshmanQuota = course.freshmanQuota,
                    registrationCount = course.getDisplayedRegistrationCount(periodType),
                )
            }

        return CourseSearchResponse(
            items = items,
            pageInfo =
                PageInfo(
                    page = page.number,
                    size = page.size,
                    totalElements = page.totalElements,
                    totalPages = page.totalPages,
                    hasNext = page.hasNext(),
                ),
        )
    }

    @Transactional
    fun import(
        year: Int,
        semester: Semester,
        file: MultipartFile,
    ) {
        val importId = UUID.randomUUID().toString().substring(0, 8)
        val t0 = System.currentTimeMillis()

        // 연관 데이터(장바구니/연습 등) 보존을 위해 delete 후 insert 방식이 아닌 upsert 방식으로 적재합니다.
        // 자연키 (year, semester, courseNumber, lectureNumber) 기준으로 기존 강의는 UPDATE, 신규 강의는 INSERT 합니다.
        val existingByKey =
            courseRepository
                .findAllByYearAndSemester(year, semester)
                .associateBy { NaturalKey.of(it) }
                .toMutableMap()

        val batchSize = courseImportBatchSize.coerceIn(50, 5000)
        val buffer = ArrayList<Course>(batchSize)
        var parsedCount = 0

        val parsedTotal =
            courseExcelParser.parse(file, year, semester) { parsed ->
                val key = NaturalKey.of(parsed)
                val existing = existingByKey[key]

                val upsertTarget =
                    if (existing == null) {
                        parsed
                    } else {
                        // 동일한 natural key의 row는 id를 유지하여 FK 연관 데이터가 보존되도록 합니다.
                        parsed.withId(existing.id!!)
                    }

                buffer.add(upsertTarget)
                parsedCount++

                if (buffer.size >= batchSize) {
                    courseRepository.saveAll(buffer)
                    courseRepository.flush()
                    entityManager.clear()

                    // 같은 파일에서 중복 row 등장 시 마지막 값을 우선으로 처리, 이후 처리에서도 일관성 유지하기 위해 map을 갱신합니다.
                    buffer.forEach { saved -> existingByKey[NaturalKey.of(saved)] = saved }
                    buffer.clear()

                    if (parsedCount % (batchSize * 5) == 0) {
                        log.info("[importId={}] parsed/upserted={}", importId, parsedCount)
                    }
                }
            }

        if (buffer.isNotEmpty()) {
            courseRepository.saveAll(buffer)
            courseRepository.flush()
            entityManager.clear()
            buffer.forEach { saved -> existingByKey[NaturalKey.of(saved)] = saved }
            buffer.clear()
        }

        val elapsed = System.currentTimeMillis() - t0
        if (parsedTotal == 0) {
            log.warn("[importId={}] No courses parsed from excel file (elapsed={}ms)", importId, elapsed)
            return
        }

        log.info(
            "[importId={}] Upserted {} courses for year={}, semester={} (elapsed={}ms)",
            importId,
            parsedTotal,
            year,
            semester,
            elapsed,
        )
    }

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

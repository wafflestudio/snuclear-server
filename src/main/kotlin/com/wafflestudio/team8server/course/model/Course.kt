package com.wafflestudio.team8server.course.model

import com.wafflestudio.team8server.config.EnrollmentPeriodType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlin.math.max

/**
 * 개설 강의 정보 엔티티
 */
@Entity
@Table(name = "courses")
class Course(
    @Column(name = "year", nullable = false)
    val year: Int,
    @Enumerated(EnumType.STRING)
    @Column(name = "semester", nullable = false)
    val semester: Semester,
    @Column(name = "classification", length = 50)
    val classification: String? = null,
    @Column(name = "college", length = 100)
    val college: String? = null,
    @Column(name = "department", length = 100)
    val department: String? = null,
    @Column(name = "academic_course", length = 50)
    val academicCourse: String? = null,
    @Column(name = "academic_year", length = 20)
    val academicYear: String? = null,
    @Column(name = "course_number", length = 20, nullable = false)
    val courseNumber: String,
    @Column(name = "lecture_number", length = 10, nullable = false)
    val lectureNumber: String,
    @Column(name = "course_title", nullable = false)
    val courseTitle: String,
    @Column(name = "course_title_normalized", insertable = false, updatable = false)
    val courseTitleNormalized: String? = null,
    @Column(name = "credit")
    val credit: Int? = null,
    @Column(name = "instructor", length = 100)
    val instructor: String? = null,
    @Column(name = "instructor_normalized", insertable = false, updatable = false)
    val instructorNormalized: String? = null,
    @Column(name = "place_and_time", columnDefinition = "JSON")
    val placeAndTime: String? = null,
    @Column(name = "quota", nullable = false)
    val quota: Int,
    @Column(name = "freshman_quota")
    val freshmanQuota: Int? = 0,
    @Column(name = "registration_count")
    val registrationCount: Int? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)

/**
 * upsert 과정에서 동일한 자연 키(year, semester, courseNumber, lectureNumber)를 가진 강의를 처리할 때
 * 기존 row의 PK를 유지하여 연관 데이터가 보존되도록 하기 위함
 */
fun Course.withId(id: Long): Course =
    Course(
        year = year,
        semester = semester,
        classification = classification,
        college = college,
        department = department,
        academicCourse = academicCourse,
        academicYear = academicYear,
        courseNumber = courseNumber,
        lectureNumber = lectureNumber,
        courseTitle = courseTitle,
        courseTitleNormalized = courseTitleNormalized,
        credit = credit,
        instructor = instructor,
        instructorNormalized = instructorNormalized,
        placeAndTime = placeAndTime,
        quota = quota,
        freshmanQuota = freshmanQuota,
        registrationCount = registrationCount,
        id = id,
    )

enum class Semester {
    SPRING,
    SUMMER,
    FALL,
    WINTER,
}

/**
 * 수강신청 기간 타입에 따른 유효 정원을 계산합니다.
 *
 * - REGULAR(재학생): quota - freshmanQuota
 * - FRESHMAN(신입생): quota - registrationCount
 */
fun Course.getEffectiveQuota(periodType: EnrollmentPeriodType): Int =
    when (periodType) {
        EnrollmentPeriodType.REGULAR -> max(0, quota - (freshmanQuota ?: 0))
        EnrollmentPeriodType.FRESHMAN -> max(0, quota - (registrationCount ?: 0))
    }

/**
 * 수강신청 기간 타입에 따라 프론트로 내려줄 표시용 수강신청인원을 계산합니다.
 *
 * - REGULAR(재학생): 항상 0
 * - FRESHMAN(신입생): registrationCount
 */
fun Course.getDisplayedRegistrationCount(periodType: EnrollmentPeriodType): Int =
    when (periodType) {
        EnrollmentPeriodType.REGULAR -> 0
        EnrollmentPeriodType.FRESHMAN -> (registrationCount ?: 0)
    }

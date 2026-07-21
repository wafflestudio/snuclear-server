package com.wafflestudio.team8server.course.repository

import com.wafflestudio.team8server.course.model.Course
import com.wafflestudio.team8server.course.model.Semester
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification

object CourseSpecification {
    private const val LIKE_ESCAPE = '\\'
    private val majorClassifications = listOf("전선", "전필", "전공선택", "전공필수")
    private val graduateAcademicCourses = listOf("석사", "박사", "석박사통합")

    fun search(
        query: String?,
        courseNumber: String?,
        academicCourse: String?,
        academicYear: Int?,
        college: String?,
        department: String?,
        classification: String?,
        year: Int?,
        semester: Semester?,
    ): Specification<Course> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            val normalizedQuery = CourseSearchNormalizer.normalize(query)
            if (normalizedQuery != null) {
                val titlePath = root.get<String>("courseTitle")
                val instructorPath = root.get<String>("instructor")
                val titleNormalizedPath = root.get<String>("courseTitleNormalized")
                val instructorNormalizedPath = root.get<String>("instructorNormalized")
                val departmentPath = root.get<String>("department")
                val collegePath = root.get<String>("college")
                val classificationPath = root.get<String>("classification")
                val academicCoursePath = root.get<String>("academicCourse")
                val academicYearPath = root.get<String>("academicYear")
                val courseNumberPath = root.get<String>("courseNumber")
                val lectureNumberPath = root.get<String>("lectureNumber")

                predicates +=
                    cb.and(
                        *normalizedQuery.keywords
                            .map { keyword ->
                                val orPredicates =
                                    mutableListOf(
                                        cb.like(titlePath, keyword.containsPattern, LIKE_ESCAPE),
                                        cb.like(instructorPath, keyword.containsPattern, LIKE_ESCAPE),
                                        cb.like(titleNormalizedPath, keyword.noSpaceContainsPattern, LIKE_ESCAPE),
                                        cb.like(instructorNormalizedPath, keyword.noSpaceContainsPattern, LIKE_ESCAPE),
                                        cb.like(departmentPath, keyword.containsPattern, LIKE_ESCAPE),
                                        cb.like(collegePath, keyword.containsPattern, LIKE_ESCAPE),
                                        cb.like(classificationPath, keyword.containsPattern, LIKE_ESCAPE),
                                        cb.like(academicCoursePath, keyword.containsPattern, LIKE_ESCAPE),
                                        cb.like(academicYearPath, keyword.containsPattern, LIKE_ESCAPE),
                                        cb.equal(courseNumberPath, keyword.raw),
                                        cb.equal(lectureNumberPath, keyword.raw),
                                    )

                                keyword.fuzzyPattern?.let { pattern ->
                                    orPredicates += cb.like(titlePath, pattern, LIKE_ESCAPE)
                                    orPredicates += cb.like(instructorPath, pattern, LIKE_ESCAPE)
                                    orPredicates += cb.like(departmentPath, pattern, LIKE_ESCAPE)
                                    orPredicates += cb.like(collegePath, pattern, LIKE_ESCAPE)
                                    orPredicates += cb.like(classificationPath, pattern, LIKE_ESCAPE)
                                    orPredicates += cb.like(academicCoursePath, pattern, LIKE_ESCAPE)
                                }

                                keyword.noSpaceFuzzyPattern?.let { pattern ->
                                    orPredicates += cb.like(titleNormalizedPath, pattern, LIKE_ESCAPE)
                                    orPredicates += cb.like(instructorNormalizedPath, pattern, LIKE_ESCAPE)
                                }

                                keyword.departmentPrefixFuzzyPattern?.let { pattern ->
                                    orPredicates += cb.like(departmentPath, pattern, LIKE_ESCAPE)
                                }

                                orPredicates += semanticPredicates(root, keyword.semanticRule)

                                cb.or(*orPredicates.toTypedArray())
                            }.toTypedArray(),
                    )
            }

            if (!courseNumber.isNullOrBlank()) {
                predicates +=
                    cb.equal(
                        root.get<String>("courseNumber"),
                        courseNumber.trim(),
                    )
            }

            if (!academicCourse.isNullOrBlank()) {
                predicates += cb.equal(root.get<String>("academicCourse"), academicCourse)
            }

            if (academicYear != null) {
                predicates += cb.equal(root.get<Int>("academicYear"), academicYear)
            }

            if (!college.isNullOrBlank()) {
                predicates += cb.equal(root.get<String>("college"), college)
            }

            if (!department.isNullOrBlank()) {
                predicates += cb.equal(root.get<String>("department"), department)
            }

            if (!classification.isNullOrBlank()) {
                predicates += cb.equal(root.get<String>("classification"), classification)
            }

            if (year != null) {
                predicates += cb.equal(root.get<Int>("year"), year)
            }

            if (semester != null) {
                predicates += cb.equal(root.get<Semester>("semester"), semester)
            }

            cb.and(*predicates.toTypedArray())
        }

    private fun semanticPredicates(
        root: Root<Course>,
        rule: SemanticCourseSearchRule?,
    ): List<Predicate> =
        when (rule) {
            SemanticCourseSearchRule.MAJOR ->
                listOf(root.get<String>("classification").`in`(majorClassifications))

            SemanticCourseSearchRule.GRADUATE ->
                listOf(root.get<String>("academicCourse").`in`(graduateAcademicCourses))

            SemanticCourseSearchRule.UNDERGRADUATE ->
                listOf(root.get<String>("academicCourse").`in`("학사", "학부"))

            null -> emptyList()
        }
}

package com.wafflestudio.team8server.course

import com.wafflestudio.team8server.course.model.Course
import com.wafflestudio.team8server.course.model.Semester

object CourseSearchTestData {
    fun dataStructuresWithSpace(): Course =
        course(
            courseNumber = "4190.311",
            courseTitle = "\uC790\uB8CC \uAD6C\uC870",
            department = "computer-science",
        )

    fun dataStructuresWithoutSpace(): Course =
        course(
            courseNumber = "4190.312",
            courseTitle = "\uC790\uB8CC\uAD6C\uC870",
            department = "computer-science",
        )

    fun physics2(): Course =
        course(
            college = "natural-sciences",
            department = "physics",
            academicYear = "2",
            courseNumber = "3341.202",
            courseTitle = "\uBB3C\uB9AC\uD5592",
        )

    fun departmentFilterTargets(): List<Course> =
        listOf(
            course(
                courseNumber = "4190.313",
                courseTitle = "\uC790\uB8CC \uAD6C\uC870",
                department = "computer-science",
            ),
            course(
                courseNumber = "4190.314",
                courseTitle = "\uC790\uB8CC \uAD6C\uC870",
                department = "electrical-engineering",
            ),
        )

    fun majorCourse(): Course =
        course(
            classification = "\uC804\uC120",
            courseNumber = "4190.315",
            courseTitle = "\uCEF4\uD4E8\uD130\uAD6C\uC870",
        )

    fun graduateCourse(): Course =
        course(
            academicCourse = "\uC11D\uC0AC",
            academicYear = null,
            courseNumber = "4190.316",
            courseTitle = "\uC5F0\uAD6C\uC138\uBBF8\uB098",
        )

    fun computerScienceDepartmentCourse(): Course =
        course(
            department = "\uCEF4\uD4E8\uD130\uACF5\uD559\uBD80",
            courseNumber = "4190.317",
            courseTitle = "\uC2DC\uC2A4\uD15C\uD504\uB85C\uADF8\uB798\uBC0D",
        )

    fun course(
        year: Int = 2026,
        semester: Semester = Semester.SPRING,
        classification: String = "major-elective",
        college: String = "engineering",
        department: String = "computer-science",
        academicCourse: String = "undergraduate",
        academicYear: String? = "3",
        courseNumber: String = "4190.310",
        lectureNumber: String = "001",
        courseTitle: String = "\uC790\uB8CC \uAD6C\uC870",
        credit: Int = 3,
        instructor: String = "professor",
        quota: Int = 80,
        freshmanQuota: Int? = null,
    ): Course =
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
            credit = credit,
            instructor = instructor,
            placeAndTime = null,
            quota = quota,
            freshmanQuota = freshmanQuota,
        )
}

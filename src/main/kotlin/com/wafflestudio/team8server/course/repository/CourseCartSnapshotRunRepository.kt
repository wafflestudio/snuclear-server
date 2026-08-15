package com.wafflestudio.team8server.course.repository

import com.wafflestudio.team8server.course.model.CourseCartSnapshotRun
import com.wafflestudio.team8server.course.model.Semester
import org.springframework.data.jpa.repository.JpaRepository

interface CourseCartSnapshotRunRepository : JpaRepository<CourseCartSnapshotRun, Long> {
    fun existsByYearAndSemester(
        year: Int,
        semester: Semester,
    ): Boolean
}

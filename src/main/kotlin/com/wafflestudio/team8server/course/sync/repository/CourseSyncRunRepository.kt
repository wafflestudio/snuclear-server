package com.wafflestudio.team8server.course.sync.repository

import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.course.sync.model.CourseSyncRun
import com.wafflestudio.team8server.course.sync.model.CourseSyncRunStatus
import org.springframework.data.jpa.repository.JpaRepository

interface CourseSyncRunRepository : JpaRepository<CourseSyncRun, Long> {
    fun findTopByOrderByStartedAtDesc(): CourseSyncRun?

    fun existsByStatusAndYearAndSemester(
        status: CourseSyncRunStatus,
        year: Int,
        semester: Semester,
    ): Boolean
}

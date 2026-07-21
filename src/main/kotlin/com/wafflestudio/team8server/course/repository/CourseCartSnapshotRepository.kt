package com.wafflestudio.team8server.course.repository

import com.wafflestudio.team8server.course.model.CourseCartSnapshot
import org.springframework.data.jpa.repository.JpaRepository

interface CourseCartSnapshotRepository : JpaRepository<CourseCartSnapshot, Long> {
    fun findAllByCourseIdIn(courseIds: Collection<Long>): List<CourseCartSnapshot>
}

package com.wafflestudio.team8server.course.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "course_cart_snapshot_runs")
class CourseCartSnapshotRun(
    @Column(nullable = false)
    val year: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val semester: Semester,
    @Column(name = "captured_at", nullable = false)
    val capturedAt: LocalDateTime,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)

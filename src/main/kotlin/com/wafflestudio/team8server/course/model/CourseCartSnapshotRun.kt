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
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CourseCartSnapshotRunStatus,
    @Column(name = "claim_token", nullable = false, length = 36)
    var claimToken: String,
    @Column(name = "claimed_at", nullable = false)
    var claimedAt: LocalDateTime,
    @Column(name = "captured_at")
    var capturedAt: LocalDateTime? = null,
    @Column(length = 500)
    var message: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)

enum class CourseCartSnapshotRunStatus {
    PENDING,
    SUCCESS,
    FAILED,
}

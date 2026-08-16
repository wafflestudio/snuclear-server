package com.wafflestudio.team8server.course.repository

import com.wafflestudio.team8server.course.model.CourseCartSnapshotRun
import com.wafflestudio.team8server.course.model.CourseCartSnapshotRunStatus
import com.wafflestudio.team8server.course.model.Semester
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface CourseCartSnapshotRunRepository : JpaRepository<CourseCartSnapshotRun, Long> {
    @Modifying
    @Transactional
    @Query(
        value =
            """
            INSERT IGNORE INTO course_cart_snapshot_runs
                (year, semester, status, claim_token, claimed_at)
            VALUES
                (:year, :semester, 'PENDING', :claimToken, :claimedAt)
            """,
        nativeQuery = true,
    )
    fun insertPendingClaim(
        year: Int,
        semester: String,
        claimToken: String,
        claimedAt: LocalDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
        """
        UPDATE CourseCartSnapshotRun r
        SET r.status = :pending,
            r.claimToken = :claimToken,
            r.claimedAt = :claimedAt,
            r.capturedAt = NULL,
            r.message = NULL
        WHERE r.year = :year
          AND r.semester = :semester
          AND (
              r.status = :failed
              OR (r.status = :pending AND r.claimedAt < :staleBefore)
          )
        """,
    )
    fun reclaim(
        year: Int,
        semester: Semester,
        claimToken: String,
        claimedAt: LocalDateTime,
        staleBefore: LocalDateTime,
        pending: CourseCartSnapshotRunStatus = CourseCartSnapshotRunStatus.PENDING,
        failed: CourseCartSnapshotRunStatus = CourseCartSnapshotRunStatus.FAILED,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
        """
        UPDATE CourseCartSnapshotRun r
        SET r.status = :success,
            r.capturedAt = :capturedAt,
            r.message = NULL
        WHERE r.year = :year
          AND r.semester = :semester
          AND r.status = :pending
          AND r.claimToken = :claimToken
        """,
    )
    fun complete(
        year: Int,
        semester: Semester,
        claimToken: String,
        capturedAt: LocalDateTime,
        pending: CourseCartSnapshotRunStatus,
        success: CourseCartSnapshotRunStatus,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
        """
        UPDATE CourseCartSnapshotRun r
        SET r.status = :failed,
            r.message = :message
        WHERE r.year = :year
          AND r.semester = :semester
          AND r.status = :pending
          AND r.claimToken = :claimToken
        """,
    )
    fun fail(
        year: Int,
        semester: Semester,
        claimToken: String,
        message: String,
        pending: CourseCartSnapshotRunStatus,
        failed: CourseCartSnapshotRunStatus,
    ): Int
}

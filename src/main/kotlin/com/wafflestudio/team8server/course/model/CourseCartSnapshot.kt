package com.wafflestudio.team8server.course.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "course_cart_snapshots")
class CourseCartSnapshot(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    val course: Course,
    @Column(name = "regular_cart_count", nullable = false)
    var regularCartCount: Int,
    @Column(name = "freshman_cart_count", nullable = false)
    var freshmanCartCount: Int,
    @Column(name = "captured_at", nullable = false)
    var capturedAt: LocalDateTime,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)

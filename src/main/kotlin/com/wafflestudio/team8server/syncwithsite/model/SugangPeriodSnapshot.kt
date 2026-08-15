package com.wafflestudio.team8server.syncwithsite.model

import com.wafflestudio.team8server.course.model.Semester
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
@Table(name = "sugang_period_snapshots")
class SugangPeriodSnapshot(
    @Column(nullable = false)
    val year: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val semester: Semester,
    @Column(name = "dumped_data", columnDefinition = "TEXT", nullable = false)
    var dumpedData: String,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)

package com.wafflestudio.team8server.tour.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "tour_config")
class TourConfig(
    @Id
    val id: Byte = SINGLETON_ID,
    @Column(nullable = false, columnDefinition = "DATETIME(6)")
    var publishedAt: LocalDateTime,
) {
    companion object {
        const val SINGLETON_ID: Byte = 1
    }
}

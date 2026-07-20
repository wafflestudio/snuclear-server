package com.wafflestudio.team8server.tour.repository

import com.wafflestudio.team8server.tour.model.TourConfig
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TourConfigRepository : JpaRepository<TourConfig, Byte> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT config FROM TourConfig config WHERE config.id = :id")
    fun findByIdForUpdate(
        @Param("id")
        id: Byte,
    ): TourConfig?
}

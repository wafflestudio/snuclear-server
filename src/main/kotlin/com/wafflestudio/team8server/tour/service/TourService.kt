package com.wafflestudio.team8server.tour.service

import com.wafflestudio.team8server.common.exception.ResourceNotFoundException
import com.wafflestudio.team8server.common.exception.TourVersionConflictException
import com.wafflestudio.team8server.common.time.TimeProvider
import com.wafflestudio.team8server.tour.dto.TourStatusResponse
import com.wafflestudio.team8server.tour.model.TourConfig
import com.wafflestudio.team8server.tour.repository.TourConfigRepository
import com.wafflestudio.team8server.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class TourService(
    private val tourConfigRepository: TourConfigRepository,
    private val userRepository: UserRepository,
    private val timeProvider: TimeProvider,
) {
    @Transactional(readOnly = true)
    fun getStatus(userId: Long): TourStatusResponse {
        val config = getCurrentConfig()
        val user = userRepository.findById(userId).orElseThrow { ResourceNotFoundException("유저를 찾을 수 없습니다") }
        val completedAt = user.tourCompletedAt
        val shouldShow =
            completedAt == null || completedAt.isBefore(config.publishedAt)

        return TourStatusResponse(
            shouldShow = shouldShow,
            publishedAt = config.publishedAt,
            completedAt = completedAt,
        )
    }

    @Transactional
    fun completeTour(
        userId: Long,
        requestedPublishedAt: LocalDateTime,
    ) {
        val config =
            tourConfigRepository.findByIdForUpdate(TourConfig.SINGLETON_ID)
                ?: throw IllegalStateException("tour_config의 행을 찾을 수 없습니다")

        if (requestedPublishedAt != config.publishedAt) {
            throw TourVersionConflictException()
        }

        val user =
            userRepository.findById(userId).orElseThrow { ResourceNotFoundException("유저를 찾을 수 없습니다") }

        user.tourCompletedAt = currentDateTime()
    }

    @Transactional
    fun publishTour(): LocalDateTime {
        val config =
            tourConfigRepository.findByIdForUpdate(TourConfig.SINGLETON_ID)
                ?: throw IllegalStateException("tour_config의 행을 찾을 수 없습니다")

        val publishedAt = currentDateTime()
        config.publishedAt = publishedAt

        return publishedAt
    }

    private fun getCurrentConfig(): TourConfig =
        tourConfigRepository
            .findById(TourConfig.SINGLETON_ID)
            .orElseThrow { IllegalStateException("tour_config의 행을 찾을 수 없습니다") }

    private fun currentDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timeProvider.currentTimeMillis()),
            ZoneId.systemDefault(),
        )
}

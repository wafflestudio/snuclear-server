package com.wafflestudio.team8server.user.service

import com.wafflestudio.team8server.common.dto.PageInfo
import com.wafflestudio.team8server.common.exception.BadRequestException
import com.wafflestudio.team8server.common.exception.ResourceNotFoundException
import com.wafflestudio.team8server.common.exception.UnauthorizedException
import com.wafflestudio.team8server.common.extension.ensureNotNull
import com.wafflestudio.team8server.practice.dto.PracticeAttemptResult
import com.wafflestudio.team8server.practice.dto.PracticeResultResponse
import com.wafflestudio.team8server.practice.dto.PracticeSessionItem
import com.wafflestudio.team8server.practice.dto.PracticeSessionListResponse
import com.wafflestudio.team8server.practice.repository.PracticeDetailRepository
import com.wafflestudio.team8server.practice.repository.PracticeLogRepository
import com.wafflestudio.team8server.user.dto.ChangePasswordRequest
import com.wafflestudio.team8server.user.dto.MyPageResponse
import com.wafflestudio.team8server.user.dto.UpdateProfileRequest
import com.wafflestudio.team8server.user.enum.SocialProvider
import com.wafflestudio.team8server.user.repository.LocalCredentialRepository
import com.wafflestudio.team8server.user.repository.SocialCredentialRepository
import com.wafflestudio.team8server.user.repository.UserRepository
import com.wafflestudio.team8server.user.service.social.google.GoogleOAuthClient
import com.wafflestudio.team8server.user.service.social.kakao.KakaoOAuthClient
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MyPageService(
    private val userRepository: UserRepository,
    private val localCredentialRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
    private val practiceLogRepository: PracticeLogRepository,
    private val practiceDetailRepository: PracticeDetailRepository,
    private val profileImageUrlResolver: ProfileImageUrlResolver,
    private val socialCredentialRepository: SocialCredentialRepository,
    private val kakaoOAuthClient: KakaoOAuthClient,
    private val googleOAuthClient: GoogleOAuthClient,
) {
    @Transactional(readOnly = true)
    fun getMyPage(userId: Long): MyPageResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { ResourceNotFoundException("사용자를 찾을 수 없습니다") }

        val canChangePassword = localCredentialRepository.findByUserId(userId) != null

        return MyPageResponse.from(user, profileImageUrlResolver, canChangePassword)
    }

    @Transactional
    fun updateProfile(
        userId: Long,
        request: UpdateProfileRequest,
    ): MyPageResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { ResourceNotFoundException("사용자를 찾을 수 없습니다") }

        user.nickname = request.nickname

        val canChangePassword = localCredentialRepository.findByUserId(userId) != null

        return MyPageResponse.from(user, profileImageUrlResolver, canChangePassword)
    }

    @Transactional
    fun changePassword(
        userId: Long,
        request: ChangePasswordRequest,
    ) {
        val credential =
            localCredentialRepository.findByUserId(userId)
                ?: throw ResourceNotFoundException(
                    "로컬 계정 정보를 찾을 수 없습니다. 소셜 로그인 사용자는 비밀번호를 변경할 수 없습니다.",
                )

        if (!passwordEncoder.matches(request.currentPassword, credential.passwordHash)) {
            throw UnauthorizedException("현재 비밀번호가 일치하지 않습니다")
        }

        credential.passwordHash = passwordEncoder.encode(request.newPassword).ensureNotNull()
    }

    @Transactional(readOnly = true)
    fun getPracticeSessions(
        userId: Long,
        page: Int,
        size: Int,
    ): PracticeSessionListResponse {
        val pageable = PageRequest.of(page, size)
        val practiceLogs = practiceLogRepository.findByUserIdWithAttemptsOrderByPracticeAtDesc(userId, pageable)

        val items =
            practiceLogs.content.map { log ->
                val totalAttempts = practiceDetailRepository.countByPracticeLogId(log.id!!).toInt()
                val successCount = practiceDetailRepository.countByPracticeLogIdAndIsSuccess(log.id!!, true).toInt()

                PracticeSessionItem(
                    id = log.id!!,
                    practiceAt = log.practiceAt,
                    totalAttempts = totalAttempts,
                    successCount = successCount,
                )
            }

        return PracticeSessionListResponse(
            items = items,
            pageInfo =
                PageInfo(
                    page = practiceLogs.number,
                    size = practiceLogs.size,
                    totalElements = practiceLogs.totalElements,
                    totalPages = practiceLogs.totalPages,
                    hasNext = practiceLogs.hasNext(),
                ),
        )
    }

    @Transactional
    fun deleteAccount(
        userId: Long,
        password: String?,
    ) {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { ResourceNotFoundException("사용자를 찾을 수 없습니다") }

        // 1) 로컬(이메일) 유저라면 비밀번호 재확인
        val localCredential = localCredentialRepository.findByUserId(userId)
        if (localCredential != null) {
            val raw = password ?: throw BadRequestException("비밀번호를 입력해주세요")
            val matches = passwordEncoder.matches(raw, localCredential.passwordHash)
            if (!matches) {
                throw BadRequestException("비밀번호가 일치하지 않습니다")
            }
        }

        // 2) 소셜 연결 해제
        val credentials = socialCredentialRepository.findAllByUserId(userId)

        credentials.forEach { credential ->
            when (credential.provider) {
                SocialProvider.KAKAO.dbValue() -> {
                    kakaoOAuthClient.unlinkUser(credential.socialId)
                }
                SocialProvider.GOOGLE.dbValue() -> {
                    val refreshToken =
                        credential.refreshToken
                            ?: throw BadRequestException(
                                "구글 계정은 탈퇴를 위해 한 번 더 로그인이 필요합니다. 재로그인 후 다시 시도해주세요.",
                            )
                    googleOAuthClient.revokeToken(refreshToken)
                }
                else -> {
                    // 정의되지 않은 provider는 무시
                }
            }
        }

        localCredentialRepository.deleteByUserId(userId)
        socialCredentialRepository.deleteAllByUserId(userId)
        userRepository.delete(user)
    }

    @Transactional(readOnly = true)
    fun getPracticeSessionDetail(
        userId: Long,
        practiceLogId: Long,
    ): PracticeResultResponse {
        val practiceLog =
            practiceLogRepository.findByIdOrNull(practiceLogId)
                ?: throw ResourceNotFoundException("연습 기록을 찾을 수 없습니다")

        if (practiceLog.user.id != userId) {
            throw UnauthorizedException("다른 사용자의 연습 기록에 접근할 수 없습니다")
        }

        val details = practiceDetailRepository.findByPracticeLogIdOrderByReactionTimeAsc(practiceLogId)
        val successCount = details.count { it.isSuccess }

        val attempts =
            details.map { detail ->
                PracticeAttemptResult(
                    courseId = detail.course?.id,
                    courseTitle = detail.courseTitle,
                    lectureNumber = detail.lectureNumber,
                    isSuccess = detail.isSuccess,
                    rank = detail.rank,
                    percentile = detail.percentile,
                    reactionTime = detail.reactionTime,
                )
            }

        return PracticeResultResponse(
            practiceLogId = practiceLog.id.ensureNotNull(),
            practiceAt = practiceLog.practiceAt.toString(),
            earlyClickDiff = practiceLog.earlyClickDiff,
            totalAttempts = details.size,
            successCount = successCount,
            attempts = attempts,
        )
    }
}

package com.wafflestudio.team8server.user.service

import org.springframework.stereotype.Component

/**
 * 프로필 이미지 URL을 해석하는 컴포넌트.
 * - 이미 full URL인 경우 (소셜 로그인 프로필): 그대로 반환
 */
@Component
class ProfileImageUrlResolver {
    fun resolve(profileImageUrl: String?): String? {
        if (profileImageUrl == null) return null

        if (profileImageUrl.startsWith("http://") || profileImageUrl.startsWith("https://")) {
            return profileImageUrl
        }

        return profileImageUrl
    }
}

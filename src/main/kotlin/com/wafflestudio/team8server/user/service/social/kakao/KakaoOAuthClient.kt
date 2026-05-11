package com.wafflestudio.team8server.user.service.social.kakao

import com.fasterxml.jackson.annotation.JsonProperty
import com.wafflestudio.team8server.common.exception.UnauthorizedException
import com.wafflestudio.team8server.config.OAuthProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Component
class KakaoOAuthClient(
    private val props: OAuthProperties,
) {
    private val log = LoggerFactory.getLogger(KakaoOAuthClient::class.java)
    private val restTemplate = RestTemplate()

    fun getUserInfo(
        authorizationCode: String,
        redirectUri: String?,
    ): KakaoUserInfo {
        val accessToken = exchangeCodeForAccessToken(authorizationCode, redirectUri)
        return fetchUserInfo(accessToken)
    }

    /**
     * 카카오 unlink (Admin Key 기반)
     * https://kapi.kakao.com/v1/user/unlink
     * Authorization: KakaoAK {adminKey}
     * target_id_type=user_id, target_id={socialId}
     */
    fun unlinkUser(socialId: String) {
        val kakao = props.kakao
        if (kakao.adminKey.isBlank()) {
            throw UnauthorizedException("카카오 admin key가 설정되지 않았습니다")
        }

        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_FORM_URLENCODED
                accept = listOf(MediaType.APPLICATION_JSON)
                set("Authorization", "KakaoAK ${kakao.adminKey}")
            }

        val form: MultiValueMap<String, String> =
            LinkedMultiValueMap<String, String>().apply {
                add("target_id_type", "user_id")
                add("target_id", socialId)
            }

        val request = HttpEntity(form, headers)

        try {
            restTemplate.exchange(
                kakao.unlinkUri,
                HttpMethod.POST,
                request,
                String::class.java,
            )
        } catch (e: HttpStatusCodeException) {
            throw UnauthorizedException("카카오 연결 해제에 실패했습니다")
        } catch (e: Exception) {
            throw UnauthorizedException("카카오 연결 해제에 실패했습니다")
        }
    }

    private fun exchangeCodeForAccessToken(
        code: String,
        redirectUri: String?,
    ): String {
        val kakao = props.kakao
        log.info(
            "Kakao token exchange requested: clientIdPresent={}, clientIdLength={}, clientSecretPresent={}, redirectUri={}",
            kakao.clientId.isNotBlank(),
            kakao.clientId.length,
            !kakao.clientSecret.isNullOrBlank(),
            redirectUri,
        )

        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_FORM_URLENCODED
                accept = listOf(MediaType.APPLICATION_JSON)
            }

        val form: MultiValueMap<String, String> =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("client_id", kakao.clientId)
                if (!kakao.clientSecret.isNullOrBlank()) add("client_secret", kakao.clientSecret!!)
                if (!redirectUri.isNullOrBlank()) add("redirect_uri", redirectUri)
                add("code", code)
            }

        val request = HttpEntity(form, headers)

        try {
            val response =
                restTemplate.exchange(
                    kakao.tokenUri,
                    HttpMethod.POST,
                    request,
                    KakaoTokenResponse::class.java,
                )

            val body = response.body ?: throw UnauthorizedException("카카오 토큰 발급에 실패했습니다")
            if (body.accessToken.isBlank()) throw UnauthorizedException("카카오 토큰 발급에 실패했습니다")
            return body.accessToken
        } catch (e: HttpStatusCodeException) {
            log.warn(
                "Kakao token exchange failed: status={}, responseBody={}",
                e.statusCode,
                sanitizeOAuthErrorBody(e.responseBodyAsString),
            )
            throw UnauthorizedException("카카오 토큰 발급에 실패했습니다")
        } catch (e: Exception) {
            log.warn(
                "Kakao token exchange failed before response: cause={}({})",
                e::class.simpleName,
                e.message,
            )
            throw UnauthorizedException("카카오 토큰 발급에 실패했습니다")
        }
    }

    private fun fetchUserInfo(accessToken: String): KakaoUserInfo {
        val kakao = props.kakao
        log.info("Kakao user info requested")

        val headers =
            HttpHeaders().apply {
                setBearerAuth(accessToken)
                accept = listOf(MediaType.APPLICATION_JSON)
            }

        val request = HttpEntity<Void>(headers)

        try {
            val response =
                restTemplate.exchange(
                    kakao.userInfoUri,
                    HttpMethod.GET,
                    request,
                    KakaoMeResponse::class.java,
                )

            val me = response.body ?: throw UnauthorizedException("카카오 사용자 정보를 가져오지 못했습니다")

            return KakaoUserInfo(
                id = me.id.toString(),
                email = null, // me.kakaoAccount?.email,
                nickname = me.kakaoAccount?.profile?.nickname,
                profileImageUrl = null, // me.kakaoAccount?.profile?.profileImageUrl,
            )
        } catch (e: HttpStatusCodeException) {
            log.warn(
                "Kakao user info request failed: status={}, responseBody={}",
                e.statusCode,
                sanitizeOAuthErrorBody(e.responseBodyAsString),
            )
            throw UnauthorizedException("카카오 사용자 정보를 가져오지 못했습니다")
        } catch (e: Exception) {
            log.warn(
                "Kakao user info request failed before response: cause={}({})",
                e::class.simpleName,
                e.message,
            )
            throw UnauthorizedException("카카오 사용자 정보를 가져오지 못했습니다")
        }
    }

    private fun sanitizeOAuthErrorBody(body: String): String =
        body
            .replace(
                Regex("(?i)(\"(?:access_token|refresh_token|id_token|client_secret|code)\"\\s*:\\s*\")[^\"]+\""),
            ) { matchResult -> "${matchResult.groupValues[1]}***\"" }
            .take(500)
}

data class KakaoUserInfo(
    val id: String,
    val email: String?,
    val nickname: String?,
    val profileImageUrl: String?,
)

private data class KakaoTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
)

private data class KakaoMeResponse(
    val id: Long,
    @JsonProperty("kakao_account")
    val kakaoAccount: KakaoAccount?,
)

private data class KakaoAccount(
    val email: String?,
    val profile: KakaoProfile?,
)

private data class KakaoProfile(
    val nickname: String?,
    @JsonProperty("profile_image_url")
    val profileImageUrl: String?,
)

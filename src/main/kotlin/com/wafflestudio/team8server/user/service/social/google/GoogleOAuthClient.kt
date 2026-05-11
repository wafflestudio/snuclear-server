package com.wafflestudio.team8server.user.service.social.google

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
class GoogleOAuthClient(
    private val props: OAuthProperties,
) {
    private val log = LoggerFactory.getLogger(GoogleOAuthClient::class.java)
    private val restTemplate = RestTemplate()

    /**
     * 구글 token revoke
     * https://oauth2.googleapis.com/revoke
     * Content-Type: application/x-www-form-urlencoded
     * token={refresh_token or access_token}
     */
    fun revokeToken(token: String) {
        val google = props.google
        if (token.isBlank()) {
            throw UnauthorizedException("구글 revoke 토큰이 비어있습니다")
        }

        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_FORM_URLENCODED
                accept = listOf(MediaType.APPLICATION_JSON)
            }

        val form: MultiValueMap<String, String> =
            LinkedMultiValueMap<String, String>().apply {
                add("token", token)
            }

        val request = HttpEntity(form, headers)

        try {
            restTemplate.exchange(
                google.revokeUri,
                HttpMethod.POST,
                request,
                String::class.java,
            )
        } catch (e: HttpStatusCodeException) {
            throw UnauthorizedException("구글 연결 해제에 실패했습니다")
        } catch (e: Exception) {
            throw UnauthorizedException("구글 연결 해제에 실패했습니다")
        }
    }

    fun exchangeCodeForTokenResult(
        code: String,
        redirectUri: String?,
    ): GoogleTokenResult {
        if (redirectUri.isNullOrBlank()) {
            // 구글은 code 교환에 redirectUri가 필수인 경우가 대부분이라 여기서 강제
            log.warn("Google token exchange rejected: redirectUriMissing=true")
            throw UnauthorizedException("redirectUri가 누락되었습니다")
        }

        val google = props.google
        log.info(
            "Google token exchange requested: clientIdPresent={}, clientIdLength={}, clientSecretPresent={}, redirectUri={}",
            google.clientId.isNotBlank(),
            google.clientId.length,
            google.clientSecret.isNotBlank(),
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
                add("client_id", google.clientId)
                add("client_secret", google.clientSecret)
                add("redirect_uri", redirectUri)
                add("code", code)
            }

        val request = HttpEntity(form, headers)

        try {
            val response =
                restTemplate.exchange(
                    google.tokenUri,
                    HttpMethod.POST,
                    request,
                    GoogleTokenResponse::class.java,
                )

            val body = response.body ?: throw UnauthorizedException("구글 토큰 발급에 실패했습니다")
            val idToken = body.idToken ?: throw UnauthorizedException("구글 id_token이 응답에 포함되지 않았습니다")

            return GoogleTokenResult(
                idToken = idToken,
                accessToken = body.accessToken,
                refreshToken = body.refreshToken,
            )
        } catch (e: HttpStatusCodeException) {
            log.warn(
                "Google token exchange failed: status={}, responseBody={}",
                e.statusCode,
                sanitizeOAuthErrorBody(e.responseBodyAsString),
            )
            throw UnauthorizedException("구글 토큰 발급에 실패했습니다")
        } catch (e: Exception) {
            log.warn(
                "Google token exchange failed before response: cause={}({})",
                e::class.simpleName,
                e.message,
            )
            throw UnauthorizedException("구글 토큰 발급에 실패했습니다")
        }
    }

    private fun sanitizeOAuthErrorBody(body: String): String =
        body
            .replace(
                Regex("(?i)(\"(?:access_token|refresh_token|id_token|client_secret|code)\"\\s*:\\s*\")[^\"]+\""),
            ) { matchResult -> "${matchResult.groupValues[1]}***\"" }
            .take(500)
}

data class GoogleTokenResult(
    val idToken: String,
    val accessToken: String?,
    val refreshToken: String?,
)

private data class GoogleTokenResponse(
    @JsonProperty("id_token")
    val idToken: String?,
    @JsonProperty("access_token")
    val accessToken: String?,
    @JsonProperty("refresh_token")
    var refreshToken: String?,
)

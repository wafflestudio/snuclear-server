package com.wafflestudio.team8server.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class OAuthConfigurationLogger(
    private val props: OAuthProperties,
) {
    private val log = LoggerFactory.getLogger(OAuthConfigurationLogger::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun logOAuthConfiguration() {
        log.info(
            "OAuth configuration loaded: kakao.clientId={}, kakao.clientSecret={}, kakao.adminKey={}, " +
                "google.clientId={}, google.clientSecret={}",
            describe(props.kakao.clientId),
            describe(props.kakao.clientSecret),
            describe(props.kakao.adminKey),
            describe(props.google.clientId),
            describe(props.google.clientSecret),
        )
    }

    private fun describe(value: String?): String {
        val normalized = value.orEmpty()
        return "present=${normalized.isNotBlank()},length=${normalized.length},base64PaddedLike=${isBase64PaddedLike(normalized)}"
    }

    private fun isBase64PaddedLike(value: String): Boolean {
        if (value.isBlank() || value.length % 4 != 0 || !value.endsWith("=")) {
            return false
        }
        return BASE64_PATTERN.matches(value)
    }

    companion object {
        private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/]+={0,2}$")
    }
}

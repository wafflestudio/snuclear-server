package com.wafflestudio.team8server.user.controller

import com.wafflestudio.team8server.common.exception.ErrorResponse
import com.wafflestudio.team8server.user.dto.LoginRequest
import com.wafflestudio.team8server.user.dto.LoginResponse
import com.wafflestudio.team8server.user.dto.SignupRequest
import com.wafflestudio.team8server.user.dto.SignupResponse
import com.wafflestudio.team8server.user.dto.SocialLoginRequest
import com.wafflestudio.team8server.user.service.AuthService
import com.wafflestudio.team8server.user.service.SocialAuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "인증 API", description = "회원가입, 로그인, 로그아웃 기능을 제공합니다")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val socialAuthService: SocialAuthService,
) {
    private val log = LoggerFactory.getLogger(AuthController::class.java)

    @Operation(
        summary = "회원가입",
        description =
            "이메일, 비밀번호, 닉네임을 사용하여 새로운 계정을 생성합니다. " +
                "비밀번호는 8-64자이며 영문, 숫자, 특수문자를 포함해야 합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "회원가입 성공",
                content = [Content(schema = Schema(implementation = SignupResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "유효성 검증 실패 (이메일 형식 오류, 비밀번호 규칙 위반 등)",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "validation-error",
                                summary = "유효성 검증 실패",
                                value = """
                                {
                                  "timestamp": "2026-01-06T12:00:00",
                                  "status": 400,
                                  "error": "Bad Request",
                                  "message": "입력 값이 유효하지 않습니다",
                                  "errorCode": "VALIDATION_FAILED",
                                  "validationErrors": {
                                    "email": "유효한 이메일 형식이어야 합니다",
                                    "password": "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다"
                                  }
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 존재하는 이메일",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "duplicate-email",
                                summary = "이메일 중복",
                                value = """
                                {
                                  "timestamp": "2026-01-06T12:00:00",
                                  "status": 409,
                                  "error": "Conflict",
                                  "message": "이미 사용 중인 이메일입니다",
                                  "errorCode": "DUPLICATE_EMAIL",
                                  "validationErrors": null
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "회원가입 요청 정보",
        required = true,
    )
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED) // 201 Created
    fun signup(
        @Valid @RequestBody request: SignupRequest,
    ): SignupResponse = authService.signup(request)

    @Operation(
        summary = "로그인",
        description =
            "이메일과 비밀번호로 로그인하여 JWT 액세스 토큰을 발급받습니다. " +
                "발급된 토큰은 Authorization 헤더에 'Bearer {token}' 형식으로 포함하여 인증된 요청을 보낼 수 있습니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "로그인 성공 - 사용자 정보와 JWT 토큰 반환",
                content = [Content(schema = Schema(implementation = LoginResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "유효성 검증 실패 (이메일 형식 오류 등)",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "validation-error",
                                summary = "유효성 검증 실패",
                                value = """
                                {
                                  "timestamp": "2026-01-06T12:00:00",
                                  "status": 400,
                                  "error": "Bad Request",
                                  "message": "입력 값이 유효하지 않습니다",
                                  "errorCode": "VALIDATION_FAILED",
                                  "validationErrors": {
                                    "email": "올바른 이메일 형식이 아닙니다"
                                  }
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패 (이메일 또는 비밀번호 불일치)",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "authentication-failed",
                                summary = "로그인 실패",
                                value = """
                                {
                                  "timestamp": "2026-01-06T12:00:00",
                                  "status": 401,
                                  "error": "UNAUTHORIZED",
                                  "message": "이메일 또는 비밀번호가 올바르지 않습니다",
                                  "errorCode": "UNAUTHORIZED",
                                  "validationErrors": null
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "로그인 요청 정보",
        required = true,
    )
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK) // 200 OK
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): LoginResponse = authService.login(request)

    @Operation(
        summary = "카카오 소셜 로그인",
        description =
            "카카오 OAuth Authorization Code로 로그인합니다. " +
                "처음 로그인하는 사용자라면 자동으로 회원가입 후 로그인 처리됩니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "로그인 성공 - 사용자 정보와 JWT 토큰 반환",
                content = [Content(schema = Schema(implementation = LoginResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "유효성 검증 실패 (Authorization Code 누락 등)",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "validation-error",
                                summary = "유효성 검증 실패",
                                value = """
                                    {
                                        "timestamp": "2026-01-06T12:00:00",
                                        "status": 400,
                                        "error": "Bad Request",
                                        "message": "입력 값이 유효하지 않습니다.",
                                        "errorCode": "VALIDATION_FAILED",
                                        "validationErrors": {
                                            "code": "인가 코드는 필수입니다"
                                        }   
                                    }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패 (카카오 인증 실패 등)",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "authentication-failed",
                                summary = "카카오 인증 실패",
                                value = """
                                    {
                                        "timestamp": "2026-01-06T12:00:00",
                                        "status": 401,
                                        "error": "UNAUTHORIZED",
                                        "message": "카카오 인증에 실패했습니다",
                                        "errorCode": "UNAUTHORIZED",
                                        "validationErrors": null
                                    }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "카카오 소셜 로그인 요청 정보",
        required = true,
    )
    @PostMapping("/kakao/login")
    @ResponseStatus(HttpStatus.OK)
    fun kakaoLogin(
        @Valid @RequestBody request: SocialLoginRequest,
    ): LoginResponse {
        log.info(
            "Social login request received: provider=kakao, codePresent={}, redirectUri={}",
            request.code.isNotBlank(),
            request.redirectUri,
        )
        return socialAuthService.kakaoLogin(request.code, request.redirectUri)
    }

    @Operation(
        summary = "구글 소셜 로그인",
        description =
            "구글 OAuth Authorization Code로 로그인합니다. " +
                "처음 로그인하는 사용자라면 자동으로 회원가입 후 로그인 처리됩니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "로그인 성공 - 사용자 정보와 JWT 토큰 반환",
                content = [Content(schema = Schema(implementation = LoginResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "유효성 검증 실패 (Authorization Code 누락 등)",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "validation-error",
                                summary = "유효성 검증 실패",
                                value = """
                                    {
                                        "timestamp": "2026-01-06T12:00:00",
                                        "status": 400,
                                        "error": "Bad Request",
                                        "message": "입력 값이 유효하지 않습니다",
                                        "errorCode": "VALIDATION_FAILED",
                                        "validationErrors": {
                                            "code": "인가 코드는 필수입니다"
                                        }
                                    }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패 (구글 인증 실패 등)",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "authentication-failed",
                                summary = "구글 인증 실패",
                                value = """
                                    {
                                        "timestamp": "2026-01-06T12:00:00",
                                        "status": 401,
                                        "error": "UNAUTHORIZED",
                                        "message": "구글 인증에 실패했습니다",
                                        "errorCode": "UNAUTHORIZED",
                                        "validationErrors": null
                                    }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "구글 소셜 로그인 요청 정보",
        required = true,
    )
    @PostMapping("/google/login")
    @ResponseStatus(HttpStatus.OK)
    fun googleLogin(
        @Valid @RequestBody request: SocialLoginRequest,
    ): LoginResponse {
        log.info(
            "Social login request received: provider=google, codePresent={}, redirectUri={}",
            request.code.isNotBlank(),
            request.redirectUri,
        )
        return socialAuthService.googleLogin(request.code, request.redirectUri)
    }

    @Operation(
        summary = "로그아웃",
        description =
            "현재 사용 중인 JWT 토큰을 블랙리스트에 추가하여 무효화합니다. " +
                "로그아웃 후 해당 토큰으로는 더 이상 인증된 요청을 보낼 수 없습니다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "로그아웃 성공",
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패 (유효하지 않은 토큰)",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "invalid-token",
                                summary = "토큰 인증 실패",
                                value = """
                                {
                                  "timestamp": "2026-01-06T12:00:00",
                                  "status": 401,
                                  "error": "UNAUTHORIZED",
                                  "message": "인증에 실패했습니다",
                                  "errorCode": "UNAUTHORIZED",
                                  "validationErrors": null
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT) // 204 No Content
    fun logout(
        @Parameter(hidden = true)
        @RequestHeader("Authorization")
        authorization: String,
    ) {
        // "Bearer {token}" 형식에서 토큰 추출
        val token = authorization.removePrefix("Bearer ")
        authService.logout(token)
    }
}

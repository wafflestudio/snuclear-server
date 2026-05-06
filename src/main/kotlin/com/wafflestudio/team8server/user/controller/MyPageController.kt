package com.wafflestudio.team8server.user.controller

import com.wafflestudio.team8server.common.auth.LoggedInUserId
import com.wafflestudio.team8server.common.exception.ErrorResponse
import com.wafflestudio.team8server.practice.dto.PracticeResultResponse
import com.wafflestudio.team8server.practice.dto.PracticeSessionListResponse
import com.wafflestudio.team8server.user.dto.ChangePasswordRequest
import com.wafflestudio.team8server.user.dto.DeleteAccountRequest
import com.wafflestudio.team8server.user.dto.MyPageResponse
import com.wafflestudio.team8server.user.dto.UpdateProfileRequest
import com.wafflestudio.team8server.user.service.MyPageService
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
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "마이페이지 API", description = "마이페이지 정보 조회, 프로필 수정, 비밀번호 변경 기능을 제공합니다")
@RestController
@RequestMapping("/api/mypage")
@SecurityRequirement(name = "Bearer Authentication")
class MyPageController(
    private val myPageService: MyPageService,
) {
    @Operation(
        summary = "마이페이지 조회",
        description = "로그인한 사용자의 닉네임, 프로필 이미지, 비밀번호 변경 가능 여부를 조회합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(schema = Schema(implementation = MyPageResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "unauthorized",
                                summary = "인증 실패",
                                value = """
                                {
                                  "timestamp": "2026-01-20T12:00:00",
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
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun getMyPage(
        @Parameter(hidden = true) @LoggedInUserId userId: Long,
    ): MyPageResponse = myPageService.getMyPage(userId)

    @Operation(
        summary = "닉네임 수정",
        description = "닉네임을 수정합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "수정 성공",
                content = [Content(schema = Schema(implementation = MyPageResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "유효성 검증 실패",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "validation-error",
                                summary = "유효성 검증 실패",
                                value = """
                                {
                                  "timestamp": "2026-01-20T12:00:00",
                                  "status": 400,
                                  "error": "Bad Request",
                                  "message": "입력 값이 유효하지 않습니다",
                                  "errorCode": "VALIDATION_FAILED",
                                  "validationErrors": {
                                    "nickname": "닉네임은 2자 이상 20자 이하여야 합니다"
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
                description = "인증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    @PatchMapping("/profile")
    @ResponseStatus(HttpStatus.OK)
    fun updateProfile(
        @Parameter(hidden = true) @LoggedInUserId userId: Long,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): MyPageResponse = myPageService.updateProfile(userId, request)

    @Operation(
        summary = "비밀번호 변경",
        description = "현재 비밀번호를 확인한 후 새 비밀번호로 변경합니다. 소셜 로그인 사용자는 사용할 수 없습니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "비밀번호 변경 성공",
            ),
            ApiResponse(
                responseCode = "400",
                description = "유효성 검증 실패",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "validation-error",
                                summary = "유효성 검증 실패",
                                value = """
                                {
                                  "timestamp": "2026-01-20T12:00:00",
                                  "status": 400,
                                  "error": "Bad Request",
                                  "message": "입력 값이 유효하지 않습니다",
                                  "errorCode": "VALIDATION_FAILED",
                                  "validationErrors": {
                                    "newPassword": "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다"
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
                description = "현재 비밀번호 불일치",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "wrong-password",
                                summary = "현재 비밀번호 불일치",
                                value = """
                                {
                                  "timestamp": "2026-01-20T12:00:00",
                                  "status": 401,
                                  "error": "UNAUTHORIZED",
                                  "message": "현재 비밀번호가 일치하지 않습니다",
                                  "errorCode": "UNAUTHORIZED",
                                  "validationErrors": null
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "로컬 계정 없음 (소셜 로그인 사용자)",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                        examples = [
                            ExampleObject(
                                name = "social-user",
                                summary = "소셜 로그인 사용자",
                                value = """
                                {
                                  "timestamp": "2026-01-20T12:00:00",
                                  "status": 404,
                                  "error": "Not Found",
                                  "message": "로컬 계정 정보를 찾을 수 없습니다. 소셜 로그인 사용자는 비밀번호를 변경할 수 없습니다.",
                                  "errorCode": "RESOURCE_NOT_FOUND",
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
    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        @Parameter(hidden = true) @LoggedInUserId userId: Long,
        @Valid @RequestBody request: ChangePasswordRequest,
    ) = myPageService.changePassword(userId, request)

    @Operation(
        summary = "연습 세션 목록 조회",
        description = "사용자의 연습 세션 목록을 페이지네이션으로 조회합니다. 최신순 정렬됩니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(schema = Schema(implementation = PracticeSessionListResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    @GetMapping("/practice-sessions")
    @ResponseStatus(HttpStatus.OK)
    fun getPracticeSessions(
        @Parameter(hidden = true) @LoggedInUserId userId: Long,
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기", example = "10")
        @RequestParam(defaultValue = "10") size: Int,
    ): PracticeSessionListResponse = myPageService.getPracticeSessions(userId, page, size)

    @Operation(
        summary = "연습 세션 상세 조회",
        description = """
            특정 연습 세션의 상세 결과를 조회합니다.
            본인의 연습 기록만 조회할 수 있습니다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(schema = Schema(implementation = PracticeResultResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패 또는 다른 사용자의 기록 접근",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "세션을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    @GetMapping("/practice-sessions/{practiceLogId}")
    @ResponseStatus(HttpStatus.OK)
    fun getPracticeSessionDetail(
        @Parameter(hidden = true) @LoggedInUserId userId: Long,
        @Parameter(description = "연습 세션 ID") @PathVariable practiceLogId: Long,
    ): PracticeResultResponse = myPageService.getPracticeSessionDetail(userId, practiceLogId)

    @Operation(
        summary = "회원 탈퇴",
        description = "현재 로그인한 사용자의 계정과 관련된 모든 데이터를 삭제합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "회원 탈퇴 성공",
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "요청 오류 (로컬 비밀번호 불일치 / 구글 재로그인 필요 등)",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(
        @Parameter(hidden = true) @LoggedInUserId userId: Long,
        @RequestBody(required = false) request: DeleteAccountRequest?,
    ) = myPageService.deleteAccount(userId, request?.password)
}

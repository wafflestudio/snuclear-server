package com.wafflestudio.team8server.common.exception

// 모든 케이스를 컴파일러가 체크
sealed class BusinessException(
    override val message: String,
    val errorCode: String,
) : RuntimeException(message)

// NOTNULL이어야 하는 곳에서 NULL이 감지되는 예외
class ResourceNotFoundException(
    message: String,
) : BusinessException(
        message = message,
        errorCode = "RESOURCE_NOT_FOUND",
    )

// 잘못된 요청이 들어오는 예외 (형식에 어긋남)
class BadRequestException(
    message: String,
) : BusinessException(
        message = message,
        errorCode = "BAD_REQUEST",
    )

// 리소스에 대한 접근이 금지된 경우의 예외
class ResourceForbiddenException(
    message: String,
) : BusinessException(
        message = message,
        errorCode = "RESOURCE_FORBIDDEN",
    )

// 이메일 중복 예외
class DuplicateEmailException(
    email: String,
) : BusinessException(
        message = "이미 사용 중인 이메일입니다: $email",
        errorCode = "DUPLICATE_EMAIL", // client가 식별할 에러 코드
    )

// 로그인 실패
class UnauthorizedException(
    message: String,
) : BusinessException(
        message = message,
        errorCode = "UNAUTHORIZED",
    )

// 활성 세션 없음
class NoActiveSessionException(
    message: String = "활성화된 연습 세션이 없습니다",
) : BusinessException(
        message = message,
        errorCode = "NO_ACTIVE_SESSION",
    )

// 이미 활성 세션 존재
class ActiveSessionExistsException(
    message: String = "이미 진행 중인 연습 세션이 있습니다",
) : BusinessException(
        message = message,
        errorCode = "ACTIVE_SESSION_EXISTS",
    )

class PreEnrollAlreadyExistsException(
    message: String = "이미 장바구니에 담긴 강의입니다",
) : BusinessException(
        message = message,
        errorCode = "PRE_ENROLL_ALREADY_EXISTS",
    )

class DuplicateCourseNumberInPreEnrollException(
    message: String = "교과목번호가 같은 강의는 장바구니에 함께 담을 수 없습니다",
) : BusinessException(
        message = message,
        errorCode = "DUPLICATE_COURSE_NUMBER_IN_PRE_ENROLL",
    )

class TimeConflictInPreEnrollException(
    message: String = "강의 시간이 겹치는 강의는 장바구니에 함께 담을 수 없습니다",
) : BusinessException(
        message = message,
        errorCode = "TIME_CONFLICT_IN_PRE_ENROLL",
    )

class UserNotFoundException(
    userId: Long,
) : BusinessException(
        message = "사용자를 찾을 수 없습니다: $userId",
        errorCode = "USER_NOT_FOUND",
    )

// 이미 동기화 작업이 실행되고 있는 경우
class CourseSyncAlreadyRunningException(
    message: String = "이미 동기화 작업이 실행 중입니다",
) : BusinessException(
        message = message,
        errorCode = "COURSE_SYNC_ALREADY_RUNNING",
    )

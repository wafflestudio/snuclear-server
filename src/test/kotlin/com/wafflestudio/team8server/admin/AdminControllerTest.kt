package com.wafflestudio.team8server.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.wafflestudio.team8server.TestcontainersConfiguration
import com.wafflestudio.team8server.admin.dto.EnrollmentPeriodUpdateRequest
import com.wafflestudio.team8server.config.EnrollmentPeriodType
import com.wafflestudio.team8server.user.JwtTokenProvider
import com.wafflestudio.team8server.user.model.User
import com.wafflestudio.team8server.user.model.UserRole
import com.wafflestudio.team8server.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class AdminControllerTest
    @Autowired
    constructor(
        private val webApplicationContext: WebApplicationContext,
        private val userRepository: UserRepository,
        private val jwtTokenProvider: JwtTokenProvider,
    ) {
        private lateinit var mockMvc: MockMvc
        private val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules()

        private lateinit var adminUser: User
        private lateinit var normalUser: User
        private lateinit var adminToken: String
        private lateinit var userToken: String

        @BeforeEach
        fun setUp() {
            mockMvc =
                MockMvcBuilders
                    .webAppContextSetup(webApplicationContext)
                    .apply<DefaultMockMvcBuilder>(springSecurity())
                    .build()

            userRepository.deleteAll()

            adminUser = userRepository.save(User(nickname = "관리자", role = UserRole.ADMIN))
            normalUser = userRepository.save(User(nickname = "일반유저", role = UserRole.USER))

            adminToken = jwtTokenProvider.createToken(adminUser.id, "ADMIN")
            userToken = jwtTokenProvider.createToken(normalUser.id, "USER")
        }

        @Nested
        @DisplayName("DB 통계 조회 (GET /api/admin/stats)")
        inner class GetDbStats {
            @Test
            @DisplayName("관리자 토큰으로 DB 통계 조회 성공")
            fun `admin can get db stats`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats")
                            .header("Authorization", "Bearer $adminToken"),
                    ).andDo(print())
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.userCount").isNumber)
                    .andExpect(jsonPath("$.practiceDetailCount").isNumber)
            }

            @Test
            @DisplayName("일반 유저 토큰으로 DB 통계 조회 시 403 반환")
            fun `normal user cannot get db stats returns 403`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats")
                            .header("Authorization", "Bearer $userToken"),
                    ).andDo(print())
                    .andExpect(status().isForbidden)
            }

            @Test
            @DisplayName("토큰 없이 DB 통계 조회 시 401 반환")
            fun `unauthenticated request returns 401`() {
                mockMvc
                    .perform(get("/api/admin/stats"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized)
            }
        }

        @Nested
        @DisplayName("일별 통계 조회 (GET /api/admin/stats/daily)")
        inner class GetDailyStats {
            @Test
            @DisplayName("관리자 토큰으로 일별 통계 조회 성공")
            fun `admin can get daily stats`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/daily")
                            .header("Authorization", "Bearer $adminToken")
                            .queryParam("from", "2024-01-01")
                            .queryParam("to", "2024-01-07"),
                    ).andDo(print())
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.dailyActiveUsers").isArray)
                    .andExpect(jsonPath("$.dailyNewUsers").isArray)
                    .andExpect(jsonPath("$.dailyPracticeDetailCounts").isArray)
            }

            @Test
            @DisplayName("from이 to보다 늦으면 400 반환")
            fun `from after to returns 400`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/daily")
                            .header("Authorization", "Bearer $adminToken")
                            .queryParam("from", "2024-01-07")
                            .queryParam("to", "2024-01-01"),
                    ).andDo(print())
                    .andExpect(status().isBadRequest)
            }

            @Test
            @DisplayName("from과 to가 같은 날짜면 정상 응답")
            fun `from equals to returns 200`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/daily")
                            .header("Authorization", "Bearer $adminToken")
                            .queryParam("from", "2024-01-01")
                            .queryParam("to", "2024-01-01"),
                    ).andDo(print())
                    .andExpect(status().isOk)
            }

            @Test
            @DisplayName("from 파라미터 누락 시 400 반환")
            fun `missing from param returns 400`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/daily")
                            .header("Authorization", "Bearer $adminToken")
                            .queryParam("to", "2024-01-07"),
                    ).andDo(print())
                    .andExpect(status().isBadRequest)
            }

            @Test
            @DisplayName("to 파라미터 누락 시 400 반환")
            fun `missing to param returns 400`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/daily")
                            .header("Authorization", "Bearer $adminToken")
                            .queryParam("from", "2024-01-01"),
                    ).andDo(print())
                    .andExpect(status().isBadRequest)
            }

            @Test
            @DisplayName("일반 유저 토큰으로 일별 통계 조회 시 403 반환")
            fun `normal user cannot get daily stats returns 403`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/daily")
                            .header("Authorization", "Bearer $userToken")
                            .queryParam("from", "2024-01-01")
                            .queryParam("to", "2024-01-07"),
                    ).andDo(print())
                    .andExpect(status().isForbidden)
            }
        }

        @Nested
        @DisplayName("반응속도 히스토그램 조회 (GET /api/admin/stats/reaction-times/histogram)")
        inner class GetReactionTimeHistogram {
            @Test
            @DisplayName("관리자 토큰으로 히스토그램 조회 성공")
            fun `admin can get reaction time histogram`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/reaction-times/histogram")
                            .header("Authorization", "Bearer $adminToken"),
                    ).andDo(print())
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.binSizeMs").value(10))
                    .andExpect(jsonPath("$.maxMs").value(30000))
                    .andExpect(jsonPath("$.overflowCount").isNumber)
                    .andExpect(jsonPath("$.bins").isArray)
                    .andExpect(jsonPath("$.bins.length()").value(3000))
            }

            @Test
            @DisplayName("일반 유저 토큰으로 히스토그램 조회 시 403 반환")
            fun `normal user cannot get histogram returns 403`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/reaction-times/histogram")
                            .header("Authorization", "Bearer $userToken"),
                    ).andDo(print())
                    .andExpect(status().isForbidden)
            }

            @Test
            @DisplayName("토큰 없이 히스토그램 조회 시 401 반환")
            fun `unauthenticated histogram request returns 401`() {
                mockMvc
                    .perform(get("/api/admin/stats/reaction-times/histogram"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized)
            }
        }

        @Nested
        @DisplayName("과목 속성별 반응속도 통계 조회 (GET /api/admin/stats/reaction-times/by-attribute)")
        inner class GetReactionTimeByAttribute {
            @Test
            @DisplayName("CLASSIFICATION 타입으로 조회 성공")
            fun `admin can get reaction time by CLASSIFICATION`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/reaction-times/by-attribute")
                            .header("Authorization", "Bearer $adminToken")
                            .queryParam("type", "CLASSIFICATION"),
                    ).andDo(print())
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$").isArray)
            }

            @Test
            @DisplayName("COLLEGE 타입으로 조회 성공")
            fun `admin can get reaction time by COLLEGE`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/reaction-times/by-attribute")
                            .header("Authorization", "Bearer $adminToken")
                            .queryParam("type", "COLLEGE"),
                    ).andDo(print())
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$").isArray)
            }

            @Test
            @DisplayName("COURSE_NUMBER 타입으로 조회 성공")
            fun `admin can get reaction time by COURSE_NUMBER`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/reaction-times/by-attribute")
                            .header("Authorization", "Bearer $adminToken")
                            .queryParam("type", "COURSE_NUMBER"),
                    ).andDo(print())
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$").isArray)
            }

            @Test
            @DisplayName("잘못된 type 파라미터 시 400 반환")
            fun `invalid type param returns 400`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/reaction-times/by-attribute")
                            .header("Authorization", "Bearer $adminToken")
                            .queryParam("type", "INVALID_TYPE"),
                    ).andDo(print())
                    .andExpect(status().isBadRequest)
            }

            @Test
            @DisplayName("type 파라미터 누락 시 400 반환")
            fun `missing type param returns 400`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/reaction-times/by-attribute")
                            .header("Authorization", "Bearer $adminToken"),
                    ).andDo(print())
                    .andExpect(status().isBadRequest)
            }

            @Test
            @DisplayName("일반 유저 토큰으로 조회 시 403 반환")
            fun `normal user cannot get by-attribute returns 403`() {
                mockMvc
                    .perform(
                        get("/api/admin/stats/reaction-times/by-attribute")
                            .header("Authorization", "Bearer $userToken")
                            .queryParam("type", "CLASSIFICATION"),
                    ).andDo(print())
                    .andExpect(status().isForbidden)
            }
        }

        @Nested
        @DisplayName("수강신청 기간 조회 (GET /api/admin/enrollment-period)")
        inner class GetEnrollmentPeriod {
            @Test
            @DisplayName("관리자 토큰으로 수강신청 기간 조회 성공")
            fun `admin can get enrollment period`() {
                mockMvc
                    .perform(
                        get("/api/admin/enrollment-period")
                            .header("Authorization", "Bearer $adminToken"),
                    ).andDo(print())
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.type").isString)
            }

            @Test
            @DisplayName("일반 유저 토큰으로 조회 시 403 반환")
            fun `normal user cannot get enrollment period returns 403`() {
                mockMvc
                    .perform(
                        get("/api/admin/enrollment-period")
                            .header("Authorization", "Bearer $userToken"),
                    ).andDo(print())
                    .andExpect(status().isForbidden)
            }

            @Test
            @DisplayName("토큰 없이 조회 시 401 반환")
            fun `unauthenticated enrollment period request returns 401`() {
                mockMvc
                    .perform(get("/api/admin/enrollment-period"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized)
            }
        }

        @Nested
        @DisplayName("수강신청 기간 변경 (PUT /api/admin/enrollment-period)")
        inner class UpdateEnrollmentPeriod {
            @Test
            @DisplayName("관리자 토큰으로 FRESHMAN으로 변경 성공")
            fun `admin can update enrollment period to FRESHMAN`() {
                val request = EnrollmentPeriodUpdateRequest(type = EnrollmentPeriodType.FRESHMAN)

                mockMvc
                    .perform(
                        put("/api/admin/enrollment-period")
                            .header("Authorization", "Bearer $adminToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)),
                    ).andDo(print())
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.type").value("FRESHMAN"))
            }

            @Test
            @DisplayName("관리자 토큰으로 REGULAR로 변경 성공")
            fun `admin can update enrollment period to REGULAR`() {
                val request = EnrollmentPeriodUpdateRequest(type = EnrollmentPeriodType.REGULAR)

                mockMvc
                    .perform(
                        put("/api/admin/enrollment-period")
                            .header("Authorization", "Bearer $adminToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)),
                    ).andDo(print())
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.type").value("REGULAR"))
            }

            @Test
            @DisplayName("변경 후 GET으로 반영 확인")
            fun `updated period is reflected in GET`() {
                val request = EnrollmentPeriodUpdateRequest(type = EnrollmentPeriodType.FRESHMAN)

                mockMvc
                    .perform(
                        put("/api/admin/enrollment-period")
                            .header("Authorization", "Bearer $adminToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)),
                    ).andExpect(status().isOk)

                mockMvc
                    .perform(
                        get("/api/admin/enrollment-period")
                            .header("Authorization", "Bearer $adminToken"),
                    ).andDo(print())
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.type").value("FRESHMAN"))
            }

            @Test
            @DisplayName("요청 바디 없이 변경 시 400 반환")
            fun `update without body returns 400`() {
                mockMvc
                    .perform(
                        put("/api/admin/enrollment-period")
                            .header("Authorization", "Bearer $adminToken")
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andDo(print())
                    .andExpect(status().isBadRequest)
            }

            @Test
            @DisplayName("일반 유저 토큰으로 변경 시 403 반환")
            fun `normal user cannot update enrollment period returns 403`() {
                val request = EnrollmentPeriodUpdateRequest(type = EnrollmentPeriodType.FRESHMAN)

                mockMvc
                    .perform(
                        put("/api/admin/enrollment-period")
                            .header("Authorization", "Bearer $userToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)),
                    ).andDo(print())
                    .andExpect(status().isForbidden)
            }

            @Test
            @DisplayName("토큰 없이 변경 시 401 반환")
            fun `unauthenticated update returns 401`() {
                val request = EnrollmentPeriodUpdateRequest(type = EnrollmentPeriodType.FRESHMAN)

                mockMvc
                    .perform(
                        put("/api/admin/enrollment-period")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)),
                    ).andDo(print())
                    .andExpect(status().isUnauthorized)
            }
        }
    }

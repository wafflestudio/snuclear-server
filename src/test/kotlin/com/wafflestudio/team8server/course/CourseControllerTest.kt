package com.wafflestudio.team8server.course

import com.fasterxml.jackson.databind.ObjectMapper
import com.wafflestudio.team8server.TestcontainersConfiguration
import com.wafflestudio.team8server.course.model.Course
import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.course.repository.CourseRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class CourseControllerTest
    @Autowired
    constructor(
        private val webApplicationContext: WebApplicationContext,
        private val courseRepository: CourseRepository,
    ) {
        private lateinit var mockMvc: MockMvc
        private val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules()

        @BeforeEach
        fun setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
            courseRepository.deleteAll()
        }

        @Test
        @DisplayName("query로 강의 검색 시 교과목명 또는 교수명에 query를 포함하는 items와 pageInfo 반환")
        fun `search courses by query returns 200 with items`() {
            courseRepository.saveAll(
                listOf(
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "전선",
                        college = "공과대학",
                        department = "전기·정보공학부",
                        academicCourse = "학부",
                        academicYear = "3",
                        courseNumber = "430.322",
                        lectureNumber = "001",
                        courseTitle = "컴퓨터조직론",
                        credit = 3,
                        instructor = "김장우",
                        placeAndTime = """{"place":"301-102(무선랜제공)/301-102(무선랜제공)","time":"화(17:00~18:15)/목(17:00~18:15)"}""",
                        quota = 100,
                        freshmanQuota = 0,
                    ),
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "전선",
                        college = "공과대학",
                        department = "전기·정보공학부",
                        academicCourse = "학부",
                        academicYear = "4",
                        courseNumber = "430.318",
                        lectureNumber = "001",
                        courseTitle = "운영체제의 기초",
                        credit = 3,
                        instructor = "김조직",
                        placeAndTime = """{"place":"301-102(무선랜제공)/301-102(무선랜제공)","time":"화(17:00~18:15)/목(17:00~18:15)"}""",
                        quota = 100,
                        freshmanQuota = 0,
                    ),
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "교양",
                        college = "자연과학대학",
                        department = "생명과학부",
                        academicCourse = "학부",
                        academicYear = "1",
                        courseNumber = "F35.103L",
                        lectureNumber = "001",
                        courseTitle = "생물학실험",
                        credit = 1,
                        instructor = null,
                        placeAndTime = null,
                        quota = 20,
                        freshmanQuota = 14,
                    ),
                ),
            )

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("query", "조직"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items").isArray)
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").isNumber)
                .andExpect(jsonPath("$.items[0].courseNumber").value("430.318"))
                .andExpect(jsonPath("$.items[0].lectureNumber").value("001"))
                .andExpect(jsonPath("$.items[0].courseTitle").value("운영체제의 기초"))
                .andExpect(jsonPath("$.items[0].credit").value(3))
                .andExpect(jsonPath("$.items[0].placeAndTime").isString)
                .andExpect(jsonPath("$.items[1].id").isNumber)
                .andExpect(jsonPath("$.items[1].courseNumber").value("430.322"))
                .andExpect(jsonPath("$.items[1].lectureNumber").value("001"))
                .andExpect(jsonPath("$.items[1].courseTitle").value("컴퓨터조직론"))
                .andExpect(jsonPath("$.items[1].credit").value(3))
                .andExpect(jsonPath("$.items[1].placeAndTime").isString)
                .andExpect(jsonPath("$.pageInfo").exists())
        }

        @Test
        @DisplayName("교과목번호로 강의 검색 시 정확히 매칭되는 강의 반환")
        fun `search courses by courseNumber returns 200`() {
            courseRepository.save(
                Course(
                    year = 2026,
                    semester = Semester.SPRING,
                    classification = "전선",
                    college = "공과대학",
                    department = "전기·정보공학부",
                    academicCourse = "학부",
                    academicYear = "3",
                    courseNumber = "430.314",
                    lectureNumber = "002",
                    courseTitle = "확률변수 및 확률과정의 기초",
                    credit = 3,
                    instructor = "최완",
                    placeAndTime = """{"place":"301-102(무선랜제공)/301-102(무선랜제공)","time":"화(11:00~12:15)/목(11:00~12:15)"}""",
                    quota = 80,
                    freshmanQuota = 80,
                ),
            )

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("courseNumber", "430.314"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseTitle").value("확률변수 및 확률과정의 기초"))
                .andExpect(jsonPath("$.pageInfo").exists())
        }

        @Test
        @DisplayName("페이지네이션 적용")
        fun `search courses pagination works`() {
            courseRepository.saveAll(
                (1..3).map { idx ->
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "전선",
                        college = "공과대학",
                        department = "전기·정보공학부",
                        academicCourse = "학부",
                        academicYear = "3",
                        courseNumber = "001.000$idx",
                        lectureNumber = "00$idx",
                        courseTitle = "테스트$idx",
                        credit = 3,
                        instructor = "교수$idx",
                        placeAndTime = null,
                        quota = 80,
                        freshmanQuota = null,
                    )
                },
            )

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("query", "테스트")
                        .queryParam("page", "0")
                        .queryParam("size", "1"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.pageInfo").exists())
        }

        @Test
        @DisplayName("학과(department) 필터로 검색")
        fun `search courses by department filter`() {
            courseRepository.saveAll(
                listOf(
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "전선",
                        college = "공과대학",
                        department = "컴퓨터공학부",
                        academicCourse = "학부",
                        academicYear = "3",
                        courseNumber = "4190.310",
                        lectureNumber = "001",
                        courseTitle = "자료구조",
                        credit = 3,
                        instructor = "홍길동",
                        placeAndTime = null,
                        quota = 80,
                        freshmanQuota = null,
                    ),
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "전선",
                        college = "공과대학",
                        department = "전기·정보공학부",
                        academicCourse = "학부",
                        academicYear = "3",
                        courseNumber = "430.322",
                        lectureNumber = "001",
                        courseTitle = "컴퓨터조직론",
                        credit = 3,
                        instructor = "김장우",
                        placeAndTime = null,
                        quota = 100,
                        freshmanQuota = null,
                    ),
                ),
            )

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("department", "컴퓨터공학부"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseTitle").value("자료구조"))
        }

        @Test
        @DisplayName("개설대학(college) 필터로 검색")
        fun `search courses by college filter`() {
            courseRepository.saveAll(
                listOf(
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "전선",
                        college = "공과대학",
                        department = "컴퓨터공학부",
                        academicCourse = "학부",
                        academicYear = "3",
                        courseNumber = "4190.310",
                        lectureNumber = "001",
                        courseTitle = "자료구조",
                        credit = 3,
                        instructor = "홍길동",
                        placeAndTime = null,
                        quota = 80,
                        freshmanQuota = null,
                    ),
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "교양",
                        college = "자연과학대학",
                        department = "생명과학부",
                        academicCourse = "학부",
                        academicYear = "1",
                        courseNumber = "F35.103L",
                        lectureNumber = "001",
                        courseTitle = "생물학실험",
                        credit = 1,
                        instructor = null,
                        placeAndTime = null,
                        quota = 20,
                        freshmanQuota = 14,
                    ),
                ),
            )

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("college", "자연과학대학"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseTitle").value("생물학실험"))
        }

        @Test
        @DisplayName("교과구분(classification) 필터로 검색")
        fun `search courses by classification filter`() {
            courseRepository.saveAll(
                listOf(
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "전필",
                        college = "공과대학",
                        department = "컴퓨터공학부",
                        academicCourse = "학부",
                        academicYear = "2",
                        courseNumber = "4190.200",
                        lectureNumber = "001",
                        courseTitle = "프로그래밍 원리",
                        credit = 3,
                        instructor = "이교수",
                        placeAndTime = null,
                        quota = 60,
                        freshmanQuota = null,
                    ),
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "전선",
                        college = "공과대학",
                        department = "컴퓨터공학부",
                        academicCourse = "학부",
                        academicYear = "3",
                        courseNumber = "4190.310",
                        lectureNumber = "001",
                        courseTitle = "자료구조",
                        credit = 3,
                        instructor = "홍길동",
                        placeAndTime = null,
                        quota = 80,
                        freshmanQuota = null,
                    ),
                ),
            )

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("classification", "전필"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseTitle").value("프로그래밍 원리"))
        }

        @Test
        @DisplayName("검색 결과가 없을 때 빈 배열 반환")
        fun `search courses with no results returns empty`() {
            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("query", "존재하지않는강의"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items").isArray)
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(0))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(0))
                .andExpect(jsonPath("$.pageInfo.hasNext").value(false))
        }

        @Test
        @DisplayName("query와 필터를 동시에 적용")
        fun `search courses with query and filter combined`() {
            courseRepository.saveAll(
                listOf(
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "전선",
                        college = "공과대학",
                        department = "컴퓨터공학부",
                        academicCourse = "학부",
                        academicYear = "3",
                        courseNumber = "4190.310",
                        lectureNumber = "001",
                        courseTitle = "자료구조",
                        credit = 3,
                        instructor = "홍길동",
                        placeAndTime = null,
                        quota = 80,
                        freshmanQuota = null,
                    ),
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "전선",
                        college = "공과대학",
                        department = "전기·정보공학부",
                        academicCourse = "학부",
                        academicYear = "3",
                        courseNumber = "430.310",
                        lectureNumber = "001",
                        courseTitle = "자료구조 응용",
                        credit = 3,
                        instructor = "김교수",
                        placeAndTime = null,
                        quota = 50,
                        freshmanQuota = null,
                    ),
                ),
            )

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("query", "자료구조")
                        .queryParam("department", "컴퓨터공학부"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseTitle").value("자료구조"))
        }

        @Test
        @DisplayName("학년(academicYear) 필터로 검색")
        fun `search courses by academic year filter`() {
            courseRepository.saveAll(
                listOf(
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "전선",
                        college = "공과대학",
                        department = "컴퓨터공학부",
                        academicCourse = "학부",
                        academicYear = "3",
                        courseNumber = "4190.310",
                        lectureNumber = "001",
                        courseTitle = "자료구조",
                        credit = 3,
                        instructor = "홍길동",
                        placeAndTime = null,
                        quota = 80,
                        freshmanQuota = null,
                    ),
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "전필",
                        college = "공과대학",
                        department = "컴퓨터공학부",
                        academicCourse = "학부",
                        academicYear = "1",
                        courseNumber = "4190.100",
                        lectureNumber = "001",
                        courseTitle = "컴퓨터프로그래밍",
                        credit = 3,
                        instructor = "박교수",
                        placeAndTime = null,
                        quota = 120,
                        freshmanQuota = null,
                    ),
                ),
            )

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("academicYear", "1"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseTitle").value("컴퓨터프로그래밍"))
        }

        @Test
        @DisplayName("페이지네이션 pageInfo 정확성 검증")
        fun `search courses pagination pageInfo is accurate`() {
            courseRepository.saveAll(
                (1..5).map { idx ->
                    Course(
                        year = 2026,
                        semester = Semester.SPRING,
                        classification = "전선",
                        college = "공과대학",
                        department = "컴퓨터공학부",
                        academicCourse = "학부",
                        academicYear = "3",
                        courseNumber = "4190.30$idx",
                        lectureNumber = "001",
                        courseTitle = "과목$idx",
                        credit = 3,
                        instructor = "교수$idx",
                        placeAndTime = null,
                        quota = 50,
                        freshmanQuota = null,
                    )
                },
            )

            // 첫 페이지
            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("department", "컴퓨터공학부")
                        .queryParam("page", "0")
                        .queryParam("size", "2"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.pageInfo.page").value(0))
                .andExpect(jsonPath("$.pageInfo.size").value(2))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(5))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(3))
                .andExpect(jsonPath("$.pageInfo.hasNext").value(true))

            // 마지막 페이지
            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("department", "컴퓨터공학부")
                        .queryParam("page", "2")
                        .queryParam("size", "2"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.pageInfo.hasNext").value(false))
        }

        @Test
        @DisplayName("공백 포함 검색어로 검색 시 공백 제거 후 매칭")
        fun `search courses with spaces in query matches without spaces`() {
            courseRepository.save(
                Course(
                    year = 2026,
                    semester = Semester.SPRING,
                    classification = "전선",
                    college = "공과대학",
                    department = "컴퓨터공학부",
                    academicCourse = "학부",
                    academicYear = "3",
                    courseNumber = "4190.310",
                    lectureNumber = "001",
                    courseTitle = "자료 구조",
                    credit = 3,
                    instructor = "홍길동",
                    placeAndTime = null,
                    quota = 80,
                    freshmanQuota = null,
                ),
            )

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("query", "자료구조"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseTitle").value("자료 구조"))
        }
    }

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class CourseSearchNormalizationControllerTest
    @Autowired
    constructor(
        private val webApplicationContext: WebApplicationContext,
        private val courseRepository: CourseRepository,
    ) {
        private lateinit var mockMvc: MockMvc

        @BeforeEach
        fun setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
            courseRepository.deleteAll()
        }

        @Test
        @DisplayName("query without spaces matches course title with spaces")
        fun `search courses without spaces in query matches title with spaces`() {
            courseRepository.save(CourseSearchTestData.dataStructuresWithSpace())

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("query", "\uC790\uB8CC\uAD6C\uC870"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseTitle").value("\uC790\uB8CC \uAD6C\uC870"))
        }

        @Test
        @DisplayName("query with spaces matches course title without spaces")
        fun `search courses with spaces in query matches title without spaces`() {
            courseRepository.save(CourseSearchTestData.dataStructuresWithoutSpace())

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("query", "\uC790\uB8CC \uAD6C\uC870"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseTitle").value("\uC790\uB8CC\uAD6C\uC870"))
        }

        @Test
        @DisplayName("compact shorthand query matches expanded course title")
        fun `search courses with compact shorthand query matches expanded title`() {
            courseRepository.save(CourseSearchTestData.physics2())

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("query", "\uBB3C2"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseTitle").value("\uBB3C\uB9AC\uD5592"))
        }

        @Test
        @DisplayName("semantic major query matches major classifications")
        fun `search courses with major semantic query matches major classifications`() {
            courseRepository.save(CourseSearchTestData.majorCourse())

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("query", "\uC804\uACF5"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].classification").value("\uC804\uC120"))
        }

        @Test
        @DisplayName("semantic graduate query matches graduate academic courses")
        fun `search courses with graduate semantic query matches graduate academic courses`() {
            courseRepository.save(CourseSearchTestData.graduateCourse())

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("query", "\uB300\uD559\uC6D0"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].academicCourse").value("\uC11D\uC0AC"))
        }

        @Test
        @DisplayName("department shorthand query matches expanded department")
        fun `search courses with department shorthand query matches expanded department`() {
            courseRepository.save(CourseSearchTestData.computerScienceDepartmentCourse())

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("query", "\uCEF4\uACF5\uACFC"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].department").value("\uCEF4\uD4E8\uD130\uACF5\uD559\uBD80"))
        }

        @Test
        @DisplayName("blank query keeps exact department filter behavior")
        fun `search courses with blank query and department filter keeps exact filter behavior`() {
            courseRepository.saveAll(CourseSearchTestData.departmentFilterTargets())

            mockMvc
                .perform(
                    get("/api/courses/search")
                        .queryParam("query", "   ")
                        .queryParam("department", "computer-science"),
                ).andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].department").value("computer-science"))
        }
    }

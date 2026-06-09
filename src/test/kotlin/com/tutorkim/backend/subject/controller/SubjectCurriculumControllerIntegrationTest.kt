package com.tutorkim.backend.subject.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tutorkim.backend.content.entity.CurriculumNode
import com.tutorkim.backend.content.repository.CurriculumNodeRepository
import com.tutorkim.backend.identity.entity.AuthSession
import com.tutorkim.backend.identity.entity.User
import com.tutorkim.backend.identity.entity.UserRole
import com.tutorkim.backend.identity.repository.AuthSessionRepository
import com.tutorkim.backend.identity.repository.UserRepository
import com.tutorkim.backend.identity.service.AuthTokenHasher
import com.tutorkim.backend.identity.service.SESSION_COOKIE_NAME
import com.tutorkim.backend.subject.entity.Subject
import com.tutorkim.backend.subject.repository.SubjectRepository
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class SubjectCurriculumControllerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val subjectRepository: SubjectRepository,
    private val curriculumNodeRepository: CurriculumNodeRepository,
    private val userRepository: UserRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val authTokenHasher: AuthTokenHasher,
) {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `authenticated user can list active subjects ordered by name`() {
        val suffix = System.nanoTime()
        subjectRepository.save(Subject(code = "SCI-$suffix", name = "과학"))
        subjectRepository.save(Subject(code = "MATH-$suffix", name = "수학"))
        subjectRepository.save(Subject(code = "OLD-$suffix", name = "비활성과목", active = false))

        val response = mockMvc.get("/api/v1/subjects") {
            with(user("11111111-1111-1111-1111-111111111111").roles("TEACHER"))
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val subjectsByCode = objectMapper.readTree(response)
            .path("data")
            .associateBy { it.path("code").asText() }
        assertThat(subjectsByCode["SCI-$suffix"]?.path("name")?.asText()).isEqualTo("과학")
        assertThat(subjectsByCode["MATH-$suffix"]?.path("name")?.asText()).isEqualTo("수학")
        assertThat(subjectsByCode).doesNotContainKey("OLD-$suffix")
    }

    @Test
    fun `valid session cookie authenticates web requests`() {
        val suffix = UUID.randomUUID()
        subjectRepository.save(Subject(code = "COOKIE-MATH-$suffix", name = "수학"))
        val rawSessionToken = "session-$suffix"
        val user = userRepository.save(
            User(
                email = "cookie-teacher-$suffix@example.com",
                name = "세션 선생",
                role = UserRole.TEACHER,
            ),
        )
        authSessionRepository.save(
            AuthSession(
                user = user,
                sessionTokenHash = authTokenHasher.sha256Hex(rawSessionToken),
                expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
            ),
        )

        val response = mockMvc.get("/api/v1/subjects") {
            cookie(Cookie(SESSION_COOKIE_NAME, rawSessionToken))
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val subjectsByCode = objectMapper.readTree(response)
            .path("data")
            .associateBy { it.path("code").asText() }
        assertThat(subjectsByCode["COOKIE-MATH-$suffix"]?.path("name")?.asText()).isEqualTo("수학")
    }

    @Test
    fun `expired session cookie does not authenticate web requests`() {
        val suffix = UUID.randomUUID()
        val rawSessionToken = "expired-$suffix"
        val user = userRepository.save(
            User(
                email = "expired-teacher-$suffix@example.com",
                name = "만료 선생",
                role = UserRole.TEACHER,
            ),
        )
        authSessionRepository.save(
            AuthSession(
                user = user,
                sessionTokenHash = authTokenHasher.sha256Hex(rawSessionToken),
                expiresAt = Instant.parse("2020-01-01T00:00:00Z"),
            ),
        )

        mockMvc.get("/api/v1/subjects") {
            cookie(Cookie(SESSION_COOKIE_NAME, rawSessionToken))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `authenticated user can read curriculum tree for active subject`() {
        val suffix = System.nanoTime()
        val subject = subjectRepository.save(Subject(code = "MATH-TREE-$suffix", name = "수학"))
        val depth1 = curriculumNodeRepository.save(
            CurriculumNode(
                subjectId = subject.id!!,
                depth = 1,
                name = "확률과 통계",
                system = true,
            ),
        )
        val depth2 = curriculumNodeRepository.save(
            CurriculumNode(
                subjectId = subject.id!!,
                parentId = depth1.id!!,
                depth = 2,
                name = "경우의 수",
                system = true,
            ),
        )
        curriculumNodeRepository.save(
            CurriculumNode(
                subjectId = subject.id!!,
                parentId = depth2.id!!,
                depth = 3,
                name = "순열",
                system = true,
            ),
        )

        mockMvc.get("/api/v1/subjects/${subject.id}/curriculum") {
            with(user("22222222-2222-2222-2222-222222222222").roles("TEACHER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].name") { value("확률과 통계") }
            jsonPath("$.data[0].depth") { value(1) }
            jsonPath("$.data[0].children[0].name") { value("경우의 수") }
            jsonPath("$.data[0].children[0].children[0].name") { value("순열") }
        }
    }

    @Test
    fun `inactive subject curriculum returns not found`() {
        val suffix = System.nanoTime()
        val subject = subjectRepository.save(Subject(code = "INACTIVE-$suffix", name = "비활성", active = false))

        mockMvc.get("/api/v1/subjects/${subject.id}/curriculum") {
            with(user("33333333-3333-3333-3333-333333333333").roles("TEACHER"))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("과목을 찾을 수 없습니다.") }
        }
    }

}

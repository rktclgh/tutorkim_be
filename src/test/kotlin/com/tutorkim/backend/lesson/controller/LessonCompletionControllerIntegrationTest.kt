package com.tutorkim.backend.lesson.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tutorkim.backend.content.entity.CurriculumNode
import com.tutorkim.backend.content.repository.CurriculumNodeRepository
import com.tutorkim.backend.identity.entity.User
import com.tutorkim.backend.identity.entity.UserRole
import com.tutorkim.backend.identity.repository.UserRepository
import com.tutorkim.backend.lesson.entity.LessonSession
import com.tutorkim.backend.lesson.entity.LessonStatus
import com.tutorkim.backend.lesson.repository.LessonSessionRepository
import com.tutorkim.backend.student.entity.StudentProfile
import com.tutorkim.backend.student.entity.TeacherProfile
import com.tutorkim.backend.student.entity.TeacherStudent
import com.tutorkim.backend.student.entity.TeacherStudentSubject
import com.tutorkim.backend.student.repository.StudentProfileRepository
import com.tutorkim.backend.student.repository.TeacherProfileRepository
import com.tutorkim.backend.student.repository.TeacherStudentRepository
import com.tutorkim.backend.student.repository.TeacherStudentSubjectRepository
import com.tutorkim.backend.subject.entity.Subject
import com.tutorkim.backend.subject.repository.SubjectRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class LessonCompletionControllerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val teacherProfileRepository: TeacherProfileRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val subjectRepository: SubjectRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val teacherStudentSubjectRepository: TeacherStudentSubjectRepository,
    private val lessonSessionRepository: LessonSessionRepository,
    private val curriculumNodeRepository: CurriculumNodeRepository,
) {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `teacher can read owned lesson session detail`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val session = createLessonSession(relationship, fixture.math)

        mockMvc.get("/api/v1/lesson-sessions/${session.id}") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(session.id!!.toString()) }
            jsonPath("$.data.studentId") { value(fixture.studentProfile.id!!.toString()) }
            jsonPath("$.data.subjectId") { value(fixture.math.id!!.toString()) }
            jsonPath("$.data.scheduledStartAt") { value("2026-05-18T10:00:00Z") }
            jsonPath("$.data.scheduledEndAt") { value("2026-05-18T12:00:00Z") }
            jsonPath("$.data.actualStartAt") { doesNotExist() }
            jsonPath("$.data.actualEndAt") { doesNotExist() }
            jsonPath("$.data.status") { value("SCHEDULED") }
            jsonPath("$.data.currentCurriculumNodeId") { doesNotExist() }
            jsonPath("$.data.currentProgress") { doesNotExist() }
        }
    }

    @Test
    fun `teacher can complete lesson and detail shows saved progress`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val session = createLessonSession(relationship, fixture.math)
        val curriculumNode = curriculumNodeRepository.save(
            CurriculumNode(
                subjectId = fixture.math.id!!,
                depth = 1,
                name = "확률",
                system = true,
            ),
        )
        val body = objectMapper.writeValueAsString(
            mapOf(
                "currentCurriculumNodeId" to curriculumNode.id!!.toString(),
                "previousProgress" to "지난 시간: 경우의 수",
                "currentProgress" to "오늘: 확률 기본 개념",
                "nextProgress" to "다음: 조건부 확률",
                "focusLevel" to "HIGH",
                "understandingLevel" to "MEDIUM",
                "assignmentPerformance" to "GOOD",
                "lessonMemo" to "계산 실수는 줄었고 개념 질문이 많음",
            ),
        )

        mockMvc.patch("/api/v1/lesson-sessions/${session.id}/complete") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(session.id!!.toString()) }
            jsonPath("$.data.status") { value("COMPLETED") }
            jsonPath("$.data.currentCurriculumNodeId") { value(curriculumNode.id!!.toString()) }
            jsonPath("$.data.previousProgress") { value("지난 시간: 경우의 수") }
            jsonPath("$.data.currentProgress") { value("오늘: 확률 기본 개념") }
            jsonPath("$.data.nextProgress") { value("다음: 조건부 확률") }
            jsonPath("$.data.focusLevel") { value("HIGH") }
            jsonPath("$.data.understandingLevel") { value("MEDIUM") }
            jsonPath("$.data.assignmentPerformance") { value("GOOD") }
            jsonPath("$.data.lessonMemo") { value("계산 실수는 줄었고 개념 질문이 많음") }
            jsonPath("$.data.actualStartAt") { value("2026-05-18T10:00:00Z") }
            jsonPath("$.data.actualEndAt") { exists() }
        }

        val savedSession = lessonSessionRepository.findById(session.id!!).orElseThrow()
        assertThat(savedSession.status).isEqualTo(LessonStatus.COMPLETED)
        assertThat(savedSession.currentCurriculumNodeId).isEqualTo(curriculumNode.id)
        assertThat(savedSession.previousProgressSummary).isEqualTo("지난 시간: 경우의 수")
        assertThat(savedSession.currentProgress).isEqualTo("오늘: 확률 기본 개념")
        assertThat(savedSession.nextProgress).isEqualTo("다음: 조건부 확률")
        assertThat(savedSession.lessonMemo).isEqualTo("계산 실수는 줄었고 개념 질문이 많음")
        assertThat(savedSession.actualStartAt).isEqualTo(Instant.parse("2026-05-18T10:00:00Z"))
        assertThat(savedSession.actualEndAt).isNotNull()
    }

    @Test
    fun `completion updates progress without changing completed lesson end time`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val fixedActualEndAt = Instant.parse("2026-05-18T12:05:00Z")
        val session = createLessonSession(relationship, fixture.math, LessonStatus.COMPLETED).also {
            it.actualStartAt = Instant.parse("2026-05-18T10:00:00Z")
            it.actualEndAt = fixedActualEndAt
            lessonSessionRepository.save(it)
        }
        val body = completeRequestBody(
            "currentProgress" to "완료 후 수정된 현재 진도",
            "lessonMemo" to "완료 후 메모만 보정",
        )

        mockMvc.patch("/api/v1/lesson-sessions/${session.id}/complete") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("COMPLETED") }
            jsonPath("$.data.currentProgress") { value("완료 후 수정된 현재 진도") }
            jsonPath("$.data.lessonMemo") { value("완료 후 메모만 보정") }
            jsonPath("$.data.actualEndAt") { value("2026-05-18T12:05:00Z") }
        }

        val savedSession = lessonSessionRepository.findById(session.id!!).orElseThrow()
        assertThat(savedSession.actualEndAt).isEqualTo(fixedActualEndAt)
        assertThat(savedSession.currentProgress).isEqualTo("완료 후 수정된 현재 진도")
    }

    @Test
    fun `student role cannot read or complete lesson sessions`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val session = createLessonSession(relationship, fixture.math)
        val body = completeRequestBody()

        mockMvc.get("/api/v1/lesson-sessions/${session.id}") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }

        mockMvc.patch("/api/v1/lesson-sessions/${session.id}/complete") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `teacher cannot read or complete another teachers lesson session`() {
        val fixture = createFixture()
        val otherTeacher = createTeacher("other")
        val relationship = createRelationship(fixture)
        val session = createLessonSession(relationship, fixture.math)
        val body = completeRequestBody()

        mockMvc.get("/api/v1/lesson-sessions/${session.id}") {
            with(user(otherTeacher.user.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("수업 세션을 찾을 수 없습니다.") }
        }

        mockMvc.patch("/api/v1/lesson-sessions/${session.id}/complete") {
            with(user(otherTeacher.user.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("수업 세션을 찾을 수 없습니다.") }
        }
    }

    @Test
    fun `teacher cannot read inactive relationship or deleted student lesson sessions`() {
        val inactiveFixture = createFixture()
        val inactiveRelationship = createRelationship(inactiveFixture, active = false)
        val inactiveSession = createLessonSession(inactiveRelationship, inactiveFixture.math)

        mockMvc.get("/api/v1/lesson-sessions/${inactiveSession.id}") {
            with(user(inactiveFixture.teacherUser.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("수업 세션을 찾을 수 없습니다.") }
        }

        val deletedFixture = createFixture()
        val deletedRelationship = createRelationship(deletedFixture)
        val deletedStudentSession = createLessonSession(deletedRelationship, deletedFixture.math)
        deletedFixture.studentProfile.deletedAt = Instant.now()
        studentProfileRepository.save(deletedFixture.studentProfile)

        mockMvc.patch("/api/v1/lesson-sessions/${deletedStudentSession.id}/complete") {
            with(user(deletedFixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = completeRequestBody()
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("수업 세션을 찾을 수 없습니다.") }
        }
    }

    @Test
    fun `completion rejects cancelled sessions invalid curriculum nodes and blank progress`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val cancelledSession = createLessonSession(relationship, fixture.math, LessonStatus.CANCELLED)
        val cancelledBody = completeRequestBody()

        mockMvc.patch("/api/v1/lesson-sessions/${cancelledSession.id}/complete") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = cancelledBody
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("CONFLICT") }
            jsonPath("$.error.message") { value("취소된 수업은 완료 처리할 수 없습니다.") }
        }

        val session = createLessonSession(relationship, fixture.math)
        val scienceNode = curriculumNodeRepository.save(
            CurriculumNode(
                subjectId = fixture.science.id!!,
                depth = 1,
                name = "운동량",
                system = true,
            ),
        )
        val invalidNodeBody = completeRequestBody(
            "currentCurriculumNodeId" to scienceNode.id!!.toString(),
        )

        mockMvc.patch("/api/v1/lesson-sessions/${session.id}/complete") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = invalidNodeBody
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("커리큘럼 노드를 찾을 수 없습니다.") }
        }

        val otherTeacher = createTeacher("curriculum-owner")
        val otherTeacherNode = curriculumNodeRepository.save(
            CurriculumNode(
                subjectId = fixture.math.id!!,
                depth = 1,
                name = "다른 선생 커스텀 단원",
                ownerTeacherId = otherTeacher.profile.id!!,
            ),
        )
        val hiddenNodeBody = completeRequestBody(
            "currentCurriculumNodeId" to otherTeacherNode.id!!.toString(),
        )

        mockMvc.patch("/api/v1/lesson-sessions/${session.id}/complete") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = hiddenNodeBody
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("커리큘럼 노드를 찾을 수 없습니다.") }
        }

        val blankProgressBody = completeRequestBody(
            "currentProgress" to " ",
        )

        mockMvc.patch("/api/v1/lesson-sessions/${session.id}/complete") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = blankProgressBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
        }

        val missingFocusLevelBody = completeRequestBody(
            "focusLevel" to null,
        )

        mockMvc.patch("/api/v1/lesson-sessions/${session.id}/complete") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = missingFocusLevelBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
        }
    }

    private fun completeRequestBody(vararg overrides: Pair<String, String?>): String {
        val body = mutableMapOf<String, Any?>(
            "previousProgress" to "지난 진도",
            "currentProgress" to "현재 진도",
            "nextProgress" to "다음 진도",
            "focusLevel" to "HIGH",
            "understandingLevel" to "MEDIUM",
            "assignmentPerformance" to "GOOD",
            "lessonMemo" to "메모",
        )
        overrides.forEach { (key, value) ->
            if (value == null) {
                body.remove(key)
            } else {
                body[key] = value
            }
        }
        return objectMapper.writeValueAsString(body)
    }

    private fun createLessonSession(
        relationship: TeacherStudent,
        subject: Subject,
        status: LessonStatus = LessonStatus.SCHEDULED,
    ): LessonSession =
        lessonSessionRepository.save(
            LessonSession(
                teacherStudentId = relationship.id!!,
                subjectId = subject.id!!,
                scheduledStartAt = Instant.parse("2026-05-18T10:00:00Z"),
                scheduledEndAt = Instant.parse("2026-05-18T12:00:00Z"),
                status = status,
            ),
        )

    private fun createRelationship(
        fixture: Fixture,
        active: Boolean = true,
    ): TeacherStudent {
        val relationship = teacherStudentRepository.save(
            TeacherStudent(
                teacher = fixture.teacherProfile,
                student = fixture.studentProfile,
                defaultSubject = fixture.math,
                active = active,
            ),
        )
        teacherStudentSubjectRepository.save(
            TeacherStudentSubject(
                teacherStudent = relationship,
                subject = fixture.math,
                primary = true,
            ),
        )
        return relationship
    }

    private fun createFixture(): Fixture {
        val teacher = createTeacher("lesson-completion")
        val suffix = UUID.randomUUID().toString()
        val codeSuffix = suffix.take(8)
        val studentUser = userRepository.save(
            User(name = "홍길동", role = UserRole.STUDENT, email = "completion-student-$suffix@example.com"),
        )
        val studentProfile = studentProfileRepository.save(
            StudentProfile(
                user = studentUser,
                name = "홍길동",
                school = "OO고",
                grade = "고2",
            ),
        )
        val math = subjectRepository.save(Subject(code = "COMPLETION-MATH-$codeSuffix", name = "수학"))
        val science = subjectRepository.save(Subject(code = "COMPLETION-SCI-$codeSuffix", name = "과학"))

        return Fixture(
            teacherUser = teacher.user,
            studentUser = studentUser,
            teacherProfile = teacher.profile,
            studentProfile = studentProfile,
            math = math,
            science = science,
        )
    }

    private fun createTeacher(label: String): Teacher {
        val suffix = UUID.randomUUID().toString()
        val teacherUser = userRepository.save(
            User(name = "김선생", role = UserRole.TEACHER, email = "$label-teacher-$suffix@example.com"),
        )
        val teacherProfile = teacherProfileRepository.save(
            TeacherProfile(user = teacherUser, displayName = "김선생"),
        )
        return Teacher(user = teacherUser, profile = teacherProfile)
    }

    private data class Teacher(
        val user: User,
        val profile: TeacherProfile,
    )

    private data class Fixture(
        val teacherUser: User,
        val studentUser: User,
        val teacherProfile: TeacherProfile,
        val studentProfile: StudentProfile,
        val math: Subject,
        val science: Subject,
    )
}

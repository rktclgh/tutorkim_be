package com.tutorkim.backend.student.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tutorkim.backend.identity.entity.User
import com.tutorkim.backend.identity.entity.UserRole
import com.tutorkim.backend.identity.repository.UserRepository
import com.tutorkim.backend.student.entity.InviteCodeStatus
import com.tutorkim.backend.student.entity.StudentProfile
import com.tutorkim.backend.student.entity.TeacherProfile
import com.tutorkim.backend.student.repository.StudentProfileRepository
import com.tutorkim.backend.student.repository.TeacherInviteCodeRepository
import com.tutorkim.backend.student.repository.TeacherProfileRepository
import com.tutorkim.backend.student.repository.TeacherStudentRepository
import com.tutorkim.backend.student.service.TeacherInviteCodeService
import com.tutorkim.backend.subject.entity.Subject
import com.tutorkim.backend.subject.entity.TeacherSubject
import com.tutorkim.backend.subject.repository.SubjectRepository
import com.tutorkim.backend.subject.repository.TeacherSubjectRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class TeacherInviteCodeControllerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val teacherProfileRepository: TeacherProfileRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val subjectRepository: SubjectRepository,
    private val teacherSubjectRepository: TeacherSubjectRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val teacherInviteCodeRepository: TeacherInviteCodeRepository,
    private val teacherInviteCodeService: TeacherInviteCodeService,
) {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `teacher can create and read the active invite code`() {
        val fixture = createFixture()
        val body = objectMapper.writeValueAsString(mapOf("expiresInHours" to 6))

        mockMvc.post("/api/v1/teacher/invite-codes") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { exists() }
            jsonPath("$.data.code") { exists() }
            jsonPath("$.data.status") { value("ACTIVE") }
            jsonPath("$.data.expiresAt") { exists() }
            jsonPath("$.error") { isEmpty() }
        }

        mockMvc.get("/api/v1/teacher/invite-codes/active") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.code") { exists() }
            jsonPath("$.data.status") { value("ACTIVE") }
        }
    }

    @Test
    fun `teacher can revoke an owned active invite code`() {
        val fixture = createFixture()
        val inviteCode = teacherInviteCodeService.createInviteCode(
            teacherId = fixture.teacherProfile.id!!,
            code = "RVK${System.nanoTime().toString().takeLast(5)}",
            expiresAt = Instant.now().plusSeconds(86_400),
        )

        mockMvc.delete("/api/v1/teacher/invite-codes/${inviteCode.id}") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isNoContent() }
        }

        assertThat(teacherInviteCodeRepository.findById(inviteCode.id!!).orElseThrow().status)
            .isEqualTo(InviteCodeStatus.REVOKED)
    }

    @Test
    fun `student can add a teacher by one time invite code`() {
        val fixture = createFixture(defaultSubject = true)
        val inviteCode = teacherInviteCodeService.createInviteCode(
            teacherId = fixture.teacherProfile.id!!,
            code = "ADD${System.nanoTime().toString().takeLast(5)}",
            expiresAt = Instant.now().plusSeconds(86_400),
        )
        val body = objectMapper.writeValueAsString(mapOf("inviteCode" to inviteCode.code))

        mockMvc.post("/api/v1/student/teachers") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { exists() }
            jsonPath("$.data.teacher.id") { value(fixture.teacherProfile.id!!.toString()) }
            jsonPath("$.data.teacher.displayName") { value("김선생") }
            jsonPath("$.data.defaultSubject.id") { value(fixture.subject.id!!.toString()) }
        }

        assertThat(teacherStudentRepository.existsByTeacher_IdAndStudent_Id(
            fixture.teacherProfile.id!!,
            fixture.studentProfile.id!!,
        )).isTrue()
        assertThat(teacherInviteCodeRepository.findById(inviteCode.id!!).orElseThrow().status)
            .isEqualTo(InviteCodeStatus.REVOKED)
    }

    @Test
    fun `invalid invite code returns a request error envelope`() {
        val fixture = createFixture()
        val body = objectMapper.writeValueAsString(mapOf("inviteCode" to "NOPE2026"))

        mockMvc.post("/api/v1/student/teachers") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.data") { isEmpty() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
            jsonPath("$.error.message") { value("초대코드가 유효하지 않거나 만료되었습니다.") }
        }
    }

    @Test
    fun `student role cannot create teacher invite codes`() {
        val fixture = createFixture()
        val body = objectMapper.writeValueAsString(mapOf("expiresInHours" to 24))

        mockMvc.post("/api/v1/teacher/invite-codes") {
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
    fun `teacher role cannot consume student invite codes`() {
        val fixture = createFixture()
        val body = objectMapper.writeValueAsString(mapOf("inviteCode" to "NOPE2026"))

        mockMvc.post("/api/v1/student/teachers") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `deleted student profile cannot consume invite codes`() {
        val fixture = createFixture()
        fixture.studentProfile.deletedAt = Instant.now()
        studentProfileRepository.save(fixture.studentProfile)
        val inviteCode = teacherInviteCodeService.createInviteCode(
            teacherId = fixture.teacherProfile.id!!,
            code = "DEL${System.nanoTime().toString().takeLast(5)}",
            expiresAt = Instant.now().plusSeconds(86_400),
        )
        val body = objectMapper.writeValueAsString(mapOf("inviteCode" to inviteCode.code))

        mockMvc.post("/api/v1/student/teachers") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
            jsonPath("$.error.message") { value("학생 프로필이 필요합니다.") }
        }
    }

    @Test
    fun `teacher can deactivate an active student relationship`() {
        val fixture = createFixture(defaultSubject = true)
        val relationship = connectTeacherAndStudent(fixture, "RMV${System.nanoTime().toString().takeLast(5)}")

        mockMvc.delete("/api/v1/students/${fixture.studentProfile.id}/teacher-relationship") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isNoContent() }
        }

        val savedRelationship = teacherStudentRepository.findById(relationship.id!!).orElseThrow()
        assertThat(savedRelationship.active).isFalse()
    }

    @Test
    fun `student role cannot deactivate teacher relationship`() {
        val fixture = createFixture(defaultSubject = true)
        connectTeacherAndStudent(fixture, "SDR${System.nanoTime().toString().takeLast(5)}")

        mockMvc.delete("/api/v1/students/${fixture.studentProfile.id}/teacher-relationship") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `teacher cannot deactivate another teacher student relationship`() {
        val fixture = createFixture(defaultSubject = true)
        connectTeacherAndStudent(fixture, "OTH${System.nanoTime().toString().takeLast(5)}")
        val otherTeacher = createFixture()

        mockMvc.delete("/api/v1/students/${fixture.studentProfile.id}/teacher-relationship") {
            with(user(otherTeacher.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("활성 학생 관계를 찾을 수 없습니다.") }
        }
    }

    @Test
    fun `student can reconnect to teacher after teacher deactivated the relationship`() {
        val fixture = createFixture(defaultSubject = true)
        val firstRelationship = connectTeacherAndStudent(fixture, "REC${System.nanoTime().toString().takeLast(5)}")
        firstRelationship.active = false
        teacherStudentRepository.save(firstRelationship)
        val inviteCode = teacherInviteCodeService.createInviteCode(
            teacherId = fixture.teacherProfile.id!!,
            code = "REJ${System.nanoTime().toString().takeLast(5)}",
            expiresAt = Instant.now().plusSeconds(86_400),
        )
        val body = objectMapper.writeValueAsString(mapOf("inviteCode" to inviteCode.code))

        mockMvc.post("/api/v1/student/teachers") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(firstRelationship.id!!.toString()) }
            jsonPath("$.data.active") { value(true) }
        }

        val relationships = teacherStudentRepository.findAll().filter {
            it.teacher.id == fixture.teacherProfile.id && it.student.id == fixture.studentProfile.id
        }
        assertThat(relationships).hasSize(1)
        assertThat(relationships.single().active).isTrue()
        assertThat(teacherInviteCodeRepository.findById(inviteCode.id!!).orElseThrow().status)
            .isEqualTo(InviteCodeStatus.REVOKED)
    }

    @Test
    fun `student cannot reconnect while the relationship is already active`() {
        val fixture = createFixture(defaultSubject = true)
        connectTeacherAndStudent(fixture, "ACT${System.nanoTime().toString().takeLast(5)}")
        val inviteCode = teacherInviteCodeService.createInviteCode(
            teacherId = fixture.teacherProfile.id!!,
            code = "ACR${System.nanoTime().toString().takeLast(5)}",
            expiresAt = Instant.now().plusSeconds(86_400),
        )
        val body = objectMapper.writeValueAsString(mapOf("inviteCode" to inviteCode.code))

        mockMvc.post("/api/v1/student/teachers") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("CONFLICT") }
            jsonPath("$.error.message") { value("이미 연결된 선생님입니다.") }
        }
    }

    private fun connectTeacherAndStudent(fixture: Fixture, code: String) =
        teacherInviteCodeService.consumeInviteCode(
            code = teacherInviteCodeService.createInviteCode(
                teacherId = fixture.teacherProfile.id!!,
                code = code,
                expiresAt = Instant.now().plusSeconds(86_400),
            ).code,
            studentId = fixture.studentProfile.id!!,
        )

    private fun createFixture(defaultSubject: Boolean = false): Fixture {
        val suffix = System.nanoTime()
        val teacherUser = userRepository.save(
            User(name = "김선생", role = UserRole.TEACHER, email = "teacher-$suffix@example.com"),
        )
        val studentUser = userRepository.save(
            User(name = "홍길동", role = UserRole.STUDENT, email = "student-$suffix@example.com"),
        )
        val teacherProfile = teacherProfileRepository.save(
            TeacherProfile(user = teacherUser, displayName = "김선생"),
        )
        val studentProfile = studentProfileRepository.save(
            StudentProfile(user = studentUser, name = "홍길동"),
        )
        val subject = subjectRepository.save(Subject(code = "MATH-$suffix", name = "수학"))

        if (defaultSubject) {
            teacherSubjectRepository.save(
                TeacherSubject(teacher = teacherProfile, subject = subject, default = true),
            )
        }

        return Fixture(
            teacherUser = teacherUser,
            studentUser = studentUser,
            teacherProfile = teacherProfile,
            studentProfile = studentProfile,
            subject = subject,
        )
    }

    private data class Fixture(
        val teacherUser: User,
        val studentUser: User,
        val teacherProfile: TeacherProfile,
        val studentProfile: StudentProfile,
        val subject: Subject,
    )
}

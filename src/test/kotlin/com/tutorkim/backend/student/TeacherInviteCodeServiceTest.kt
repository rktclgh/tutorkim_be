package com.tutorkim.backend.student

import com.tutorkim.backend.identity.AuthSession
import com.tutorkim.backend.identity.AuthSessionRepository
import com.tutorkim.backend.identity.User
import com.tutorkim.backend.identity.UserRepository
import com.tutorkim.backend.identity.UserRole
import com.tutorkim.backend.subject.Subject
import com.tutorkim.backend.subject.SubjectRepository
import com.tutorkim.backend.subject.TeacherSubject
import com.tutorkim.backend.subject.TeacherSubjectRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@DataJpaTest
@Import(TeacherInviteCodeService::class)
class TeacherInviteCodeServiceTest @Autowired constructor(
    private val service: TeacherInviteCodeService,
    private val userRepository: UserRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val entityManager: TestEntityManager,
    private val subjectRepository: SubjectRepository,
    private val teacherProfileRepository: TeacherProfileRepository,
    private val teacherSubjectRepository: TeacherSubjectRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val teacherStudentSubjectRepository: TeacherStudentSubjectRepository,
    private val teacherInviteCodeRepository: TeacherInviteCodeRepository,
) {
    private val now: Instant = Instant.parse("2026-05-19T00:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `creating a new invite code revokes the teacher previous active code`() {
        val fixture = createTeacherStudentFixture()
        val first = service.createInviteCode(
            teacherId = fixture.teacher.id!!,
            code = "FIRST026",
            expiresAt = now.plusSeconds(86_400),
            clock = clock,
        )

        val second = service.createInviteCode(
            teacherId = fixture.teacher.id!!,
            code = "SECOND26",
            expiresAt = now.plusSeconds(86_400),
            clock = clock,
        )

        assertThat(teacherInviteCodeRepository.findById(first.id!!).orElseThrow().status)
            .isEqualTo(InviteCodeStatus.REVOKED)
        assertThat(teacherInviteCodeRepository.findById(second.id!!).orElseThrow().status)
            .isEqualTo(InviteCodeStatus.ACTIVE)
        assertThat(teacherInviteCodeRepository.findAll().filter { it.status == InviteCodeStatus.ACTIVE })
            .hasSize(1)
    }

    @Test
    fun `auth session requires a session or refresh token hash`() {
        val fixture = createTeacherStudentFixture()
        assertThatThrownBy {
            authSessionRepository.save(
                AuthSession(
                    user = fixture.teacher.user,
                    expiresAt = now.plusSeconds(3_600),
                    csrfTokenHash = "csrf-only",
                ),
            )
            entityManager.flush()
        }.hasRootCauseInstanceOf(IllegalArgumentException::class.java)
            .rootCause()
            .hasMessage("Auth session requires a session or refresh token hash.")
    }

    @Test
    fun `consume invite code creates one relationship and prevents reuse`() {
        val fixture = createTeacherStudentFixture()
        val invite = service.createInviteCode(
            teacherId = fixture.teacher.id!!,
            code = "A1B2C3D4",
            expiresAt = now.plusSeconds(86_400),
            clock = clock,
        )

        val relationship = service.consumeInviteCode(
            code = invite.code,
            studentId = fixture.student.id!!,
            clock = clock,
        )

        assertThat(relationship.teacher.id).isEqualTo(fixture.teacher.id)
        assertThat(relationship.student.id).isEqualTo(fixture.student.id)
        assertThat(relationship.active).isTrue()
        assertThat(teacherInviteCodeRepository.findById(invite.id!!).orElseThrow().status)
            .isEqualTo(InviteCodeStatus.REVOKED)

        assertThatThrownBy {
            service.consumeInviteCode(invite.code, fixture.student.id!!, clock)
        }.isInstanceOf(InviteCodeNotConsumableException::class.java)

        assertThat(teacherStudentRepository.findAll()).hasSize(1)
    }

    @Test
    fun `consume invite code applies teacher default subject to student relationship`() {
        val fixture = createTeacherStudentFixture(defaultSubject = true)
        val invite = service.createInviteCode(
            teacherId = fixture.teacher.id!!,
            code = "MATH2026",
            expiresAt = now.plusSeconds(86_400),
            clock = clock,
        )

        val relationship = service.consumeInviteCode(invite.code, fixture.student.id!!, clock)

        assertThat(relationship.defaultSubject?.id).isEqualTo(fixture.subject.id)

        val subjects = teacherStudentSubjectRepository.findByTeacherStudent_Id(relationship.id!!)
        assertThat(subjects).hasSize(1)
        assertThat(subjects.first().subject.id).isEqualTo(fixture.subject.id)
        assertThat(subjects.first().primary).isTrue()
    }

    @Test
    fun `consume invite code fails when teacher has multiple default subjects`() {
        val fixture = createTeacherStudentFixture(defaultSubject = true)
        val secondSubject = subjectRepository.save(Subject(code = "SCI-${System.nanoTime()}", name = "과학"))
        teacherSubjectRepository.save(
            TeacherSubject(teacher = fixture.teacher, subject = secondSubject, default = true),
        )
        val invite = service.createInviteCode(
            teacherId = fixture.teacher.id!!,
            code = "DUPL2026",
            expiresAt = now.plusSeconds(86_400),
            clock = clock,
        )

        assertThatThrownBy {
            service.consumeInviteCode(invite.code, fixture.student.id!!, clock)
        }.isInstanceOf(TeacherDefaultSubjectNotUniqueException::class.java)
    }

    private fun createTeacherStudentFixture(defaultSubject: Boolean = false): Fixture {
        val teacherUser = userRepository.save(
            User(name = "김선생", role = UserRole.TEACHER, email = "teacher-${System.nanoTime()}@example.com"),
        )
        val studentUser = userRepository.save(
            User(name = "홍길동", role = UserRole.STUDENT, email = "student-${System.nanoTime()}@example.com"),
        )
        val teacher = teacherProfileRepository.save(
            TeacherProfile(user = teacherUser, displayName = "김선생"),
        )
        val student = studentProfileRepository.save(
            StudentProfile(user = studentUser, name = "홍길동"),
        )
        val subject = subjectRepository.save(Subject(code = "MATH-${System.nanoTime()}", name = "수학"))

        if (defaultSubject) {
            teacherSubjectRepository.save(
                TeacherSubject(teacher = teacher, subject = subject, default = true),
            )
        }

        return Fixture(teacher = teacher, student = student, subject = subject)
    }

    private data class Fixture(
        val teacher: TeacherProfile,
        val student: StudentProfile,
        val subject: Subject,
    )
}

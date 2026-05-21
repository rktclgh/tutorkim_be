package com.tutorkim.backend.student.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tutorkim.backend.identity.entity.User
import com.tutorkim.backend.identity.entity.UserRole
import com.tutorkim.backend.identity.repository.UserRepository
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
import org.springframework.test.web.servlet.put

@SpringBootTest
@AutoConfigureMockMvc
class StudentManagementControllerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val teacherProfileRepository: TeacherProfileRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val subjectRepository: SubjectRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val teacherStudentSubjectRepository: TeacherStudentSubjectRepository,
) {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `teacher can list active students and filter by subject`() {
        val fixture = createFixture()
        createRelationship(fixture, fixture.math, active = true)
        val inactiveRelationship = createRelationship(
            fixture,
            fixture.science,
            active = false,
            studentName = "비활성학생",
        )

        mockMvc.get("/api/v1/students") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].student.name") { value("홍길동") }
            jsonPath("$.data[0].subjects[0].code") { value(fixture.math.code) }
            jsonPath("$.data[0].subjects[0].primary") { value(true) }
            jsonPath("$.data[0].active") { value(true) }
        }

        mockMvc.get("/api/v1/students") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("active", "false")
            param("subjectId", fixture.science.id!!.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].id") { value(inactiveRelationship.id!!.toString()) }
            jsonPath("$.data[0].student.name") { value("비활성학생") }
            jsonPath("$.data[0].active") { value(false) }
        }
    }

    @Test
    fun `teacher can update subjects for an active student relationship`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture, fixture.math, active = true)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "subjectIds" to listOf(fixture.science.id!!.toString()),
                "primarySubjectId" to fixture.science.id!!.toString(),
            ),
        )

        mockMvc.put("/api/v1/students/${fixture.studentProfile.id}/subjects") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(relationship.id!!.toString()) }
            jsonPath("$.data.defaultSubject.id") { value(fixture.science.id!!.toString()) }
            jsonPath("$.data.subjects.length()") { value(1) }
            jsonPath("$.data.subjects[0].id") { value(fixture.science.id!!.toString()) }
            jsonPath("$.data.subjects[0].primary") { value(true) }
        }

        val savedRelationship = teacherStudentRepository.findById(relationship.id!!).orElseThrow()
        assertThat(savedRelationship.defaultSubject?.id).isEqualTo(fixture.science.id)
        val savedSubject = teacherStudentSubjectRepository.findByTeacherStudent_Id(relationship.id!!).single()
        assertThat(savedSubject.subject.id).isEqualTo(fixture.science.id)
        assertThat(savedSubject.primary).isTrue()
    }

    @Test
    fun `teacher can change the primary subject while retaining existing subject rows`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture, fixture.math, active = true)
        teacherStudentSubjectRepository.save(
            TeacherStudentSubject(
                teacherStudent = relationship,
                subject = fixture.science,
                primary = false,
            ),
        )
        val body = objectMapper.writeValueAsString(
            mapOf(
                "subjectIds" to listOf(fixture.math.id!!.toString(), fixture.science.id!!.toString()),
                "primarySubjectId" to fixture.science.id!!.toString(),
            ),
        )

        mockMvc.put("/api/v1/students/${fixture.studentProfile.id}/subjects") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.defaultSubject.id") { value(fixture.science.id!!.toString()) }
            jsonPath("$.data.subjects.length()") { value(2) }
            jsonPath("$.data.subjects[0].id") { value(fixture.science.id!!.toString()) }
            jsonPath("$.data.subjects[0].primary") { value(true) }
        }

        val savedSubjects = teacherStudentSubjectRepository.findByTeacherStudent_Id(relationship.id!!)
        assertThat(savedSubjects).hasSize(2)
        assertThat(savedSubjects.single { it.subject.id == fixture.science.id }.primary).isTrue()
        assertThat(savedSubjects.single { it.subject.id == fixture.math.id }.primary).isFalse()
    }

    @Test
    fun `student role cannot list or update teacher student subjects`() {
        val fixture = createFixture()
        createRelationship(fixture, fixture.math, active = true)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "subjectIds" to listOf(fixture.science.id!!.toString()),
                "primarySubjectId" to fixture.science.id!!.toString(),
            ),
        )

        mockMvc.get("/api/v1/students") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }

        mockMvc.put("/api/v1/students/${fixture.studentProfile.id}/subjects") {
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
    fun `teacher cannot update subjects when active relationship is missing`() {
        val fixture = createFixture()
        createRelationship(fixture, fixture.math, active = false)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "subjectIds" to listOf(fixture.science.id!!.toString()),
                "primarySubjectId" to fixture.science.id!!.toString(),
            ),
        )

        mockMvc.put("/api/v1/students/${fixture.studentProfile.id}/subjects") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("활성 학생 관계를 찾을 수 없습니다.") }
        }
    }

    @Test
    fun `primary subject must be included in requested subjects`() {
        val fixture = createFixture()
        createRelationship(fixture, fixture.math, active = true)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "subjectIds" to listOf(fixture.math.id!!.toString()),
                "primarySubjectId" to fixture.science.id!!.toString(),
            ),
        )

        mockMvc.put("/api/v1/students/${fixture.studentProfile.id}/subjects") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("기본 과목은 과목 목록에 포함되어야 합니다.") }
        }
    }

    private fun createRelationship(
        fixture: Fixture,
        subject: Subject,
        active: Boolean,
        studentName: String = fixture.studentProfile.name,
    ): TeacherStudent {
        val studentProfile = if (studentName == fixture.studentProfile.name) {
            fixture.studentProfile
        } else {
            studentProfileRepository.save(
                StudentProfile(name = studentName, school = "OO고", grade = "고2"),
            )
        }
        val relationship = teacherStudentRepository.save(
            TeacherStudent(
                teacher = fixture.teacherProfile,
                student = studentProfile,
                defaultSubject = subject,
                active = active,
            ),
        )
        teacherStudentSubjectRepository.save(
            TeacherStudentSubject(
                teacherStudent = relationship,
                subject = subject,
                primary = true,
            ),
        )
        return relationship
    }

    private fun createFixture(): Fixture {
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
            StudentProfile(
                user = studentUser,
                name = "홍길동",
                school = "OO고",
                grade = "고2",
                phone = "010-0000-0000",
                parentPhone = "010-1111-1111",
            ),
        )
        val math = subjectRepository.save(Subject(code = "MATH-$suffix", name = "수학"))
        val science = subjectRepository.save(Subject(code = "SCI-$suffix", name = "과학"))

        return Fixture(
            teacherUser = teacherUser,
            studentUser = studentUser,
            teacherProfile = teacherProfile,
            studentProfile = studentProfile,
            math = math,
            science = science,
        )
    }

    private data class Fixture(
        val teacherUser: User,
        val studentUser: User,
        val teacherProfile: TeacherProfile,
        val studentProfile: StudentProfile,
        val math: Subject,
        val science: Subject,
    )
}

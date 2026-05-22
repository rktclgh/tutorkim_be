package com.tutorkim.backend.lesson.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tutorkim.backend.identity.entity.User
import com.tutorkim.backend.identity.entity.UserRole
import com.tutorkim.backend.identity.repository.UserRepository
import com.tutorkim.backend.lesson.repository.LessonScheduleRepository
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
import org.springframework.test.web.servlet.post
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class LessonScheduleControllerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val teacherProfileRepository: TeacherProfileRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val subjectRepository: SubjectRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val teacherStudentSubjectRepository: TeacherStudentSubjectRepository,
    private val lessonScheduleRepository: LessonScheduleRepository,
    private val lessonSessionRepository: LessonSessionRepository,
) {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `teacher can create recurring lesson schedule for an active student subject`() {
        val fixture = createFixture()
        createRelationship(fixture, active = true)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "studentId" to fixture.studentProfile.id!!.toString(),
                "subjectId" to fixture.math.id!!.toString(),
                "dayOfWeek" to 0,
                "startTime" to "19:00",
                "endTime" to "21:00",
                "timezone" to "Asia/Seoul",
            ),
        )

        mockMvc.post("/api/v1/lesson-schedules") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { exists() }
            jsonPath("$.data.studentId") { value(fixture.studentProfile.id!!.toString()) }
            jsonPath("$.data.subjectId") { value(fixture.math.id!!.toString()) }
            jsonPath("$.data.dayOfWeek") { value(0) }
            jsonPath("$.data.startTime") { value("19:00:00") }
            jsonPath("$.data.endTime") { value("21:00:00") }
            jsonPath("$.data.timezone") { value("Asia/Seoul") }
            jsonPath("$.data.active") { value(true) }
        }

        val savedSchedule = lessonScheduleRepository.findAll().single { it.subjectId == fixture.math.id }
        assertThat(savedSchedule.teacherStudentId).isNotNull()
        assertThat(savedSchedule.subjectId).isEqualTo(fixture.math.id)
    }

    @Test
    fun `lesson schedule day of week follows ddl boundary zero to six`() {
        val fixture = createFixture()
        createRelationship(fixture, active = true)
        val validBody = objectMapper.writeValueAsString(
            mapOf(
                "studentId" to fixture.studentProfile.id!!.toString(),
                "subjectId" to fixture.math.id!!.toString(),
                "dayOfWeek" to 6,
                "startTime" to "19:00",
                "endTime" to "21:00",
                "timezone" to "Asia/Seoul",
            ),
        )
        val invalidBody = objectMapper.writeValueAsString(
            mapOf(
                "studentId" to fixture.studentProfile.id!!.toString(),
                "subjectId" to fixture.math.id!!.toString(),
                "dayOfWeek" to 7,
                "startTime" to "19:00",
                "endTime" to "21:00",
                "timezone" to "Asia/Seoul",
            ),
        )

        mockMvc.post("/api/v1/lesson-schedules") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = validBody
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.dayOfWeek") { value(6) }
        }

        mockMvc.post("/api/v1/lesson-schedules") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = invalidBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
        }
    }

    @Test
    fun `teacher can create lesson session and read it from the home timetable`() {
        val fixture = createFixture()
        createRelationship(fixture, active = true)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "studentId" to fixture.studentProfile.id!!.toString(),
                "subjectId" to fixture.math.id!!.toString(),
                "scheduledStartAt" to "2026-05-18T19:00:00+09:00",
                "scheduledEndAt" to "2026-05-18T21:00:00+09:00",
            ),
        )

        mockMvc.post("/api/v1/lesson-sessions") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { exists() }
            jsonPath("$.data.studentId") { value(fixture.studentProfile.id!!.toString()) }
            jsonPath("$.data.subjectId") { value(fixture.math.id!!.toString()) }
            jsonPath("$.data.scheduledStartAt") { value("2026-05-18T10:00:00Z") }
            jsonPath("$.data.scheduledEndAt") { value("2026-05-18T12:00:00Z") }
            jsonPath("$.data.status") { value("SCHEDULED") }
        }

        mockMvc.get("/api/v1/home/timetable") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("from", "2026-05-18")
            param("to", "2026-05-24")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].date") { value("2026-05-18") }
            jsonPath("$.data[0].lessons.length()") { value(1) }
            jsonPath("$.data[0].lessons[0].studentId") { value(fixture.studentProfile.id!!.toString()) }
            jsonPath("$.data[0].lessons[0].subjectId") { value(fixture.math.id!!.toString()) }
        }

        assertThat(lessonSessionRepository.findAll()).hasSize(1)
    }

    @Test
    fun `student role cannot create lesson schedules or sessions`() {
        val fixture = createFixture()
        createRelationship(fixture, active = true)
        val scheduleBody = objectMapper.writeValueAsString(
            mapOf(
                "studentId" to fixture.studentProfile.id!!.toString(),
                "subjectId" to fixture.math.id!!.toString(),
                "dayOfWeek" to 2,
                "startTime" to "19:00",
                "endTime" to "21:00",
                "timezone" to "Asia/Seoul",
            ),
        )
        val sessionBody = objectMapper.writeValueAsString(
            mapOf(
                "studentId" to fixture.studentProfile.id!!.toString(),
                "subjectId" to fixture.math.id!!.toString(),
                "scheduledStartAt" to "2026-05-18T19:00:00+09:00",
                "scheduledEndAt" to "2026-05-18T21:00:00+09:00",
            ),
        )

        mockMvc.post("/api/v1/lesson-schedules") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = scheduleBody
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }

        mockMvc.post("/api/v1/lesson-sessions") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = sessionBody
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `lesson creation rejects inactive relationships unassigned subjects and invalid time ranges`() {
        val fixture = createFixture()
        createRelationship(fixture, active = false)
        val inactiveBody = objectMapper.writeValueAsString(
            mapOf(
                "studentId" to fixture.studentProfile.id!!.toString(),
                "subjectId" to fixture.math.id!!.toString(),
                "dayOfWeek" to 2,
                "startTime" to "19:00",
                "endTime" to "21:00",
                "timezone" to "Asia/Seoul",
            ),
        )

        mockMvc.post("/api/v1/lesson-schedules") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = inactiveBody
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("활성 학생 관계를 찾을 수 없습니다.") }
        }

        val activeFixture = createFixture()
        createRelationship(activeFixture, active = true)
        val unassignedSubjectBody = objectMapper.writeValueAsString(
            mapOf(
                "studentId" to activeFixture.studentProfile.id!!.toString(),
                "subjectId" to activeFixture.science.id!!.toString(),
                "scheduledStartAt" to "2026-05-18T19:00:00+09:00",
                "scheduledEndAt" to "2026-05-18T21:00:00+09:00",
            ),
        )

        mockMvc.post("/api/v1/lesson-sessions") {
            with(user(activeFixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = unassignedSubjectBody
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("학생 과목을 찾을 수 없습니다.") }
        }

        val invalidTimeBody = objectMapper.writeValueAsString(
            mapOf(
                "studentId" to activeFixture.studentProfile.id!!.toString(),
                "subjectId" to activeFixture.math.id!!.toString(),
                "dayOfWeek" to 2,
                "startTime" to "21:00",
                "endTime" to "19:00",
                "timezone" to "Asia/Seoul",
            ),
        )

        mockMvc.post("/api/v1/lesson-schedules") {
            with(user(activeFixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = invalidTimeBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("종료 시간은 시작 시간 이후여야 합니다.") }
        }
    }

    @Test
    fun `lesson creation rejects deleted students invalid timezone and oversized timetable ranges`() {
        val fixture = createFixture()
        createRelationship(fixture, active = true)
        fixture.studentProfile.deletedAt = Instant.now()
        studentProfileRepository.save(fixture.studentProfile)
        val scheduleCountBeforeDeletedStudentRequests = lessonScheduleRepository.count()
        val sessionCountBeforeDeletedStudentRequests = lessonSessionRepository.count()
        val deletedStudentScheduleBody = objectMapper.writeValueAsString(
            mapOf(
                "studentId" to fixture.studentProfile.id!!.toString(),
                "subjectId" to fixture.math.id!!.toString(),
                "dayOfWeek" to 2,
                "startTime" to "19:00",
                "endTime" to "21:00",
                "timezone" to "Asia/Seoul",
            ),
        )
        val deletedStudentSessionBody = objectMapper.writeValueAsString(
            mapOf(
                "studentId" to fixture.studentProfile.id!!.toString(),
                "subjectId" to fixture.math.id!!.toString(),
                "scheduledStartAt" to "2026-05-18T19:00:00+09:00",
                "scheduledEndAt" to "2026-05-18T21:00:00+09:00",
            ),
        )

        mockMvc.post("/api/v1/lesson-schedules") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = deletedStudentScheduleBody
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("활성 학생 관계를 찾을 수 없습니다.") }
        }

        mockMvc.post("/api/v1/lesson-sessions") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = deletedStudentSessionBody
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("활성 학생 관계를 찾을 수 없습니다.") }
        }

        assertThat(lessonScheduleRepository.count()).isEqualTo(scheduleCountBeforeDeletedStudentRequests)
        assertThat(lessonSessionRepository.count()).isEqualTo(sessionCountBeforeDeletedStudentRequests)

        val activeFixture = createFixture()
        createRelationship(activeFixture, active = true)
        val invalidTimezoneBody = objectMapper.writeValueAsString(
            mapOf(
                "studentId" to activeFixture.studentProfile.id!!.toString(),
                "subjectId" to activeFixture.math.id!!.toString(),
                "dayOfWeek" to 2,
                "startTime" to "19:00",
                "endTime" to "21:00",
                "timezone" to "not-a-zone",
            ),
        )

        mockMvc.post("/api/v1/lesson-schedules") {
            with(user(activeFixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = invalidTimezoneBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("시간대가 유효하지 않습니다.") }
        }

        mockMvc.get("/api/v1/home/timetable") {
            with(user(activeFixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("from", "2026-05-01")
            param("to", "2026-06-15")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("시간표 조회 범위는 최대 31일입니다.") }
        }
    }

    private fun createRelationship(
        fixture: Fixture,
        active: Boolean,
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
        val suffix = System.nanoTime()
        val teacherUser = userRepository.save(
            User(name = "김선생", role = UserRole.TEACHER, email = "lesson-teacher-$suffix@example.com"),
        )
        val studentUser = userRepository.save(
            User(name = "홍길동", role = UserRole.STUDENT, email = "lesson-student-$suffix@example.com"),
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
            ),
        )
        val math = subjectRepository.save(Subject(code = "LESSON-MATH-$suffix", name = "수학"))
        val science = subjectRepository.save(Subject(code = "LESSON-SCI-$suffix", name = "과학"))

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

package com.tutorkim.backend.assignment.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.repository.AssignmentProblemRepository
import com.tutorkim.backend.assignment.repository.AssignmentRepository
import com.tutorkim.backend.assignment.repository.AssignmentSubmissionRepository
import com.tutorkim.backend.assignment.repository.AssignmentTargetRepository
import com.tutorkim.backend.content.entity.CurriculumNode
import com.tutorkim.backend.content.repository.CurriculumNodeRepository
import com.tutorkim.backend.identity.entity.User
import com.tutorkim.backend.identity.entity.UserRole
import com.tutorkim.backend.identity.repository.UserRepository
import com.tutorkim.backend.lesson.entity.LessonSession
import com.tutorkim.backend.lesson.repository.LessonSessionRepository
import com.tutorkim.backend.problem.entity.ParseStatus
import com.tutorkim.backend.problem.entity.Problem
import com.tutorkim.backend.problem.entity.ProblemAnswerType
import com.tutorkim.backend.problem.entity.ProblemBlock
import com.tutorkim.backend.problem.entity.ProblemBlockType
import com.tutorkim.backend.problem.repository.ProblemBlockRepository
import com.tutorkim.backend.problem.repository.ProblemRepository
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
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class AssignmentManagementControllerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val teacherProfileRepository: TeacherProfileRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val subjectRepository: SubjectRepository,
    private val curriculumNodeRepository: CurriculumNodeRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val teacherStudentSubjectRepository: TeacherStudentSubjectRepository,
    private val lessonSessionRepository: LessonSessionRepository,
    private val problemRepository: ProblemRepository,
    private val problemBlockRepository: ProblemBlockRepository,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentProblemRepository: AssignmentProblemRepository,
    private val assignmentTargetRepository: AssignmentTargetRepository,
    private val assignmentSubmissionRepository: AssignmentSubmissionRepository,
) {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `teacher creates assignment draft for active student and owned problems`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val problems = createProblems(fixture.teacherProfile.id!!, fixture.math.id!!, 2)
        val lessonSession = createLessonSession(relationship, fixture.math)

        mockMvc.post("/api/v1/assignments") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = createAssignmentBody(fixture, problems.map { it.id!! }, lessonSession.id!!)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { exists() }
            jsonPath("$.data.title") { value(fixture.assignmentTitle) }
            jsonPath("$.data.assignmentType") { value("HOMEWORK") }
            jsonPath("$.data.status") { value("DRAFT") }
            jsonPath("$.data.student.id") { value(fixture.studentProfile.id!!.toString()) }
            jsonPath("$.data.student.name") { value("홍길동") }
            jsonPath("$.data.problemCount") { value(2) }
            jsonPath("$.data.questionCount") { value(0) }
            jsonPath("$.data.expired") { value(false) }
            jsonPath("$.data.canSolve") { value(false) }
            jsonPath("$.data.submissionStatus") { value("NOT_SUBMITTED") }
        }

        val savedAssignment = assignmentRepository.findAll().single { it.title == fixture.assignmentTitle }
        assertThat(savedAssignment.teacherId).isEqualTo(fixture.teacherProfile.id!!)
        assertThat(savedAssignment.teacherStudentId).isEqualTo(relationship.id!!)
        assertThat(savedAssignment.lessonSessionId).isEqualTo(lessonSession.id!!)
        assertThat(savedAssignment.status).isEqualTo(AssignmentStatus.DRAFT)
        assertThat(assignmentTargetRepository.findAll().filter { it.assignmentId == savedAssignment.id }).hasSize(1)
        assertThat(assignmentSubmissionRepository.findAll().filter { it.assignmentId == savedAssignment.id }).isEmpty()
        assertThat(
            assignmentProblemRepository.findAll()
                .filter { it.assignmentId == savedAssignment.id }
                .sortedBy { it.sortOrder }
                .map { it.problemId },
        ).containsExactly(problems[0].id, problems[1].id)
    }

    @Test
    fun `teacher publishes draft and lists assignment with submission availability`() {
        val fixture = createFixture()
        createRelationship(fixture)
        val problems = createProblems(fixture.teacherProfile.id!!, fixture.math.id!!, 1)
        val assignmentId = createDraftThroughApi(fixture, problems.map { it.id!! })
        val draftUpdatedAt = assignmentRepository.findById(assignmentId).orElseThrow().updatedAt

        mockMvc.post("/api/v1/assignments/$assignmentId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(assignmentId.toString()) }
            jsonPath("$.data.status") { value("PUBLISHED") }
            jsonPath("$.data.submissionStatus") { value("NOT_SUBMITTED") }
            jsonPath("$.data.canSolve") { value(true) }
        }

        val savedAssignment = assignmentRepository.findById(assignmentId).orElseThrow()
        assertThat(savedAssignment.status).isEqualTo(AssignmentStatus.PUBLISHED)
        assertThat(savedAssignment.publishedAt).isNotNull()
        assertThat(savedAssignment.updatedAt).isAfter(draftUpdatedAt)
        assertThat(assignmentSubmissionRepository.findAll().filter { it.assignmentId == assignmentId }).hasSize(1)

        mockMvc.get("/api/v1/assignments") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("studentId", fixture.studentProfile.id!!.toString())
            param("type", "HOMEWORK")
            param("status", "PUBLISHED")
            param("limit", "50")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].id") { value(assignmentId.toString()) }
            jsonPath("$.data[0].problemCount") { value(1) }
            jsonPath("$.data[0].questionCount") { value(0) }
            jsonPath("$.data[0].status") { value("PUBLISHED") }
            jsonPath("$.data[0].submissionStatus") { value("NOT_SUBMITTED") }
            jsonPath("$.data[0].expired") { value(false) }
            jsonPath("$.data[0].canSolve") { value(true) }
        }
    }

    @Test
    fun `teacher still lists historical assignments after relationship deactivation`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val problems = createProblems(fixture.teacherProfile.id!!, fixture.math.id!!, 1)
        val assignmentId = createDraftThroughApi(fixture, problems.map { it.id!! })

        mockMvc.post("/api/v1/assignments/$assignmentId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
        }

        relationship.active = false
        teacherStudentRepository.saveAndFlush(relationship)

        mockMvc.get("/api/v1/assignments") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("studentId", fixture.studentProfile.id!!.toString())
            param("status", "PUBLISHED")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].id") { value(assignmentId.toString()) }
            jsonPath("$.data[0].student.id") { value(fixture.studentProfile.id!!.toString()) }
            jsonPath("$.data[0].status") { value("PUBLISHED") }
            jsonPath("$.data[0].canSolve") { value(false) }
        }
    }

    @Test
    fun `assignment list rejects invalid filters as bad request`() {
        val fixture = createFixture()

        mockMvc.get("/api/v1/assignments") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("type", "QUIZ")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }

        mockMvc.get("/api/v1/assignments") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("status", "OPEN")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }

        mockMvc.get("/api/v1/assignments") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("limit", "101")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("조회 개수는 1개 이상 100개 이하로 지정해야 합니다.") }
        }
    }

    @Test
    fun `assignment creation enforces active relationship subject and owned active problems`() {
        val fixture = createFixture()
        createRelationship(fixture)
        val otherFixture = createFixture()
        createRelationship(otherFixture)
        val ownedMathProblem = createProblems(fixture.teacherProfile.id!!, fixture.math.id!!, 1).single()
        val otherTeacherProblem = createProblems(otherFixture.teacherProfile.id!!, otherFixture.math.id!!, 1).single()
        val scienceProblem = createProblems(fixture.teacherProfile.id!!, fixture.science.id!!, 1).single()

        mockMvc.post("/api/v1/assignments") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = createAssignmentBody(fixture, listOf(ownedMathProblem.id!!), subjectId = fixture.science.id!!)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("학생 과목을 찾을 수 없습니다.") }
        }

        mockMvc.post("/api/v1/assignments") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = createAssignmentBody(fixture, listOf(otherTeacherProblem.id!!))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("문제를 찾을 수 없습니다.") }
        }

        mockMvc.post("/api/v1/assignments") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = createAssignmentBody(fixture, listOf(scienceProblem.id!!))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("과제 문제는 과제 과목과 같아야 합니다.") }
        }

        mockMvc.post("/api/v1/assignments") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = createAssignmentBody(fixture, listOf(ownedMathProblem.id!!, ownedMathProblem.id!!))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("과제 문제는 중복될 수 없습니다.") }
        }
    }

    @Test
    fun `assignment routes enforce teacher role and publish ownership state`() {
        val fixture = createFixture()
        createRelationship(fixture)
        val otherFixture = createFixture()
        createRelationship(otherFixture)
        val problems = createProblems(fixture.teacherProfile.id!!, fixture.math.id!!, 1)
        val assignmentId = createDraftThroughApi(fixture, problems.map { it.id!! })

        mockMvc.post("/api/v1/assignments") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = createAssignmentBody(fixture, problems.map { it.id!! })
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }

        mockMvc.get("/api/v1/assignments") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }

        mockMvc.post("/api/v1/assignments/$assignmentId/publish") {
            with(user(otherFixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }

        mockMvc.post("/api/v1/assignments/$assignmentId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
        }

        mockMvc.post("/api/v1/assignments/$assignmentId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("CONFLICT") }
            jsonPath("$.error.message") { value("초안 상태의 과제만 발행할 수 있습니다.") }
        }
    }

    private fun createDraftThroughApi(
        fixture: Fixture,
        problemIds: List<UUID>,
    ): UUID {
        mockMvc.post("/api/v1/assignments") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = createAssignmentBody(fixture, problemIds)
        }.andExpect {
            status { isOk() }
        }
        return assignmentRepository.findAll()
            .single { it.title == fixture.assignmentTitle && it.teacherId == fixture.teacherProfile.id!! }
            .id!!
    }

    private fun createAssignmentBody(
        fixture: Fixture,
        problemIds: List<UUID>,
        lessonSessionId: UUID? = null,
        subjectId: UUID = fixture.math.id!!,
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "studentId" to fixture.studentProfile.id!!.toString(),
                "lessonSessionId" to lessonSessionId?.toString(),
                "subjectId" to subjectId.toString(),
                "title" to fixture.assignmentTitle,
                "assignmentType" to "HOMEWORK",
                "resultVisibility" to "HIDDEN_UNTIL_RELEASED",
                "dueAt" to "2030-05-20T23:59:00+09:00",
                "problemIds" to problemIds.map(UUID::toString),
            ),
        )

    private fun createRelationship(fixture: Fixture): TeacherStudent {
        val relationship = teacherStudentRepository.save(
            TeacherStudent(
                teacher = fixture.teacherProfile,
                student = fixture.studentProfile,
                defaultSubject = fixture.math,
                active = true,
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

    private fun createLessonSession(
        relationship: TeacherStudent,
        subject: Subject,
    ): LessonSession =
        lessonSessionRepository.save(
            LessonSession(
                teacherStudentId = relationship.id!!,
                subjectId = subject.id!!,
                scheduledStartAt = Instant.parse("2030-05-19T10:00:00Z"),
                scheduledEndAt = Instant.parse("2030-05-19T12:00:00Z"),
            ),
        )

    private fun createProblems(
        teacherId: UUID,
        subjectId: UUID,
        count: Int,
    ): List<Problem> {
        val labels = createLabelFixture(subjectId)
        return (1..count).map { number ->
            val problem = problemRepository.saveAndFlush(
                Problem(
                    ownerTeacherId = teacherId,
                    subjectId = subjectId,
                    answerType = ProblemAnswerType.SINGLE_CHOICE,
                    correctChoiceNumbers = listOf(1.toShort()),
                    difficulty = 3,
                    labelDepth1Id = labels.depth1Id,
                    labelDepth2Id = labels.depth2Id,
                    labelDepth3Id = labels.depth3Id,
                    parseStatus = ParseStatus.REVIEWED,
                    reviewedAt = Instant.now(),
                ),
            )
            problemBlockRepository.save(
                ProblemBlock(
                    problemId = problem.id!!,
                    blockType = ProblemBlockType.TEXT,
                    sortOrder = 1,
                    textContent = "문제 $number",
                ),
            )
            problem
        }
    }

    private fun createLabelFixture(subjectId: UUID): LabelFixture {
        val depth1 = curriculumNodeRepository.save(CurriculumNode(subjectId = subjectId, depth = 1, name = "대단원-${UUID.randomUUID()}", system = true))
        val depth2 = curriculumNodeRepository.save(CurriculumNode(subjectId = subjectId, parentId = depth1.id, depth = 2, name = "중단원", system = true))
        val depth3 = curriculumNodeRepository.save(CurriculumNode(subjectId = subjectId, parentId = depth2.id, depth = 3, name = "소단원", system = true))
        return LabelFixture(depth1.id!!, depth2.id!!, depth3.id!!)
    }

    private fun createFixture(): Fixture {
        val suffix = UUID.randomUUID().toString().take(8)
        val teacherUser = userRepository.save(
            User(name = "김선생", role = UserRole.TEACHER, email = "assignment-teacher-$suffix@example.com"),
        )
        val studentUser = userRepository.save(
            User(name = "홍길동", role = UserRole.STUDENT, email = "assignment-student-$suffix@example.com"),
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
        val math = subjectRepository.save(Subject(code = "ASSIGNMENT-MATH-$suffix", name = "수학"))
        val science = subjectRepository.save(Subject(code = "ASSIGNMENT-SCI-$suffix", name = "과학"))

        return Fixture(
            teacherUser = teacherUser,
            studentUser = studentUser,
            teacherProfile = teacherProfile,
            studentProfile = studentProfile,
            math = math,
            science = science,
            assignmentTitle = "조합 복습 숙제-$suffix",
        )
    }

    private data class Fixture(
        val teacherUser: User,
        val studentUser: User,
        val teacherProfile: TeacherProfile,
        val studentProfile: StudentProfile,
        val math: Subject,
        val science: Subject,
        val assignmentTitle: String,
    )

    private data class LabelFixture(
        val depth1Id: UUID,
        val depth2Id: UUID,
        val depth3Id: UUID,
    )
}

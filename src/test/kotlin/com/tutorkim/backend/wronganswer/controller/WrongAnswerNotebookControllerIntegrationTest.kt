package com.tutorkim.backend.wronganswer.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tutorkim.backend.assignment.entity.Assignment
import com.tutorkim.backend.assignment.entity.AssignmentProblem
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.entity.AssignmentSubmission
import com.tutorkim.backend.assignment.entity.AssignmentTarget
import com.tutorkim.backend.assignment.entity.AssignmentType
import com.tutorkim.backend.assignment.entity.GradingStatus
import com.tutorkim.backend.assignment.entity.ProblemAttemptStatus
import com.tutorkim.backend.assignment.entity.ResultVisibility
import com.tutorkim.backend.assignment.entity.SubmissionAnswer
import com.tutorkim.backend.assignment.entity.SubmissionStatus
import com.tutorkim.backend.assignment.repository.AssignmentProblemRepository
import com.tutorkim.backend.assignment.repository.AssignmentRepository
import com.tutorkim.backend.assignment.repository.AssignmentSubmissionRepository
import com.tutorkim.backend.assignment.repository.AssignmentTargetRepository
import com.tutorkim.backend.assignment.repository.SubmissionAnswerRepository
import com.tutorkim.backend.content.entity.CurriculumNode
import com.tutorkim.backend.content.repository.CurriculumNodeRepository
import com.tutorkim.backend.identity.entity.User
import com.tutorkim.backend.identity.entity.UserRole
import com.tutorkim.backend.identity.repository.UserRepository
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
import com.tutorkim.backend.wronganswer.entity.WrongAnswerNotebook
import com.tutorkim.backend.wronganswer.entity.WrongAnswerNotebookProblem
import com.tutorkim.backend.wronganswer.entity.WrongAnswerNotebookStatus
import com.tutorkim.backend.wronganswer.repository.WrongAnswerNotebookProblemRepository
import com.tutorkim.backend.wronganswer.repository.WrongAnswerNotebookRepository
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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class WrongAnswerNotebookControllerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val teacherProfileRepository: TeacherProfileRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val subjectRepository: SubjectRepository,
    private val curriculumNodeRepository: CurriculumNodeRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val teacherStudentSubjectRepository: TeacherStudentSubjectRepository,
    private val problemRepository: ProblemRepository,
    private val problemBlockRepository: ProblemBlockRepository,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentProblemRepository: AssignmentProblemRepository,
    private val assignmentTargetRepository: AssignmentTargetRepository,
    private val assignmentSubmissionRepository: AssignmentSubmissionRepository,
    private val submissionAnswerRepository: SubmissionAnswerRepository,
    private val wrongAnswerNotebookRepository: WrongAnswerNotebookRepository,
    private val wrongAnswerNotebookProblemRepository: WrongAnswerNotebookProblemRepository,
) {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `teacher creates wrong-answer notebook draft and publishes review assignment`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sourceAssignmentProblem = createPublishedSourceAssignment(fixture, relationship, "삼각함수 숙제")

        val draftResponse = mockMvc.post("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebooks") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = notebookBody(fixture, sourceAssignmentProblem.id!!, "MATH-${sourceAssignmentProblem.problemId}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { exists() }
            jsonPath("$.data.assignmentId") { value(null) }
            jsonPath("$.data.title") { value("6월 1주차 오답노트") }
            jsonPath("$.data.status") { value("DRAFT") }
            jsonPath("$.data.problemCount") { value(1) }
        }.andReturn().response.contentAsString
        val notebookId = UUID.fromString(objectMapper.readTree(draftResponse)["data"]["id"].asText())

        val publishResponse = mockMvc.post("/api/v1/wrong-answer-notebooks/$notebookId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(notebookId.toString()) }
            jsonPath("$.data.assignmentId") { exists() }
            jsonPath("$.data.status") { value("PUBLISHED") }
            jsonPath("$.data.problemCount") { value(1) }
        }.andReturn().response.contentAsString
        val assignmentId = UUID.fromString(objectMapper.readTree(publishResponse)["data"]["assignmentId"].asText())

        val assignment = assignmentRepository.findById(assignmentId).orElseThrow()
        assertThat(assignment.assignmentType).isEqualTo(AssignmentType.REVIEW_SET)
        assertThat(assignment.status).isEqualTo(AssignmentStatus.PUBLISHED)
        assertThat(assignment.teacherStudentId).isEqualTo(relationship.id!!)
        assertThat(assignment.subjectId).isEqualTo(fixture.math.id!!)
        assertThat(assignmentTargetRepository.findByAssignmentId(assignmentId)).hasSize(1)
        assertThat(assignmentProblemRepository.findByAssignmentIdOrderBySortOrderAsc(assignmentId).map { it.problemId })
            .containsExactly(sourceAssignmentProblem.problemId)
        assertThat(assignmentSubmissionRepository.findByAssignmentId(assignmentId)).hasSize(1)

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebooks") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("includeExpired", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].id") { value(notebookId.toString()) }
            jsonPath("$.data[0].assignmentId") { value(assignmentId.toString()) }
            jsonPath("$.data[0].problemCount") { value(1) }
            jsonPath("$.data[0].remainingProblemCount") { value(1) }
            jsonPath("$.data[0].submissionStatus") { value("NOT_SUBMITTED") }
            jsonPath("$.data[0].canSolve") { value(true) }
        }

        mockMvc.get("/api/v1/student/wrong-answer-notebooks") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            param("includeExpired", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].id") { value(notebookId.toString()) }
            jsonPath("$.data[0].assignmentId") { value(assignmentId.toString()) }
        }

        mockMvc.get("/api/v1/student/assignments") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            param("includeExpired", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].id") { value(assignmentId.toString()) }
            jsonPath("$.data[0].assignmentType") { value("REVIEW_SET") }
            jsonPath("$.data[0].submissionStatus") { value("NOT_SUBMITTED") }
        }
    }

    @Test
    fun `teacher lists wrong-answer notebook sources from assignments and previous notebooks`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sourceAssignmentProblem = createPublishedSourceAssignment(fixture, relationship, "삼각함수 숙제")
        createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "정답 숙제",
            attemptStatus = ProblemAttemptStatus.CORRECT_FIRST,
            isCorrect = true,
        )
        createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "부분 저장 숙제",
            submissionStatus = SubmissionStatus.PARTIAL,
        )

        val draftResponse = mockMvc.post("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebooks") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = notebookBody(fixture, sourceAssignmentProblem.id!!, sourceAssignmentProblem.problemId.toString())
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val notebookId = UUID.fromString(objectMapper.readTree(draftResponse)["data"]["id"].asText())
        mockMvc.post("/api/v1/wrong-answer-notebooks/$notebookId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
        }

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebook-sources") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", fixture.math.id!!.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.assignments.length()") { value(1) }
            jsonPath("$.data.assignments[0].uniqueProblemId") { value(sourceAssignmentProblem.problemId.toString()) }
            jsonPath("$.data.assignments[0].assignmentProblemId") { value(sourceAssignmentProblem.id!!.toString()) }
            jsonPath("$.data.assignments[0].sourceType") { value("ASSIGNMENT") }
            jsonPath("$.data.assignments[0].attemptStatus") { value("WRONG_FIRST") }
            jsonPath("$.data.assignments[0].retryCount") { value(0) }
            jsonPath("$.data.assignments[0].selected") { value(true) }
            jsonPath("$.data.previousNotebooks.length()") { value(1) }
            jsonPath("$.data.previousNotebooks[0].uniqueProblemId") { value(sourceAssignmentProblem.problemId.toString()) }
            jsonPath("$.data.previousNotebooks[0].assignmentProblemId") { value(sourceAssignmentProblem.id!!.toString()) }
            jsonPath("$.data.previousNotebooks[0].sourceType") { value("PREVIOUS_NOTEBOOK") }
            jsonPath("$.data.previousNotebooks[0].selected") { value(true) }
        }
    }

    @Test
    fun `wrong-answer notebook sources enforce teacher relationship and role`() {
        val fixture = createFixture()
        createRelationship(fixture)
        val otherFixture = createFixture()
        createRelationship(otherFixture)

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebook-sources") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            param("subjectId", fixture.math.id!!.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }

        mockMvc.get("/api/v1/students/${otherFixture.studentProfile.id}/wrong-answer-notebook-sources") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", otherFixture.math.id!!.toString())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun `teacher gets student wrong-answer report filtered by subject curriculum date and unresolved state`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sharedLabels = createLabelFixture(fixture.math.id!!)
        val unresolvedProblem = createProblem(
            teacherId = fixture.teacherProfile.id!!,
            subjectId = fixture.math.id!!,
            labels = sharedLabels,
        )
        val resolvedProblem = createProblem(
            teacherId = fixture.teacherProfile.id!!,
            subjectId = fixture.math.id!!,
            labels = sharedLabels,
        )
        createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "범위 밖 숙제",
            problem = createProblem(fixture.teacherProfile.id!!, fixture.math.id!!, labels = sharedLabels),
            submittedAt = Instant.parse("2026-04-30T03:00:00Z"),
        )
        val unresolvedAssignmentProblem = createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "5월 오답 숙제",
            problem = unresolvedProblem,
            attemptStatus = ProblemAttemptStatus.WRONG_FIRST,
            isCorrect = false,
            submittedAt = Instant.parse("2026-05-10T03:00:00Z"),
        )
        createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "5월 해결 숙제",
            problem = resolvedProblem,
            attemptStatus = ProblemAttemptStatus.CORRECT_RETRY,
            isCorrect = true,
            submittedAt = Instant.parse("2026-05-11T03:00:00Z"),
        )

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answers") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", fixture.math.id!!.toString())
            param("curriculumNodeId", sharedLabels.depth2Id.toString())
            param("unresolvedOnly", "true")
            param("from", "2026-05-01")
            param("to", "2026-05-31")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.studentId") { value(fixture.studentProfile.id!!.toString()) }
            jsonPath("$.data.subjectId") { value(fixture.math.id!!.toString()) }
            jsonPath("$.data.totalCount") { value(1) }
            jsonPath("$.data.unresolvedCount") { value(1) }
            jsonPath("$.data.items.length()") { value(1) }
            jsonPath("$.data.items[0].assignmentProblemId") { value(unresolvedAssignmentProblem.id!!.toString()) }
            jsonPath("$.data.items[0].problemId") { value(unresolvedProblem.id!!.toString()) }
            jsonPath("$.data.items[0].assignmentTitle") { value("5월 오답 숙제") }
            jsonPath("$.data.items[0].attemptStatus") { value("WRONG_FIRST") }
            jsonPath("$.data.items[0].resolved") { value(false) }
            jsonPath("$.data.items[0].submittedAt") { value("2026-05-10T03:00:00Z") }
        }

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answers") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", fixture.math.id!!.toString())
            param("unresolvedOnly", "false")
            param("from", "2026-05-01")
            param("to", "2026-05-31")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalCount") { value(2) }
            jsonPath("$.data.unresolvedCount") { value(1) }
            jsonPath("$.data.items[0].attemptStatus") { value("CORRECT_RETRY") }
            jsonPath("$.data.items[0].resolved") { value(true) }
            jsonPath("$.data.items[1].attemptStatus") { value("WRONG_FIRST") }
            jsonPath("$.data.items[1].resolved") { value(false) }
        }
    }

    @Test
    fun `wrong-answer report does not resurrect older wrong answer when latest submission is correct first outside date range`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sharedProblem = createProblem(fixture.teacherProfile.id!!, fixture.math.id!!)
        createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "5월 오답 숙제",
            problem = sharedProblem,
            attemptStatus = ProblemAttemptStatus.WRONG_FIRST,
            isCorrect = false,
            assignmentType = AssignmentType.HOMEWORK,
            createdAt = Instant.parse("2026-05-10T00:00:00Z"),
            submittedAt = Instant.parse("2026-05-10T03:00:00Z"),
        )
        createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "6월 해결 복습",
            problem = sharedProblem,
            attemptStatus = ProblemAttemptStatus.CORRECT_FIRST,
            isCorrect = true,
            assignmentType = AssignmentType.REVIEW_SET,
            createdAt = Instant.parse("2026-06-02T00:00:00Z"),
            submittedAt = Instant.parse("2026-06-02T03:00:00Z"),
        )

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answers") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", fixture.math.id!!.toString())
            param("unresolvedOnly", "false")
            param("from", "2026-05-01")
            param("to", "2026-05-31")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalCount") { value(0) }
            jsonPath("$.data.unresolvedCount") { value(0) }
            jsonPath("$.data.items.length()") { value(0) }
        }
    }

    @Test
    fun `wrong-answer report uses archived submitted assignments as historical latest answer records`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sharedProblem = createProblem(fixture.teacherProfile.id!!, fixture.math.id!!)
        createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "이전 오답 숙제",
            problem = sharedProblem,
            attemptStatus = ProblemAttemptStatus.WRONG_FIRST,
            isCorrect = false,
            assignmentType = AssignmentType.HOMEWORK,
            createdAt = Instant.parse("2026-05-10T00:00:00Z"),
            submittedAt = Instant.parse("2026-05-10T03:00:00Z"),
        )
        createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "보관된 해결 과제",
            problem = sharedProblem,
            attemptStatus = ProblemAttemptStatus.CORRECT_FIRST,
            isCorrect = true,
            assignmentType = AssignmentType.TEST,
            assignmentStatus = AssignmentStatus.ARCHIVED,
            createdAt = Instant.parse("2026-05-12T00:00:00Z"),
            submittedAt = Instant.parse("2026-05-12T03:00:00Z"),
        )

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answers") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", fixture.math.id!!.toString())
            param("unresolvedOnly", "false")
            param("from", "2026-05-01")
            param("to", "2026-05-31")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalCount") { value(0) }
            jsonPath("$.data.items.length()") { value(0) }
        }
    }

    @Test
    fun `wrong-answer report surfaces latest pending manual-review answer as unresolved`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sharedProblem = createProblem(fixture.teacherProfile.id!!, fixture.math.id!!)
        createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "이전 오답 숙제",
            problem = sharedProblem,
            attemptStatus = ProblemAttemptStatus.WRONG_FIRST,
            isCorrect = false,
            createdAt = Instant.parse("2026-05-10T00:00:00Z"),
            submittedAt = Instant.parse("2026-05-10T03:00:00Z"),
        )
        val pendingAssignmentProblem = createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "최신 수동검토 숙제",
            problem = sharedProblem,
            attemptStatus = ProblemAttemptStatus.PENDING,
            isCorrect = null,
            createdAt = Instant.parse("2026-05-13T00:00:00Z"),
            submittedAt = Instant.parse("2026-05-13T03:00:00Z"),
        )

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answers") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", fixture.math.id!!.toString())
            param("unresolvedOnly", "true")
            param("from", "2026-05-01")
            param("to", "2026-05-31")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalCount") { value(1) }
            jsonPath("$.data.unresolvedCount") { value(1) }
            jsonPath("$.data.items[0].assignmentProblemId") { value(pendingAssignmentProblem.id!!.toString()) }
            jsonPath("$.data.items[0].attemptStatus") { value("PENDING") }
            jsonPath("$.data.items[0].resolved") { value(false) }
        }
    }

    @Test
    fun `wrong-answer report enforces teacher role relationship subject and date range`() {
        val fixture = createFixture()
        createRelationship(fixture)
        val otherFixture = createFixture()
        createRelationship(otherFixture)

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answers") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            param("subjectId", fixture.math.id!!.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }

        mockMvc.get("/api/v1/students/${otherFixture.studentProfile.id}/wrong-answers") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", otherFixture.math.id!!.toString())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answers") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", fixture.math.id!!.toString())
            param("from", "2026-06-01")
            param("to", "2026-05-01")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
        }
    }

    @Test
    fun `wrong-answer notebook sources keep older wrong assignment when newer history is only partial`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sharedProblem = createProblem(fixture.teacherProfile.id!!, fixture.math.id!!)
        val olderWrong = createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "제출된 오답 숙제",
            problem = sharedProblem,
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
            submittedAt = Instant.parse("2026-06-01T01:00:00Z"),
        )
        createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "부분 저장된 최신 숙제",
            problem = sharedProblem,
            submissionStatus = SubmissionStatus.PARTIAL,
            createdAt = Instant.parse("2026-06-02T00:00:00Z"),
            submittedAt = null,
        )

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebook-sources") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", fixture.math.id!!.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.assignments.length()") { value(1) }
            jsonPath("$.data.assignments[0].assignmentProblemId") { value(olderWrong.id!!.toString()) }
            jsonPath("$.data.assignments[0].attemptStatus") { value("WRONG_FIRST") }
        }
    }

    @Test
    fun `wrong-answer notebook sources use latest submitted assignment per problem`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sharedProblem = createProblem(fixture.teacherProfile.id!!, fixture.math.id!!)
        createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "이전 오답 숙제",
            problem = sharedProblem,
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
            submittedAt = Instant.parse("2026-06-01T01:00:00Z"),
        )
        createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "최신 정답 숙제",
            problem = sharedProblem,
            attemptStatus = ProblemAttemptStatus.CORRECT_FIRST,
            isCorrect = true,
            createdAt = Instant.parse("2026-06-02T00:00:00Z"),
            submittedAt = Instant.parse("2026-06-02T01:00:00Z"),
        )

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebook-sources") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", fixture.math.id!!.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.assignments.length()") { value(0) }
        }
    }

    @Test
    fun `wrong-answer notebook sources exclude previous notebook when latest review is correct first`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sharedProblem = createProblem(fixture.teacherProfile.id!!, fixture.math.id!!)
        val sourceAssignmentProblem = createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "원본 오답 숙제",
            problem = sharedProblem,
        )
        createPublishedNotebookWithReview(
            fixture = fixture,
            relationship = relationship,
            sourceAssignmentProblem = sourceAssignmentProblem,
            title = "이전 오답노트",
            notebookCreatedAt = Instant.parse("2026-06-01T00:00:00Z"),
            reviewAttemptStatus = ProblemAttemptStatus.WRONG_FIRST,
            reviewSubmittedAt = Instant.parse("2026-06-01T01:00:00Z"),
        )
        createPublishedNotebookWithReview(
            fixture = fixture,
            relationship = relationship,
            sourceAssignmentProblem = sourceAssignmentProblem,
            title = "최신 오답노트",
            notebookCreatedAt = Instant.parse("2026-06-02T00:00:00Z"),
            reviewAttemptStatus = ProblemAttemptStatus.CORRECT_FIRST,
            reviewIsCorrect = true,
            reviewSubmittedAt = Instant.parse("2026-06-02T01:00:00Z"),
        )

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebook-sources") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", fixture.math.id!!.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.previousNotebooks.length()") { value(0) }
        }
    }

    @Test
    fun `wrong-answer notebook sources order previous notebooks by published time`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sharedProblem = createProblem(fixture.teacherProfile.id!!, fixture.math.id!!)
        val sourceAssignmentProblem = createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "원본 오답 숙제",
            problem = sharedProblem,
        )
        createPublishedNotebookWithReview(
            fixture = fixture,
            relationship = relationship,
            sourceAssignmentProblem = sourceAssignmentProblem,
            title = "나중에 작성된 이전 발행 노트",
            notebookCreatedAt = Instant.parse("2026-06-03T00:00:00Z"),
            notebookPublishedAt = Instant.parse("2026-06-01T00:00:00Z"),
            reviewAttemptStatus = ProblemAttemptStatus.WRONG_FIRST,
            reviewSubmittedAt = Instant.parse("2026-06-01T01:00:00Z"),
        )
        createPublishedNotebookWithReview(
            fixture = fixture,
            relationship = relationship,
            sourceAssignmentProblem = sourceAssignmentProblem,
            title = "먼저 작성된 최신 발행 노트",
            notebookCreatedAt = Instant.parse("2026-06-01T00:00:00Z"),
            notebookPublishedAt = Instant.parse("2026-06-03T00:00:00Z"),
            reviewAttemptStatus = ProblemAttemptStatus.CORRECT_FIRST,
            reviewIsCorrect = true,
            reviewSubmittedAt = Instant.parse("2026-06-03T01:00:00Z"),
        )

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebook-sources") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", fixture.math.id!!.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.previousNotebooks.length()") { value(0) }
        }
    }

    @Test
    fun `wrong-answer notebook draft rejects source problem without wrong-answer history`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sourceAssignmentProblem = createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "정답 숙제",
            attemptStatus = ProblemAttemptStatus.CORRECT_FIRST,
            isCorrect = true,
        )

        mockMvc.post("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebooks") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = notebookBody(fixture, sourceAssignmentProblem.id!!, "MATH-${sourceAssignmentProblem.problemId}")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("오답 제출 기록을 찾을 수 없습니다.") }
        }
    }

    @Test
    fun `student list hides draft notebooks before publish`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sourceAssignmentProblem = createPublishedSourceAssignment(fixture, relationship, "미발행 숙제")

        val draftResponse = mockMvc.post("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebooks") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = notebookBody(fixture, sourceAssignmentProblem.id!!, "MATH-${sourceAssignmentProblem.problemId}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("DRAFT") }
        }.andReturn().response.contentAsString
        val notebookId = UUID.fromString(objectMapper.readTree(draftResponse)["data"]["id"].asText())

        mockMvc.get("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebooks") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            param("includeExpired", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].id") { value(notebookId.toString()) }
        }

        mockMvc.get("/api/v1/student/wrong-answer-notebooks") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            param("includeExpired", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(0) }
        }
    }

    @Test
    fun `wrong-answer notebook draft requires submitted source submission`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sourceAssignmentProblem = createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "부분저장 숙제",
            submissionStatus = SubmissionStatus.PARTIAL,
        )

        mockMvc.post("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebooks") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = notebookBody(fixture, sourceAssignmentProblem.id!!, "MATH-${sourceAssignmentProblem.problemId}")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("오답 제출 기록을 찾을 수 없습니다.") }
        }
    }

    @Test
    fun `wrong-answer notebook publish requires active relationship and assigned subject`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val sourceAssignmentProblem = createPublishedSourceAssignment(fixture, relationship, "관계 종료 숙제")

        val draftResponse = mockMvc.post("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebooks") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = notebookBody(fixture, sourceAssignmentProblem.id!!, "MATH-${sourceAssignmentProblem.problemId}")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val notebookId = UUID.fromString(objectMapper.readTree(draftResponse)["data"]["id"].asText())
        relationship.active = false
        teacherStudentRepository.saveAndFlush(relationship)

        mockMvc.post("/api/v1/wrong-answer-notebooks/$notebookId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("학생 관계를 찾을 수 없습니다.") }
        }
    }

    @Test
    fun `wrong-answer notebook draft enforces teacher ownership and duplicate refs`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val otherFixture = createFixture()
        createRelationship(otherFixture)
        val sourceAssignmentProblem = createPublishedSourceAssignment(fixture, relationship, "확률 숙제")

        mockMvc.post("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebooks") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = notebookBody(fixture, sourceAssignmentProblem.id!!, "MATH-${sourceAssignmentProblem.problemId}")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }

        mockMvc.post("/api/v1/students/${otherFixture.studentProfile.id}/wrong-answer-notebooks") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = notebookBody(otherFixture, sourceAssignmentProblem.id!!, "MATH-${sourceAssignmentProblem.problemId}")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }

        mockMvc.post("/api/v1/students/${fixture.studentProfile.id}/wrong-answer-notebooks") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "title" to "중복 오답노트",
                    "subjectId" to fixture.math.id!!.toString(),
                    "dueAt" to "2030-06-05T19:00:00+09:00",
                    "problemRefs" to listOf(
                        mapOf(
                            "uniqueProblemId" to "DUPLICATE",
                            "sourceAssignmentProblemId" to sourceAssignmentProblem.id!!.toString(),
                        ),
                        mapOf(
                            "uniqueProblemId" to "DUPLICATE",
                            "sourceAssignmentProblemId" to sourceAssignmentProblem.id!!.toString(),
                        ),
                    ),
                ),
            )
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("오답노트 문제는 중복될 수 없습니다.") }
        }
    }

    private fun notebookBody(
        fixture: Fixture,
        sourceAssignmentProblemId: UUID,
        uniqueProblemId: String,
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "title" to "6월 1주차 오답노트",
                "subjectId" to fixture.math.id!!.toString(),
                "dueAt" to "2030-06-05T19:00:00+09:00",
                "problemRefs" to listOf(
                    mapOf(
                        "uniqueProblemId" to uniqueProblemId,
                        "sourceAssignmentProblemId" to sourceAssignmentProblemId.toString(),
                    ),
                ),
            ),
        )

    private fun createPublishedSourceAssignment(
        fixture: Fixture,
        relationship: TeacherStudent,
        title: String,
        problem: Problem = createProblem(fixture.teacherProfile.id!!, fixture.math.id!!),
        attemptStatus: ProblemAttemptStatus = ProblemAttemptStatus.WRONG_FIRST,
        isCorrect: Boolean? = false,
        submissionStatus: SubmissionStatus = SubmissionStatus.SUBMITTED,
        assignmentType: AssignmentType = AssignmentType.HOMEWORK,
        assignmentStatus: AssignmentStatus = AssignmentStatus.PUBLISHED,
        createdAt: Instant = Instant.now(),
        submittedAt: Instant? = Instant.now(),
    ): AssignmentProblem {
        val assignment = assignmentRepository.saveAndFlush(
            Assignment(
                teacherId = fixture.teacherProfile.id!!,
                teacherStudentId = relationship.id!!,
                subjectId = fixture.math.id!!,
                title = title,
                assignmentType = assignmentType,
                status = assignmentStatus,
                resultVisibility = ResultVisibility.HIDDEN_UNTIL_RELEASED,
                dueAt = Instant.parse("2030-06-04T10:00:00Z"),
                publishedAt = createdAt,
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
        )
        assignmentTargetRepository.save(
            AssignmentTarget(
                assignmentId = assignment.id!!,
                teacherStudentId = relationship.id!!,
            ),
        )
        val assignmentProblem = assignmentProblemRepository.saveAndFlush(
            AssignmentProblem(
                assignmentId = assignment.id!!,
                problemId = problem.id!!,
                sortOrder = 1,
                points = BigDecimal.ONE,
            ),
        )
        val submission = assignmentSubmissionRepository.saveAndFlush(
            AssignmentSubmission(
                assignmentId = assignment.id!!,
                teacherStudentId = relationship.id!!,
                status = submissionStatus,
                gradingStatus = GradingStatus.AUTO_GRADED,
                totalPoints = BigDecimal.ONE,
                submittedAt = submittedAt,
            ),
        )
        submissionAnswerRepository.save(
            SubmissionAnswer(
                submissionId = submission.id!!,
                problemId = problem.id!!,
                selectedChoiceNumbers = listOf(2.toShort()),
                autoIsCorrect = isCorrect,
                isCorrect = isCorrect,
                attemptStatus = attemptStatus,
            ),
        )
        return assignmentProblem
    }

    private fun createPublishedNotebookWithReview(
        fixture: Fixture,
        relationship: TeacherStudent,
        sourceAssignmentProblem: AssignmentProblem,
        title: String,
        notebookCreatedAt: Instant,
        notebookPublishedAt: Instant = notebookCreatedAt,
        reviewAttemptStatus: ProblemAttemptStatus,
        reviewIsCorrect: Boolean = false,
        reviewSubmittedAt: Instant,
    ): WrongAnswerNotebook {
        val reviewAssignmentProblem = createPublishedSourceAssignment(
            fixture = fixture,
            relationship = relationship,
            title = "$title 복습 과제",
            problem = problemRepository.findById(sourceAssignmentProblem.problemId).orElseThrow(),
            attemptStatus = reviewAttemptStatus,
            isCorrect = reviewIsCorrect,
            assignmentType = AssignmentType.REVIEW_SET,
            createdAt = notebookCreatedAt,
            submittedAt = reviewSubmittedAt,
        )
        val notebook = wrongAnswerNotebookRepository.saveAndFlush(
            WrongAnswerNotebook(
                teacherId = fixture.teacherProfile.id!!,
                teacherStudentId = relationship.id!!,
                subjectId = fixture.math.id!!,
                assignmentId = reviewAssignmentProblem.assignmentId,
                title = title,
                sourceSummary = "1문제",
                status = WrongAnswerNotebookStatus.PUBLISHED,
                dueAt = Instant.parse("2030-06-05T10:00:00Z"),
                publishedAt = notebookPublishedAt,
                createdAt = notebookCreatedAt,
                updatedAt = notebookCreatedAt,
            ),
        )
        wrongAnswerNotebookProblemRepository.saveAndFlush(
            WrongAnswerNotebookProblem(
                notebookId = notebook.id!!,
                problemId = sourceAssignmentProblem.problemId,
                sourceAssignmentProblemId = sourceAssignmentProblem.id!!,
                uniqueProblemId = sourceAssignmentProblem.problemId.toString(),
                sortOrder = 1,
                createdAt = notebookCreatedAt,
            ),
        )
        return notebook
    }

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

    private fun createProblem(
        teacherId: UUID,
        subjectId: UUID,
        labels: LabelFixture = createLabelFixture(subjectId),
    ): Problem {
        val problem = problemRepository.saveAndFlush(
            Problem(
                ownerTeacherId = teacherId,
                subjectId = subjectId,
                answerType = ProblemAnswerType.SINGLE_CHOICE,
                correctChoiceNumbers = listOf(1),
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
                textContent = "오답 문제",
            ),
        )
        return problem
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
            User(name = "오답선생", role = UserRole.TEACHER, email = "wrong-teacher-$suffix@example.com"),
        )
        val studentUser = userRepository.save(
            User(name = "오답학생", role = UserRole.STUDENT, email = "wrong-student-$suffix@example.com"),
        )
        val teacherProfile = teacherProfileRepository.save(
            TeacherProfile(user = teacherUser, displayName = "오답선생"),
        )
        val studentProfile = studentProfileRepository.save(
            StudentProfile(
                user = studentUser,
                name = "오답학생",
                school = "OO고",
                grade = "고2",
            ),
        )
        val math = subjectRepository.save(Subject(code = "WRONG-MATH-$suffix", name = "수학"))

        return Fixture(
            teacherUser = teacherUser,
            studentUser = studentUser,
            teacherProfile = teacherProfile,
            studentProfile = studentProfile,
            math = math,
        )
    }

    private data class Fixture(
        val teacherUser: User,
        val studentUser: User,
        val teacherProfile: TeacherProfile,
        val studentProfile: StudentProfile,
        val math: Subject,
    )

    private data class LabelFixture(
        val depth1Id: UUID,
        val depth2Id: UUID,
        val depth3Id: UUID,
    )
}

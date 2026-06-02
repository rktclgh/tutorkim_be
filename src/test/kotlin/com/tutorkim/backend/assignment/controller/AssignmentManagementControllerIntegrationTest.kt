package com.tutorkim.backend.assignment.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tutorkim.backend.assignment.entity.ProblemAttemptStatus
import com.tutorkim.backend.assignment.entity.ResultVisibility
import com.tutorkim.backend.assignment.entity.SubmissionAnswer
import com.tutorkim.backend.assignment.entity.SubmissionSolutionFile
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.repository.AssignmentProblemRepository
import com.tutorkim.backend.assignment.repository.AssignmentRepository
import com.tutorkim.backend.assignment.repository.SubmissionAnswerRepository
import com.tutorkim.backend.assignment.repository.AssignmentSubmissionRepository
import com.tutorkim.backend.assignment.repository.AssignmentTargetRepository
import com.tutorkim.backend.assignment.repository.SubmissionSolutionFileRepository
import com.tutorkim.backend.content.entity.CurriculumNode
import com.tutorkim.backend.content.repository.CurriculumNodeRepository
import com.tutorkim.backend.file.entity.FileAsset
import com.tutorkim.backend.file.repository.FileAssetRepository
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
import com.tutorkim.backend.problem.entity.ProblemExplanation
import com.tutorkim.backend.problem.entity.ProblemExplanationSourceType
import com.tutorkim.backend.problem.repository.ProblemBlockRepository
import com.tutorkim.backend.problem.repository.ProblemExplanationRepository
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
    private val problemExplanationRepository: ProblemExplanationRepository,
    private val fileAssetRepository: FileAssetRepository,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentProblemRepository: AssignmentProblemRepository,
    private val assignmentTargetRepository: AssignmentTargetRepository,
    private val assignmentSubmissionRepository: AssignmentSubmissionRepository,
    private val submissionAnswerRepository: SubmissionAnswerRepository,
    private val submissionSolutionFileRepository: SubmissionSolutionFileRepository,
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
    fun `teacher releases published hidden assignment results`() {
        val fixture = createFixture()
        createRelationship(fixture)
        val problems = createProblems(fixture.teacherProfile.id!!, fixture.math.id!!, 1)
        val assignmentId = createDraftThroughApi(fixture, problems.map { it.id!! })

        mockMvc.post("/api/v1/assignments/$assignmentId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
        }
        val publishedUpdatedAt = assignmentRepository.findById(assignmentId).orElseThrow().updatedAt

        mockMvc.post("/api/v1/assignments/$assignmentId/release-results") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(assignmentId.toString()) }
            jsonPath("$.data.status") { value("PUBLISHED") }
            jsonPath("$.data.resultVisibility") { value("RELEASED") }
            jsonPath("$.data.submissionStatus") { value("NOT_SUBMITTED") }
            jsonPath("$.data.problems.length()") { value(1) }
        }

        val releasedAssignment = assignmentRepository.findById(assignmentId).orElseThrow()
        assertThat(releasedAssignment.resultVisibility).isEqualTo(ResultVisibility.RELEASED)
        assertThat(releasedAssignment.updatedAt).isAfter(publishedUpdatedAt)

        mockMvc.get("/api/v1/assignments/$assignmentId") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.resultVisibility") { value("RELEASED") }
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
    fun `teacher gets assignment detail with ordered problems answers and solution files`() {
        val fixture = createFixture()
        createRelationship(fixture)
        val problems = createProblems(fixture.teacherProfile.id!!, fixture.math.id!!, 2)
        val assignmentId = createDraftThroughApi(fixture, problems.map { it.id!! })

        mockMvc.post("/api/v1/assignments/$assignmentId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
        }

        val submission = assignmentSubmissionRepository.findAll().single { it.assignmentId == assignmentId }
        val answer = submissionAnswerRepository.save(
            SubmissionAnswer(
                submissionId = submission.id!!,
                problemId = problems[0].id!!,
                selectedChoiceNumbers = listOf(1.toShort()),
                attemptStatus = ProblemAttemptStatus.CORRECT_FIRST,
                retryCount = 0,
                autoIsCorrect = true,
                isCorrect = true,
                autoGradedAt = Instant.now(),
            ),
        )
        val studentSolutionFile = createFileAsset(fixture.studentUser.id!!, "student-solution")
        submissionSolutionFileRepository.save(
            SubmissionSolutionFile(
                submissionAnswerId = answer.id!!,
                fileAssetId = studentSolutionFile.id!!,
            ),
        )
        val teacherSolutionFile = createFileAsset(fixture.teacherUser.id!!, "teacher-solution")
        val explanation = problemExplanationRepository.save(
            ProblemExplanation(
                problemId = problems[0].id!!,
                sortOrder = 1,
                sourceType = ProblemExplanationSourceType.TEACHER_SOLUTION_IMAGE,
                fileAssetId = teacherSolutionFile.id!!,
                visibleToStudent = true,
                createdBy = fixture.teacherUser.id!!,
            ),
        )

        mockMvc.get("/api/v1/assignments/$assignmentId") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(assignmentId.toString()) }
            jsonPath("$.data.title") { value(fixture.assignmentTitle) }
            jsonPath("$.data.assignmentType") { value("HOMEWORK") }
            jsonPath("$.data.student.id") { value(fixture.studentProfile.id!!.toString()) }
            jsonPath("$.data.subjectId") { value(fixture.math.id!!.toString()) }
            jsonPath("$.data.expired") { value(false) }
            jsonPath("$.data.canSolve") { value(true) }
            jsonPath("$.data.questionCount") { value(0) }
            jsonPath("$.data.status") { value("PUBLISHED") }
            jsonPath("$.data.submissionStatus") { value("NOT_SUBMITTED") }
            jsonPath("$.data.resultVisibility") { value("HIDDEN_UNTIL_RELEASED") }
            jsonPath("$.data.problems.length()") { value(2) }
            jsonPath("$.data.problems[0].number") { value(1) }
            jsonPath("$.data.problems[0].assignmentProblemId") { exists() }
            jsonPath("$.data.problems[0].problemId") { value(problems[0].id!!.toString()) }
            jsonPath("$.data.problems[0].points") { value(1) }
            jsonPath("$.data.problems[0].blocks.length()") { value(1) }
            jsonPath("$.data.problems[0].blocks[0].text") { value("문제 1") }
            jsonPath("$.data.problems[0].answerType") { value("SINGLE_CHOICE") }
            jsonPath("$.data.problems[0].attemptStatus") { value("CORRECT_FIRST") }
            jsonPath("$.data.problems[0].retryCount") { value(0) }
            jsonPath("$.data.problems[0].studentAnswer.selectedChoiceNumbers[0]") { value(1) }
            jsonPath("$.data.problems[0].studentAnswer.unknown") { value(false) }
            jsonPath("$.data.problems[0].studentSolutionFiles[0].fileAssetId") { value(studentSolutionFile.id!!.toString()) }
            jsonPath("$.data.problems[0].studentSolutionFiles[0].uploadedAt") { exists() }
            jsonPath("$.data.problems[0].teacherSolutionFiles[0].explanationId") { value(explanation.id!!.toString()) }
            jsonPath("$.data.problems[0].teacherSolutionFiles[0].fileAssetId") { value(teacherSolutionFile.id!!.toString()) }
            jsonPath("$.data.problems[0].teacherSolutionFiles[0].visibleToStudent") { value(true) }
            jsonPath("$.data.problems[1].number") { value(2) }
            jsonPath("$.data.problems[1].problemId") { value(problems[1].id!!.toString()) }
            jsonPath("$.data.problems[1].attemptStatus") { value("PENDING") }
            jsonPath("$.data.problems[1].retryCount") { value(0) }
            jsonPath("$.data.problems[1].studentAnswer") { doesNotExist() }
            jsonPath("$.data.problems[1].studentSolutionFiles.length()") { value(0) }
            jsonPath("$.data.problems[1].teacherSolutionFiles.length()") { value(0) }
        }
    }

    @Test
    fun `teacher gets historical assignment detail after relationship deactivation and problem archive`() {
        val fixture = createFixture()
        val relationship = createRelationship(fixture)
        val problem = createProblems(fixture.teacherProfile.id!!, fixture.math.id!!, 1).single()
        val assignmentId = createDraftThroughApi(fixture, listOf(problem.id!!))

        mockMvc.post("/api/v1/assignments/$assignmentId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
        }

        relationship.active = false
        teacherStudentRepository.saveAndFlush(relationship)
        problem.archivedAt = Instant.now()
        problem.archivedBy = fixture.teacherUser.id!!
        problemRepository.saveAndFlush(problem)

        mockMvc.get("/api/v1/assignments/$assignmentId") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(assignmentId.toString()) }
            jsonPath("$.data.canSolve") { value(false) }
            jsonPath("$.data.problems.length()") { value(1) }
            jsonPath("$.data.problems[0].problemId") { value(problem.id!!.toString()) }
            jsonPath("$.data.problems[0].blocks[0].text") { value("문제 1") }
        }
    }

    @Test
    fun `student lists own published assignments with answered count and expiry filter`() {
        val fixture = createFixture()
        createRelationship(fixture)
        val expiredProblem = createProblems(fixture.teacherProfile.id!!, fixture.math.id!!, 1).single()
        val currentProblems = createProblems(fixture.teacherProfile.id!!, fixture.math.id!!, 2)
        val expiredAssignmentId = createDraftThroughApi(
            fixture = fixture,
            problemIds = listOf(expiredProblem.id!!),
            title = "${fixture.assignmentTitle}-expired",
            dueAt = "2020-05-20T23:59:00+09:00",
        )

        mockMvc.post("/api/v1/assignments/$expiredAssignmentId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
        }

        val currentAssignmentId = createDraftThroughApi(fixture, currentProblems.map { it.id!! })

        mockMvc.post("/api/v1/assignments/$currentAssignmentId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
        }

        val submission = assignmentSubmissionRepository.findAll().single { it.assignmentId == currentAssignmentId }
        submissionAnswerRepository.save(
            SubmissionAnswer(
                submissionId = submission.id!!,
                problemId = currentProblems[0].id!!,
                selectedChoiceNumbers = listOf(1.toShort()),
                attemptStatus = ProblemAttemptStatus.CORRECT_FIRST,
                autoIsCorrect = true,
                isCorrect = true,
                autoGradedAt = Instant.now(),
            ),
        )

        mockMvc.get("/api/v1/student/assignments") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].id") { value(currentAssignmentId.toString()) }
            jsonPath("$.data[0].title") { value(fixture.assignmentTitle) }
            jsonPath("$.data[0].teacher.id") { value(fixture.teacherProfile.id!!.toString()) }
            jsonPath("$.data[0].teacher.name") { value(fixture.teacherProfile.displayName) }
            jsonPath("$.data[0].subject.id") { value(fixture.math.id!!.toString()) }
            jsonPath("$.data[0].subject.name") { value(fixture.math.name) }
            jsonPath("$.data[0].assignmentType") { value("HOMEWORK") }
            jsonPath("$.data[0].expired") { value(false) }
            jsonPath("$.data[0].canSolve") { value(true) }
            jsonPath("$.data[0].problemCount") { value(2) }
            jsonPath("$.data[0].answeredCount") { value(1) }
            jsonPath("$.data[0].questionCount") { value(0) }
            jsonPath("$.data[0].submissionStatus") { value("NOT_SUBMITTED") }
        }

        mockMvc.get("/api/v1/student/assignments") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            param("includeExpired", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(2) }
            jsonPath("$.data[0].id") { value(currentAssignmentId.toString()) }
            jsonPath("$.data[1].id") { value(expiredAssignmentId.toString()) }
            jsonPath("$.data[1].expired") { value(true) }
            jsonPath("$.data[1].canSolve") { value(false) }
        }
    }

    @Test
    fun `student gets own assignment detail with visible teacher solution files only`() {
        val fixture = createFixture()
        createRelationship(fixture)
        val otherFixture = createFixture()
        createRelationship(otherFixture)
        val problems = createProblems(fixture.teacherProfile.id!!, fixture.math.id!!, 2)
        val assignmentId = createDraftThroughApi(fixture, problems.map { it.id!! })

        mockMvc.post("/api/v1/assignments/$assignmentId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
        }

        val submission = assignmentSubmissionRepository.findAll().single { it.assignmentId == assignmentId }
        val answer = submissionAnswerRepository.save(
            SubmissionAnswer(
                submissionId = submission.id!!,
                problemId = problems[0].id!!,
                selectedChoiceNumbers = listOf(1.toShort()),
                attemptStatus = ProblemAttemptStatus.CORRECT_FIRST,
                autoIsCorrect = true,
                isCorrect = true,
                autoGradedAt = Instant.now(),
            ),
        )
        val studentSolutionFile = createFileAsset(fixture.studentUser.id!!, "student-detail-solution")
        submissionSolutionFileRepository.save(
            SubmissionSolutionFile(
                submissionAnswerId = answer.id!!,
                fileAssetId = studentSolutionFile.id!!,
            ),
        )
        val visibleTeacherSolutionFile = createFileAsset(fixture.teacherUser.id!!, "visible-teacher-solution")
        val hiddenTeacherSolutionFile = createFileAsset(fixture.teacherUser.id!!, "hidden-teacher-solution")
        val visibleExplanation = problemExplanationRepository.save(
            ProblemExplanation(
                problemId = problems[0].id!!,
                sortOrder = 1,
                sourceType = ProblemExplanationSourceType.TEACHER_SOLUTION_IMAGE,
                fileAssetId = visibleTeacherSolutionFile.id!!,
                visibleToStudent = true,
                createdBy = fixture.teacherUser.id!!,
            ),
        )
        problemExplanationRepository.save(
            ProblemExplanation(
                problemId = problems[0].id!!,
                sortOrder = 2,
                sourceType = ProblemExplanationSourceType.TEACHER_SOLUTION_IMAGE,
                fileAssetId = hiddenTeacherSolutionFile.id!!,
                visibleToStudent = false,
                createdBy = fixture.teacherUser.id!!,
            ),
        )

        mockMvc.get("/api/v1/student/assignments/$assignmentId") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(assignmentId.toString()) }
            jsonPath("$.data.title") { value(fixture.assignmentTitle) }
            jsonPath("$.data.teacher.id") { value(fixture.teacherProfile.id!!.toString()) }
            jsonPath("$.data.teacher.name") { value(fixture.teacherProfile.displayName) }
            jsonPath("$.data.subject.id") { value(fixture.math.id!!.toString()) }
            jsonPath("$.data.subject.name") { value(fixture.math.name) }
            jsonPath("$.data.expired") { value(false) }
            jsonPath("$.data.canSolve") { value(true) }
            jsonPath("$.data.status") { value("PUBLISHED") }
            jsonPath("$.data.submissionStatus") { value("NOT_SUBMITTED") }
            jsonPath("$.data.resultVisibility") { value("HIDDEN_UNTIL_RELEASED") }
            jsonPath("$.data.problems.length()") { value(2) }
            jsonPath("$.data.problems[0].problemId") { value(problems[0].id!!.toString()) }
            jsonPath("$.data.problems[0].blocks[0].text") { value("문제 1") }
            jsonPath("$.data.problems[0].attemptStatus") { value("CORRECT_FIRST") }
            jsonPath("$.data.problems[0].studentAnswer.selectedChoiceNumbers[0]") { value(1) }
            jsonPath("$.data.problems[0].studentSolutionFiles[0].fileAssetId") { value(studentSolutionFile.id!!.toString()) }
            jsonPath("$.data.problems[0].teacherSolutionFiles.length()") { value(1) }
            jsonPath("$.data.problems[0].teacherSolutionFiles[0].explanationId") { value(visibleExplanation.id!!.toString()) }
            jsonPath("$.data.problems[0].teacherSolutionFiles[0].fileAssetId") { value(visibleTeacherSolutionFile.id!!.toString()) }
            jsonPath("$.data.problems[0].teacherSolutionFiles[0].visibleToStudent") { value(true) }
            jsonPath("$.data.problems[1].problemId") { value(problems[1].id!!.toString()) }
            jsonPath("$.data.problems[1].attemptStatus") { value("PENDING") }
        }

        mockMvc.get("/api/v1/student/assignments/$assignmentId") {
            with(user(otherFixture.studentUser.id!!.toString()).roles("STUDENT"))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }

        mockMvc.get("/api/v1/student/assignments/$assignmentId") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `release results rejects draft and already released assignment`() {
        val fixture = createFixture()
        createRelationship(fixture)
        val problems = createProblems(fixture.teacherProfile.id!!, fixture.math.id!!, 1)
        val assignmentId = createDraftThroughApi(fixture, problems.map { it.id!! })

        mockMvc.post("/api/v1/assignments/$assignmentId/release-results") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("CONFLICT") }
            jsonPath("$.error.message") { value("발행된 과제만 결과를 공개할 수 있습니다.") }
        }

        mockMvc.post("/api/v1/assignments/$assignmentId/publish") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
        }

        mockMvc.post("/api/v1/assignments/$assignmentId/release-results") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
        }

        mockMvc.post("/api/v1/assignments/$assignmentId/release-results") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("CONFLICT") }
            jsonPath("$.error.message") { value("이미 공개된 과제 결과입니다.") }
        }

        val archivedFixture = createFixture()
        createRelationship(archivedFixture)
        val archivedProblem = createProblems(archivedFixture.teacherProfile.id!!, archivedFixture.math.id!!, 1).single()
        val archivedAssignmentId = createDraftThroughApi(archivedFixture, listOf(archivedProblem.id!!))
        val archivedAssignment = assignmentRepository.findById(archivedAssignmentId).orElseThrow()
        archivedAssignment.status = AssignmentStatus.ARCHIVED
        assignmentRepository.saveAndFlush(archivedAssignment)

        mockMvc.post("/api/v1/assignments/$archivedAssignmentId/release-results") {
            with(user(archivedFixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("CONFLICT") }
            jsonPath("$.error.message") { value("발행된 과제만 결과를 공개할 수 있습니다.") }
        }

        val visibleFixture = createFixture()
        createRelationship(visibleFixture)
        val visibleProblem = createProblems(visibleFixture.teacherProfile.id!!, visibleFixture.math.id!!, 1).single()
        val visibleAssignmentId = createDraftThroughApi(visibleFixture, listOf(visibleProblem.id!!))

        mockMvc.post("/api/v1/assignments/$visibleAssignmentId/publish") {
            with(user(visibleFixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
        }
        val visibleAssignment = assignmentRepository.findById(visibleAssignmentId).orElseThrow()
        visibleAssignment.resultVisibility = ResultVisibility.IMMEDIATE
        assignmentRepository.saveAndFlush(visibleAssignment)

        mockMvc.post("/api/v1/assignments/$visibleAssignmentId/release-results") {
            with(user(visibleFixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("CONFLICT") }
            jsonPath("$.error.message") { value("이미 공개된 과제 결과입니다.") }
        }

        visibleAssignment.status = AssignmentStatus.CLOSED
        visibleAssignment.resultVisibility = ResultVisibility.HIDDEN_UNTIL_RELEASED
        assignmentRepository.saveAndFlush(visibleAssignment)

        mockMvc.post("/api/v1/assignments/$visibleAssignmentId/release-results") {
            with(user(visibleFixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("CLOSED") }
            jsonPath("$.data.resultVisibility") { value("RELEASED") }
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

        mockMvc.get("/api/v1/assignments/$assignmentId") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }

        mockMvc.post("/api/v1/assignments/$assignmentId/release-results") {
            with(user(fixture.studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }

        mockMvc.get("/api/v1/assignments/$assignmentId") {
            with(user(otherFixture.teacherUser.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }

        mockMvc.post("/api/v1/assignments/$assignmentId/release-results") {
            with(user(otherFixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
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
        title: String = fixture.assignmentTitle,
        dueAt: String = "2030-05-20T23:59:00+09:00",
    ): UUID {
        mockMvc.post("/api/v1/assignments") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = createAssignmentBody(fixture, problemIds, title = title, dueAt = dueAt)
        }.andExpect {
            status { isOk() }
        }
        return assignmentRepository.findAll()
            .single { it.title == title && it.teacherId == fixture.teacherProfile.id!! }
            .id!!
    }

    private fun createAssignmentBody(
        fixture: Fixture,
        problemIds: List<UUID>,
        lessonSessionId: UUID? = null,
        subjectId: UUID = fixture.math.id!!,
        title: String = fixture.assignmentTitle,
        dueAt: String = "2030-05-20T23:59:00+09:00",
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "studentId" to fixture.studentProfile.id!!.toString(),
                "lessonSessionId" to lessonSessionId?.toString(),
                "subjectId" to subjectId.toString(),
                "title" to title,
                "assignmentType" to "HOMEWORK",
                "resultVisibility" to "HIDDEN_UNTIL_RELEASED",
                "dueAt" to dueAt,
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

    private fun createFileAsset(
        ownerUserId: UUID,
        label: String,
    ): FileAsset =
        fileAssetRepository.save(
            FileAsset(
                ownerUserId = ownerUserId,
                storageKey = "assignment-detail/$label-${UUID.randomUUID()}.png",
                originalFilename = "$label.png",
                contentType = "image/png",
                sizeBytes = 128,
            ),
        )

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

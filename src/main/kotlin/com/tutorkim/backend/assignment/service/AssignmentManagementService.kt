package com.tutorkim.backend.assignment.service

import com.tutorkim.backend.assignment.dto.AssignmentDetailResponse
import com.tutorkim.backend.assignment.dto.AssignmentProblemDetailResponse
import com.tutorkim.backend.assignment.dto.AssignmentSummaryResponse
import com.tutorkim.backend.assignment.dto.CreateAssignmentRequest
import com.tutorkim.backend.assignment.dto.ManualGradeSubmissionAnswerRequest
import com.tutorkim.backend.assignment.entity.Assignment
import com.tutorkim.backend.assignment.entity.AssignmentProblem
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.entity.AssignmentSubmission
import com.tutorkim.backend.assignment.entity.AssignmentTarget
import com.tutorkim.backend.assignment.entity.AssignmentType
import com.tutorkim.backend.assignment.entity.GradingStatus
import com.tutorkim.backend.assignment.entity.ProblemAttemptStatus
import com.tutorkim.backend.assignment.entity.ResultVisibility
import com.tutorkim.backend.assignment.entity.SubmissionStatus
import com.tutorkim.backend.assignment.repository.AssignmentProblemRepository
import com.tutorkim.backend.assignment.repository.AssignmentProblemQuestionRepository
import com.tutorkim.backend.assignment.repository.AssignmentRepository
import com.tutorkim.backend.assignment.repository.AssignmentSubmissionRepository
import com.tutorkim.backend.assignment.repository.AssignmentTargetRepository
import com.tutorkim.backend.assignment.repository.SubmissionAnswerRepository
import com.tutorkim.backend.assignment.repository.SubmissionSolutionFileRepository
import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.lesson.repository.LessonSessionRepository
import com.tutorkim.backend.problem.dto.ProblemBlockResponse
import com.tutorkim.backend.problem.entity.Problem
import com.tutorkim.backend.problem.entity.ProblemExplanationSourceType
import com.tutorkim.backend.problem.repository.ProblemBlockRepository
import com.tutorkim.backend.problem.repository.ProblemExplanationRepository
import com.tutorkim.backend.problem.repository.ProblemRepository
import com.tutorkim.backend.student.entity.TeacherStudent
import com.tutorkim.backend.student.repository.TeacherProfileRepository
import com.tutorkim.backend.student.repository.TeacherStudentRepository
import com.tutorkim.backend.student.repository.TeacherStudentSubjectRepository
import org.hibernate.Hibernate
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private val MANUAL_GRADING_ALLOWED_SUBMISSION_STATUSES = setOf(
    SubmissionStatus.SUBMITTED,
    SubmissionStatus.LATE_SUBMITTED,
)

@Service
class AssignmentManagementService(
    private val teacherProfileRepository: TeacherProfileRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val teacherStudentSubjectRepository: TeacherStudentSubjectRepository,
    private val lessonSessionRepository: LessonSessionRepository,
    private val problemRepository: ProblemRepository,
    private val problemBlockRepository: ProblemBlockRepository,
    private val problemExplanationRepository: ProblemExplanationRepository,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentProblemRepository: AssignmentProblemRepository,
    private val assignmentProblemQuestionRepository: AssignmentProblemQuestionRepository,
    private val assignmentTargetRepository: AssignmentTargetRepository,
    private val assignmentSubmissionRepository: AssignmentSubmissionRepository,
    private val submissionAnswerRepository: SubmissionAnswerRepository,
    private val submissionSolutionFileRepository: SubmissionSolutionFileRepository,
    private val transactionTemplate: TransactionTemplate,
    private val assignmentAvailabilityPolicy: AssignmentAvailabilityPolicy = AssignmentAvailabilityPolicy(),
) {
    @Transactional
    fun createDraft(
        teacherUserId: UUID,
        request: CreateAssignmentRequest,
    ): AssignmentSummaryResponse {
        if (request.problemIds.size != request.problemIds.toSet().size) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "과제 문제는 중복될 수 없습니다.")
        }

        val relationship = findActiveRelationshipForUpdate(teacherUserId, request.studentId)
        if (!teacherStudentSubjectRepository.existsByTeacherStudent_IdAndSubject_Id(relationship.id!!, request.subjectId)) {
            throw ApiException(ErrorCode.NOT_FOUND, "학생 과목을 찾을 수 없습니다.")
        }
        validateLessonSession(teacherUserId, relationship, request)
        val problems = findOwnedProblems(relationship.teacher.id!!, request)

        val assignment = assignmentRepository.saveAndFlush(
            Assignment(
                teacherId = relationship.teacher.id!!,
                teacherStudentId = relationship.id!!,
                lessonSessionId = request.lessonSessionId,
                subjectId = request.subjectId,
                title = request.title.trim(),
                assignmentType = request.assignmentType,
                resultVisibility = request.resultVisibility,
                dueAt = request.dueAt,
            ),
        )
        assignmentTargetRepository.save(
            AssignmentTarget(
                assignmentId = assignment.id!!,
                teacherStudentId = relationship.id!!,
            ),
        )
        assignmentProblemRepository.saveAll(
            request.problemIds.mapIndexed { index, problemId ->
                AssignmentProblem(
                    assignmentId = assignment.id!!,
                    problemId = problemId,
                    sortOrder = index + 1,
                    points = BigDecimal.ONE,
                )
            },
        )

        return summary(
            assignment = assignment,
            relationship = relationship,
            problemCount = problems.size,
            submissionStatus = SubmissionStatus.NOT_SUBMITTED,
        )
    }

    @Transactional
    fun publish(
        teacherUserId: UUID,
        assignmentId: UUID,
    ): AssignmentSummaryResponse {
        val teacherId = findTeacherId(teacherUserId)
        val assignment = assignmentRepository.findOwnedByTeacherIdForUpdate(assignmentId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "과제를 찾을 수 없습니다.")
        if (assignment.status != AssignmentStatus.DRAFT) {
            throw ApiException(ErrorCode.CONFLICT, "초안 상태의 과제만 발행할 수 있습니다.")
        }
        val problemCount = assignmentProblemRepository.countByAssignmentId(assignment.id!!)
        if (problemCount == 0) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "과제에는 최소 1개의 문제가 필요합니다.")
        }
        val target = assignmentTargetRepository.findByAssignmentId(assignment.id!!).singleOrNull()
            ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "과제 대상 학생을 찾을 수 없습니다.")

        val now = Instant.now()
        assignment.status = AssignmentStatus.PUBLISHED
        assignment.publishedAt = now
        assignment.updatedAt = now
        val savedAssignment = assignmentRepository.saveAndFlush(assignment)
        if (!assignmentSubmissionRepository.existsByAssignmentIdAndTeacherStudentId(savedAssignment.id!!, target.teacherStudentId)) {
            assignmentSubmissionRepository.save(
                AssignmentSubmission(
                    assignmentId = savedAssignment.id!!,
                    teacherStudentId = target.teacherStudentId,
                    status = SubmissionStatus.NOT_SUBMITTED,
                    totalPoints = BigDecimal.valueOf(problemCount.toLong()),
                ),
            )
        }

        return summary(
            assignment = savedAssignment,
            relationship = findRelationship(target.teacherStudentId, teacherId),
            problemCount = problemCount,
            submissionStatus = SubmissionStatus.NOT_SUBMITTED,
        )
    }

    fun releaseResults(
        teacherUserId: UUID,
        assignmentId: UUID,
    ): AssignmentDetailResponse {
        val teacherId = findTeacherId(teacherUserId)
        transactionTemplate.executeWithoutResult {
            val assignment = assignmentRepository.findOwnedByTeacherIdForUpdate(assignmentId, teacherId)
                ?: throw ApiException(ErrorCode.NOT_FOUND, "과제를 찾을 수 없습니다.")
            if (assignment.status !in setOf(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED)) {
                throw ApiException(ErrorCode.CONFLICT, "발행된 과제만 결과를 공개할 수 있습니다.")
            }
            if (assignment.resultVisibility == ResultVisibility.RELEASED ||
                assignment.resultVisibility == ResultVisibility.IMMEDIATE
            ) {
                throw ApiException(ErrorCode.CONFLICT, "이미 공개된 과제 결과입니다.")
            }

            assignment.resultVisibility = ResultVisibility.RELEASED
            assignment.updatedAt = Instant.now()
            assignmentRepository.saveAndFlush(assignment)
        }

        return transactionTemplate.execute {
            getDetail(
                teacherUserId = teacherUserId,
                assignmentId = assignmentId,
            )
        } ?: throw ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "과제 상세 응답을 생성할 수 없습니다.")
    }

    @Transactional
    fun manuallyGradeSubmissionAnswer(
        teacherUserId: UUID,
        submissionId: UUID,
        answerId: UUID,
        request: ManualGradeSubmissionAnswerRequest,
    ): AssignmentDetailResponse {
        val teacherId = findTeacherId(teacherUserId)
        val submission = assignmentSubmissionRepository.findByIdForUpdate(submissionId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "제출을 찾을 수 없습니다.")
        val assignment = assignmentRepository.findOwnedByTeacherIdForUpdate(submission.assignmentId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "과제를 찾을 수 없습니다.")
        if (assignment.teacherStudentId != submission.teacherStudentId) {
            throw ApiException(ErrorCode.NOT_FOUND, "제출을 찾을 수 없습니다.")
        }
        if (submission.status !in MANUAL_GRADING_ALLOWED_SUBMISSION_STATUSES) {
            throw ApiException(ErrorCode.CONFLICT, "제출 완료된 답안만 수동 채점할 수 있습니다.")
        }
        val answer = submissionAnswerRepository.findByIdAndSubmissionIdForUpdate(
            answerId = answerId,
            submissionId = submission.id!!,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "제출 답안을 찾을 수 없습니다.")
        val now = Instant.now()
        val isCorrect = request.isCorrect!!

        answer.manualIsCorrect = isCorrect
        answer.manualGradingReason = request.reason?.trim()?.ifBlank { null }
        answer.manuallyGradedBy = teacherUserId
        answer.manuallyGradedAt = now
        answer.isCorrect = isCorrect
        answer.attemptStatus = when {
            isCorrect && answer.retryCount > 0 -> ProblemAttemptStatus.CORRECT_RETRY
            isCorrect -> ProblemAttemptStatus.CORRECT_FIRST
            else -> ProblemAttemptStatus.WRONG_FIRST
        }
        answer.updatedAt = now

        val assignmentProblems = assignmentProblemRepository.findByAssignmentIdOrderBySortOrderAsc(assignment.id!!)
        val answersByProblemId = submissionAnswerRepository.findBySubmissionIdAndProblemIdIn(
            submissionId = submission.id!!,
            problemIds = assignmentProblems.map { it.problemId },
        ).associateBy { it.problemId }.toMutableMap()
        answersByProblemId[answer.problemId] = answer
        val totalPoints = assignmentProblems.fold(BigDecimal.ZERO) { total, problem -> total + problem.points }
        val score = assignmentProblems.fold(BigDecimal.ZERO) { total, assignmentProblem ->
            val gradedAnswer = answersByProblemId[assignmentProblem.problemId]
            if (gradedAnswer?.isCorrect == true) total + assignmentProblem.points else total
        }
        submission.gradingStatus = GradingStatus.MANUALLY_ADJUSTED
        submission.score = score
        submission.totalPoints = totalPoints
        submission.gradedAt = now
        submission.updatedAt = now

        return getDetail(
            teacherUserId = teacherUserId,
            assignmentId = assignment.id!!,
        )
    }

    @Transactional(readOnly = true)
    fun listAssignments(
        teacherUserId: UUID,
        studentId: UUID?,
        type: AssignmentType?,
        status: AssignmentStatus?,
        limit: Int,
    ): List<AssignmentSummaryResponse> {
        if (limit !in 1..100) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "조회 개수는 1개 이상 100개 이하로 지정해야 합니다.")
        }
        val teacherId = findTeacherId(teacherUserId)
        val assignments = assignmentRepository.searchForTeacher(
            teacherId = teacherId,
            studentId = studentId,
            type = type,
            status = status,
            pageable = PageRequest.of(0, limit),
        )
        if (assignments.isEmpty()) {
            return emptyList()
        }

        val assignmentIds = assignments.map { it.id!! }
        val relationshipsById = teacherStudentRepository.findByIdInAndTeacherIdWithStudent(
            ids = assignments.map { it.teacherStudentId!! }.toSet(),
            teacherId = teacherId,
        ).associateBy { it.id!! }
        val problemCountsByAssignmentId = assignmentProblemRepository.countByAssignmentIdIn(assignmentIds)
            .associate { it.assignmentId to it.problemCount.toInt() }
        val questionCountsByAssignmentId = assignmentProblemQuestionRepository.countUnresolvedByAssignmentIdIn(assignmentIds)
            .associate { it.assignmentId to it.questionCount.toInt() }
        val submissionsByAssignmentId = assignmentSubmissionRepository.findByAssignmentIdIn(assignmentIds)
            .groupBy { it.assignmentId }
        return assignments.map { assignment ->
            val relationship = relationshipsById[assignment.teacherStudentId]
                ?: throw ApiException(ErrorCode.NOT_FOUND, "학생 관계를 찾을 수 없습니다.")
            val problemCount = problemCountsByAssignmentId[assignment.id] ?: 0
            val submissionStatus = submissionsByAssignmentId[assignment.id]?.firstOrNull()?.status
                ?: SubmissionStatus.NOT_SUBMITTED
            summary(
                assignment = assignment,
                relationship = relationship,
                problemCount = problemCount,
                questionCount = questionCountsByAssignmentId[assignment.id] ?: 0,
                submissionStatus = submissionStatus,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getDetail(
        teacherUserId: UUID,
        assignmentId: UUID,
    ): AssignmentDetailResponse {
        val teacherId = findTeacherId(teacherUserId)
        val assignment = assignmentRepository.findByIdAndTeacherId(assignmentId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "과제를 찾을 수 없습니다.")
        val relationship = findHistoricalRelationship(assignment.teacherStudentId!!, teacherId)
        val assignmentProblems = assignmentProblemRepository.findByAssignmentIdOrderBySortOrderAsc(assignment.id!!)
        val problemIds = assignmentProblems.map { it.problemId }
        val problemsById = if (problemIds.isEmpty()) {
            emptyMap()
        } else {
            problemRepository.findAllById(problemIds).associateBy { it.id!! }
        }
        val blocksByProblemId = if (problemIds.isEmpty()) {
            emptyMap()
        } else {
            problemBlockRepository.findByProblemIdIn(problemIds).groupBy { it.problemId }
        }
        val teacherSolutionFilesByProblemId = if (problemIds.isEmpty()) {
            emptyMap()
        } else {
            problemExplanationRepository.findActiveByProblemIdIn(problemIds)
                .filter {
                    it.sourceType == ProblemExplanationSourceType.TEACHER_SOLUTION_IMAGE &&
                        it.fileAssetId != null
                }
                .groupBy { it.problemId }
        }
        val submission = assignmentSubmissionRepository.findByAssignmentIdAndTeacherStudentId(
            assignmentId = assignment.id!!,
            teacherStudentId = relationship.id!!,
        )
        val answersByProblemId = if (submission == null || problemIds.isEmpty()) {
            emptyMap()
        } else {
            submissionAnswerRepository.findBySubmissionIdAndProblemIdIn(
                submissionId = submission.id!!,
                problemIds = problemIds,
            ).associateBy { answer -> answer.problemId }
        }
        val studentSolutionFilesByAnswerId = if (answersByProblemId.isEmpty()) {
            emptyMap()
        } else {
            submissionSolutionFileRepository.findBySubmissionAnswerIdIn(answersByProblemId.values.map { it.id!! })
                .groupBy { it.submissionAnswerId }
        }
        val now = Instant.now()
        val submissionStatus = submission?.status ?: SubmissionStatus.NOT_SUBMITTED
        val availability = AssignmentAvailability(
            status = assignment.status,
            dueAt = assignment.dueAt,
            submissionStatus = submissionStatus,
        )
        val canSolve = relationship.active && relationship.student.deletedAt == null &&
            assignmentAvailabilityPolicy.canSolve(availability, now)

        return AssignmentDetailResponse.from(
            assignment = assignment,
            student = relationship.student,
            expired = assignmentAvailabilityPolicy.isExpired(assignment.dueAt, now),
            canSolve = canSolve,
            questionCount = assignmentProblemQuestionRepository.countByAssignmentIdAndResolvedAtIsNull(assignment.id!!),
            submissionStatus = submissionStatus,
            problems = assignmentProblems.map { assignmentProblem ->
                val problem = problemsById[assignmentProblem.problemId]
                    ?: throw ApiException(ErrorCode.NOT_FOUND, "과제 문제를 찾을 수 없습니다.")
                val answer = answersByProblemId[assignmentProblem.problemId]
                AssignmentProblemDetailResponse.from(
                    assignmentProblem = assignmentProblem,
                    problem = problem,
                    blocks = blocksByProblemId[assignmentProblem.problemId].orEmpty().map(ProblemBlockResponse::from),
                    answer = answer,
                    studentSolutionFiles = answer?.id?.let { studentSolutionFilesByAnswerId[it] }.orEmpty(),
                    teacherSolutionFiles = teacherSolutionFilesByProblemId[assignmentProblem.problemId].orEmpty(),
                )
            },
        )
    }

    private fun validateLessonSession(
        teacherUserId: UUID,
        relationship: TeacherStudent,
        request: CreateAssignmentRequest,
    ) {
        val lessonSessionId = request.lessonSessionId ?: return
        val session = lessonSessionRepository.findOwnedByTeacherUserId(teacherUserId, lessonSessionId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "수업을 찾을 수 없습니다.")
        if (session.teacherStudentId != relationship.id || session.subjectId != request.subjectId) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "수업과 과제 학생/과목이 일치해야 합니다.")
        }
    }

    private fun findOwnedProblems(
        teacherId: UUID,
        request: CreateAssignmentRequest,
    ): List<Problem> {
        val problems = problemRepository.findByOwnerTeacherIdAndArchivedAtIsNullAndDeletedAtIsNullAndIdIn(
            ownerTeacherId = teacherId,
            ids = request.problemIds,
        )
        if (problems.size != request.problemIds.toSet().size) {
            throw ApiException(ErrorCode.NOT_FOUND, "문제를 찾을 수 없습니다.")
        }
        if (problems.any { it.subjectId != request.subjectId }) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "과제 문제는 과제 과목과 같아야 합니다.")
        }
        return problems
    }

    private fun summary(
        assignment: Assignment,
        relationship: TeacherStudent,
        problemCount: Int,
        questionCount: Int = 0,
        submissionStatus: SubmissionStatus,
    ): AssignmentSummaryResponse {
        Hibernate.initialize(relationship.student)
        val now = Instant.now()
        val availability = AssignmentAvailability(
            status = assignment.status,
            dueAt = assignment.dueAt,
            submissionStatus = submissionStatus,
        )
        val canSolve = relationship.active && relationship.student.deletedAt == null &&
            assignmentAvailabilityPolicy.canSolve(availability, now)
        return AssignmentSummaryResponse.from(
            assignment = assignment,
            student = relationship.student,
            problemCount = problemCount,
            questionCount = questionCount,
            expired = assignmentAvailabilityPolicy.isExpired(assignment.dueAt, now),
            canSolve = canSolve,
            submissionStatus = submissionStatus,
        )
    }

    private fun findActiveRelationshipForUpdate(
        teacherUserId: UUID,
        studentId: UUID,
    ): TeacherStudent =
        teacherStudentRepository.findActiveByTeacherUserIdAndStudentIdForUpdate(
            teacherUserId = teacherUserId,
            studentId = studentId,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "활성 학생 관계를 찾을 수 없습니다.")

    private fun findRelationship(
        teacherStudentId: UUID,
        teacherId: UUID,
    ): TeacherStudent =
        teacherStudentRepository.findById(teacherStudentId)
            .filter { it.teacher.id == teacherId && it.active && it.student.deletedAt == null }
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "활성 학생 관계를 찾을 수 없습니다.") }

    private fun findHistoricalRelationship(
        teacherStudentId: UUID,
        teacherId: UUID,
    ): TeacherStudent =
        teacherStudentRepository.findById(teacherStudentId)
            .filter { it.teacher.id == teacherId }
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "학생 관계를 찾을 수 없습니다.") }

    private fun findTeacherId(teacherUserId: UUID): UUID =
        teacherProfileRepository.findByUser_Id(teacherUserId)?.id
            ?: throw ApiException(ErrorCode.NOT_FOUND, "선생 프로필을 찾을 수 없습니다.")
}

package com.tutorkim.backend.assignment.service

import com.tutorkim.backend.assignment.dto.AssignmentProblemDetailResponse
import com.tutorkim.backend.assignment.dto.AttachStudentSolutionFileRequest
import com.tutorkim.backend.assignment.dto.SaveStudentAnswerRequest
import com.tutorkim.backend.assignment.dto.StudentAssignmentDetailResponse
import com.tutorkim.backend.assignment.dto.StudentAssignmentSummaryResponse
import com.tutorkim.backend.assignment.dto.SubmitStudentAnswersRequest
import com.tutorkim.backend.assignment.entity.Assignment
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.entity.AssignmentSubmission
import com.tutorkim.backend.assignment.entity.GradingDecision
import com.tutorkim.backend.assignment.entity.GradingStatus
import com.tutorkim.backend.assignment.entity.ProblemAttemptStatus
import com.tutorkim.backend.assignment.entity.ResultVisibility
import com.tutorkim.backend.assignment.entity.SubmissionAnswer
import com.tutorkim.backend.assignment.entity.SubmissionSolutionFile
import com.tutorkim.backend.assignment.entity.SubmissionStatus
import com.tutorkim.backend.assignment.repository.AssignmentProblemRepository
import com.tutorkim.backend.assignment.repository.AssignmentProblemQuestionRepository
import com.tutorkim.backend.assignment.repository.AssignmentRepository
import com.tutorkim.backend.assignment.repository.AssignmentSubmissionRepository
import com.tutorkim.backend.assignment.repository.SubmissionAnswerRepository
import com.tutorkim.backend.assignment.repository.SubmissionSolutionFileRepository
import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.file.repository.FileAssetRepository
import com.tutorkim.backend.problem.entity.Problem
import com.tutorkim.backend.problem.entity.ProblemAnswerType
import com.tutorkim.backend.problem.dto.ProblemBlockResponse
import com.tutorkim.backend.problem.entity.ProblemExplanationSourceType
import com.tutorkim.backend.problem.repository.ProblemBlockRepository
import com.tutorkim.backend.problem.repository.ProblemExplanationRepository
import com.tutorkim.backend.problem.repository.ProblemRepository
import com.tutorkim.backend.student.entity.TeacherStudent
import com.tutorkim.backend.student.repository.StudentProfileRepository
import com.tutorkim.backend.student.repository.TeacherStudentRepository
import com.tutorkim.backend.subject.entity.Subject
import com.tutorkim.backend.subject.repository.SubjectRepository
import java.math.BigDecimal
import org.springframework.data.domain.PageRequest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class StudentAssignmentService(
    private val studentProfileRepository: StudentProfileRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val subjectRepository: SubjectRepository,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentProblemRepository: AssignmentProblemRepository,
    private val assignmentProblemQuestionRepository: AssignmentProblemQuestionRepository,
    private val assignmentSubmissionRepository: AssignmentSubmissionRepository,
    private val submissionAnswerRepository: SubmissionAnswerRepository,
    private val submissionSolutionFileRepository: SubmissionSolutionFileRepository,
    private val fileAssetRepository: FileAssetRepository,
    private val problemRepository: ProblemRepository,
    private val problemBlockRepository: ProblemBlockRepository,
    private val problemExplanationRepository: ProblemExplanationRepository,
    private val answerGradingService: AnswerGradingService,
    private val assignmentAvailabilityPolicy: AssignmentAvailabilityPolicy = AssignmentAvailabilityPolicy(),
) {
    @Transactional(readOnly = true)
    fun listMyAssignments(
        studentUserId: UUID,
        includeExpired: Boolean,
    ): List<StudentAssignmentSummaryResponse> {
        val studentId = findStudentId(studentUserId)
        val now = Instant.now()
        val assignments = assignmentRepository.searchForStudent(
            studentId = studentId,
            statuses = STUDENT_VISIBLE_STATUSES,
            includeExpired = includeExpired,
            now = now,
            pageable = PageRequest.of(0, STUDENT_ASSIGNMENT_LIMIT),
        )
        if (assignments.isEmpty()) {
            return emptyList()
        }

        val assignmentIds = assignments.map { it.id!! }
        val relationshipsById = teacherStudentRepository.findByIdInAndStudentIdWithTeacher(
            ids = assignments.mapNotNull { it.teacherStudentId }.toSet(),
            studentId = studentId,
        ).associateBy { it.id!! }
        val subjectsById = subjectRepository.findAllById(assignments.map { it.subjectId }.toSet())
            .associateBy { it.id!! }
        val problemCountsByAssignmentId = assignmentProblemRepository.countByAssignmentIdIn(assignmentIds)
            .associate { it.assignmentId to it.problemCount.toInt() }
        val questionCountsByAssignmentId = assignmentProblemQuestionRepository.countUnresolvedByAssignmentIdIn(assignmentIds)
            .associate { it.assignmentId to it.questionCount.toInt() }
        val submissionsByAssignmentId = assignmentSubmissionRepository.findByAssignmentIdIn(assignmentIds)
            .filter { submission -> relationshipsById.containsKey(submission.teacherStudentId) }
            .associateBy { it.assignmentId }
        val submissionIds = submissionsByAssignmentId.values.map { it.id!! }
        val answeredCountsBySubmissionId = if (submissionIds.isEmpty()) {
            emptyMap()
        } else {
            submissionAnswerRepository.countBySubmissionIdIn(submissionIds)
                .associate { it.submissionId to it.answerCount.toInt() }
        }

        return assignments.map { assignment ->
            val relationship = relationshipsById[assignment.teacherStudentId]
                ?: throw ApiException(ErrorCode.NOT_FOUND, "학생 관계를 찾을 수 없습니다.")
            val subject = subjectsById[assignment.subjectId]
                ?: throw ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없습니다.")
            val submission = submissionsByAssignmentId[assignment.id]
            val submissionStatus = submission?.status ?: SubmissionStatus.NOT_SUBMITTED
            val availability = AssignmentAvailability(
                status = assignment.status,
                dueAt = assignment.dueAt,
                submissionStatus = submissionStatus,
            )
            StudentAssignmentSummaryResponse.from(
                assignment = assignment,
                teacher = relationship.teacher,
                subject = subject,
                expired = assignmentAvailabilityPolicy.isExpired(assignment.dueAt, now),
                canSolve = assignmentAvailabilityPolicy.canSolve(availability, now),
                problemCount = problemCountsByAssignmentId[assignment.id] ?: 0,
                answeredCount = submission?.id?.let { answeredCountsBySubmissionId[it] } ?: 0,
                questionCount = questionCountsByAssignmentId[assignment.id] ?: 0,
                submissionStatus = submissionStatus,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getMyAssignmentDetail(
        studentUserId: UUID,
        assignmentId: UUID,
    ): StudentAssignmentDetailResponse {
        val studentId = findStudentId(studentUserId)
        val assignment = assignmentRepository.findVisibleForStudent(
            assignmentId = assignmentId,
            studentId = studentId,
            statuses = STUDENT_VISIBLE_STATUSES,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "과제를 찾을 수 없습니다.")
        val relationship = findRelationship(assignment.teacherStudentId!!, studentId)
        val subject = subjectRepository.findById(assignment.subjectId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없습니다.") }
        val submission = assignmentSubmissionRepository.findByAssignmentIdAndTeacherStudentId(
            assignmentId = assignment.id!!,
            teacherStudentId = relationship.id!!,
        )
        val now = Instant.now()
        val submissionStatus = submission?.status ?: SubmissionStatus.NOT_SUBMITTED
        val availability = AssignmentAvailability(
            status = assignment.status,
            dueAt = assignment.dueAt,
            submissionStatus = submissionStatus,
        )

        return StudentAssignmentDetailResponse.from(
            assignment = assignment,
            teacher = relationship.teacher,
            subject = subject,
            expired = assignmentAvailabilityPolicy.isExpired(assignment.dueAt, now),
            canSolve = assignmentAvailabilityPolicy.canSolve(availability, now),
            questionCount = assignmentProblemQuestionRepository.countByAssignmentIdAndResolvedAtIsNull(assignment.id!!),
            submissionStatus = submissionStatus,
            problems = buildProblemDetails(assignment, submission),
        )
    }

    @Transactional
    fun saveMyAnswer(
        studentUserId: UUID,
        assignmentId: UUID,
        assignmentProblemId: UUID,
        request: SaveStudentAnswerRequest,
    ): StudentAssignmentDetailResponse {
        val context = findSolvableContext(studentUserId, assignmentId)
        val assignmentProblem = assignmentProblemRepository.findByIdAndAssignmentId(
            id = assignmentProblemId,
            assignmentId = context.assignment.id!!,
        )
            ?: throw ApiException(ErrorCode.NOT_FOUND, "과제 문제를 찾을 수 없습니다.")
        val problem = problemRepository.findById(assignmentProblem.problemId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "과제 문제를 찾을 수 없습니다.") }

        upsertAnswer(
            submission = context.submission,
            problem = problem,
            request = request,
            now = Instant.now(),
        )
        markPartial(context.submission)

        return getMyAssignmentDetail(studentUserId, assignmentId)
    }

    @Transactional
    fun attachMySolutionFile(
        studentUserId: UUID,
        assignmentId: UUID,
        assignmentProblemId: UUID,
        request: AttachStudentSolutionFileRequest,
    ): StudentAssignmentDetailResponse {
        val context = findSolvableContext(studentUserId, assignmentId)
        val assignmentProblem = assignmentProblemRepository.findByIdAndAssignmentId(
            id = assignmentProblemId,
            assignmentId = context.assignment.id!!,
        )
            ?: throw ApiException(ErrorCode.NOT_FOUND, "과제 문제를 찾을 수 없습니다.")
        val answer = submissionAnswerRepository.findBySubmissionIdAndProblemId(
            submissionId = context.submission.id!!,
            problemId = assignmentProblem.problemId,
        ) ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "답안을 먼저 저장해야 풀이 파일을 첨부할 수 있습니다.")

        fileAssetRepository.findByIdAndOwnerUserId(
            id = request.fileAssetId,
            ownerUserId = studentUserId,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "파일을 찾을 수 없습니다.")
        if (submissionSolutionFileRepository.existsByFileAssetId(request.fileAssetId)) {
            throw ApiException(ErrorCode.CONFLICT, "이미 첨부된 풀이 파일입니다.")
        }

        try {
            submissionSolutionFileRepository.saveAndFlush(
                SubmissionSolutionFile(
                    submissionAnswerId = answer.id!!,
                    fileAssetId = request.fileAssetId,
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            throw ApiException(ErrorCode.CONFLICT, "이미 첨부된 풀이 파일입니다.")
        }

        return getMyAssignmentDetail(studentUserId, assignmentId)
    }

    @Transactional
    fun submitMyAssignment(
        studentUserId: UUID,
        assignmentId: UUID,
    ): StudentAssignmentDetailResponse {
        val context = findSolvableContext(studentUserId, assignmentId)
        submitExistingAnswers(context)
        return getMyAssignmentDetail(studentUserId, assignmentId)
    }

    @Transactional
    fun submitMyAnswers(
        studentUserId: UUID,
        assignmentId: UUID,
        request: SubmitStudentAnswersRequest,
    ): StudentAssignmentDetailResponse {
        if (request.answers.map { it.problemId }.toSet().size != request.answers.size) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "답안 문제는 중복될 수 없습니다.")
        }

        val context = findSolvableContext(studentUserId, assignmentId)
        val assignmentProblems = assignmentProblemRepository.findByAssignmentIdOrderBySortOrderAsc(context.assignment.id!!)
        val assignmentProblemIdsByProblemId = assignmentProblems.associateBy { it.problemId }
        val requestedProblemIds = request.answers.map { it.problemId }.toSet()
        if (!assignmentProblemIdsByProblemId.keys.containsAll(requestedProblemIds)) {
            throw ApiException(ErrorCode.NOT_FOUND, "과제 문제를 찾을 수 없습니다.")
        }
        val problemsById = problemRepository.findAllById(requestedProblemIds).associateBy { it.id!! }
        val existingAnswersByProblemId = submissionAnswerRepository.findBySubmissionIdAndProblemIdIn(
            submissionId = context.submission.id!!,
            problemIds = requestedProblemIds,
        ).associateBy { it.problemId }
        val now = Instant.now()

        val answersToSave = request.answers.map { answer ->
            val problem = problemsById[answer.problemId]
                ?: throw ApiException(ErrorCode.NOT_FOUND, "과제 문제를 찾을 수 없습니다.")
            prepareAnswer(
                submission = context.submission,
                problem = problem,
                request = answer.toSaveRequest(),
                existingAnswer = existingAnswersByProblemId[answer.problemId],
                now = now,
            )
        }
        submissionAnswerRepository.saveAll(answersToSave)
        submissionAnswerRepository.flush()
        submitExistingAnswers(context)

        return getMyAssignmentDetail(studentUserId, assignmentId)
    }

    private fun buildProblemDetails(
        assignment: Assignment,
        submission: AssignmentSubmission?,
    ): List<AssignmentProblemDetailResponse> {
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
        val teacherSolutionFilesByProblemId = if (problemIds.isEmpty() || !canShowTeacherSolutionFiles(assignment, submission)) {
            emptyMap()
        } else {
            problemExplanationRepository.findActiveByProblemIdIn(problemIds)
                .filter {
                    it.sourceType == ProblemExplanationSourceType.TEACHER_SOLUTION_IMAGE &&
                        it.fileAssetId != null &&
                        it.visibleToStudent
                }
                .groupBy { it.problemId }
        }
        val answersByProblemId = if (submission == null || problemIds.isEmpty()) {
            emptyMap()
        } else {
            submissionAnswerRepository.findBySubmissionIdAndProblemIdIn(
                submissionId = submission.id!!,
                problemIds = problemIds,
            ).associateBy { it.problemId }
        }
        val studentSolutionFilesByAnswerId = if (answersByProblemId.isEmpty()) {
            emptyMap()
        } else {
            submissionSolutionFileRepository.findBySubmissionAnswerIdIn(answersByProblemId.values.map { it.id!! })
                .groupBy { it.submissionAnswerId }
        }

        return assignmentProblems.map { assignmentProblem ->
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
        }
    }

    private fun canShowTeacherSolutionFiles(
        assignment: Assignment,
        submission: AssignmentSubmission?,
    ): Boolean =
        assignment.resultVisibility == ResultVisibility.RELEASED ||
            (
                assignment.resultVisibility == ResultVisibility.IMMEDIATE &&
                    submission?.status in SUBMITTED_STATUSES
            )

    private fun findSolvableContext(
        studentUserId: UUID,
        assignmentId: UUID,
    ): StudentAssignmentSolveContext {
        val studentId = findStudentId(studentUserId)
        val assignment = assignmentRepository.findVisibleForStudent(
            assignmentId = assignmentId,
            studentId = studentId,
            statuses = STUDENT_VISIBLE_STATUSES,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "과제를 찾을 수 없습니다.")
        val relationship = findRelationship(assignment.teacherStudentId!!, studentId)
        val submission = assignmentSubmissionRepository.findByAssignmentIdAndTeacherStudentIdForUpdate(
            assignmentId = assignment.id!!,
            teacherStudentId = relationship.id!!,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "제출 정보를 찾을 수 없습니다.")
        val availability = AssignmentAvailability(
            status = assignment.status,
            dueAt = assignment.dueAt,
            submissionStatus = submission.status,
        )
        if (!assignmentAvailabilityPolicy.canSolve(availability, Instant.now())) {
            throw ApiException(ErrorCode.CONFLICT, "풀이 가능한 과제가 아닙니다.")
        }
        return StudentAssignmentSolveContext(
            assignment = assignment,
            submission = submission,
        )
    }

    private fun upsertAnswer(
        submission: AssignmentSubmission,
        problem: Problem,
        request: SaveStudentAnswerRequest,
        now: Instant,
    ): SubmissionAnswer {
        val existingAnswer = submissionAnswerRepository.findBySubmissionIdAndProblemId(
            submissionId = submission.id!!,
            problemId = problem.id!!,
        )
        return submissionAnswerRepository.saveAndFlush(
            prepareAnswer(
                submission = submission,
                problem = problem,
                request = request,
                existingAnswer = existingAnswer,
                now = now,
            ),
        )
    }

    private fun prepareAnswer(
        submission: AssignmentSubmission,
        problem: Problem,
        request: SaveStudentAnswerRequest,
        existingAnswer: SubmissionAnswer?,
        now: Instant,
    ): SubmissionAnswer {
        validateAnswer(problem.answerType, request)
        val gradingResult = answerGradingService.grade(
            AnswerKey(
                answerType = problem.answerType.toAssignmentAnswerType(),
                correctChoiceNumbers = problem.correctChoiceNumbers?.map { it.toInt() },
                correctNumericAnswer = problem.correctNumericAnswer,
            ),
            StudentAnswer(
                selectedChoiceNumbers = request.selectedChoiceNumbers,
                numericAnswer = request.numericAnswer,
                unknown = request.unknown,
            ),
        )
        val answer = existingAnswer ?: SubmissionAnswer(
            submissionId = submission.id!!,
            problemId = problem.id!!,
            createdAt = now,
        )
        val retryCount = if (existingAnswer == null) {
            0
        } else if (existingAnswer.attemptStatus in RETRY_ELIGIBLE_ATTEMPT_STATUSES) {
            existingAnswer.retryCount + 1
        } else {
            existingAnswer.retryCount
        }

        answer.selectedChoiceNumbers = request.selectedChoiceNumbers.map { it.toShort() }.ifEmpty { null }
        answer.numericAnswer = request.numericAnswer
        answer.unknown = request.unknown
        answer.retryCount = retryCount
        answer.attemptStatus = attemptStatus(gradingResult, existingAnswer)
        answer.autoIsCorrect = gradingResult.isCorrect
        answer.isCorrect = gradingResult.isCorrect
        answer.autoGradedAt = if (gradingResult.decision == GradingDecision.AUTO_GRADED) now else null
        answer.updatedAt = now
        return answer
    }

    private fun validateAnswer(
        answerType: ProblemAnswerType,
        request: SaveStudentAnswerRequest,
    ) {
        if (request.unknown) {
            if (request.selectedChoiceNumbers.isNotEmpty() || request.numericAnswer != null) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "모르겠어요 답안에는 선택지나 숫자 답안을 함께 보낼 수 없습니다.")
            }
            return
        }
        if (request.selectedChoiceNumbers.size != request.selectedChoiceNumbers.toSet().size) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "선택지는 중복될 수 없습니다.")
        }
        when (answerType) {
            ProblemAnswerType.SINGLE_CHOICE -> {
                if (request.selectedChoiceNumbers.size != 1 || request.numericAnswer != null) {
                    throw ApiException(ErrorCode.VALIDATION_ERROR, "객관식 단일 정답은 선택지 1개만 제출해야 합니다.")
                }
            }
            ProblemAnswerType.MULTIPLE_CHOICE -> {
                if (request.selectedChoiceNumbers.isEmpty() || request.numericAnswer != null) {
                    throw ApiException(ErrorCode.VALIDATION_ERROR, "객관식 복수 정답은 선택지를 1개 이상 제출해야 합니다.")
                }
            }
            ProblemAnswerType.NUMERIC -> {
                if (request.selectedChoiceNumbers.isNotEmpty() || request.numericAnswer == null) {
                    throw ApiException(ErrorCode.VALIDATION_ERROR, "주관식 숫자 답안은 숫자 답안만 제출해야 합니다.")
                }
            }
        }
    }

    private fun attemptStatus(
        gradingResult: GradingResult,
        existingAnswer: SubmissionAnswer?,
    ): ProblemAttemptStatus =
        when (gradingResult.decision) {
            GradingDecision.UNKNOWN -> ProblemAttemptStatus.UNKNOWN
            GradingDecision.MANUAL_REVIEW_REQUIRED -> ProblemAttemptStatus.PENDING
            GradingDecision.AUTO_GRADED -> {
                if (gradingResult.isCorrect == true) {
                    if (existingAnswer == null || existingAnswer.retryCount == 0 && existingAnswer.attemptStatus != ProblemAttemptStatus.WRONG_FIRST) {
                        ProblemAttemptStatus.CORRECT_FIRST
                    } else {
                        ProblemAttemptStatus.CORRECT_RETRY
                    }
                } else {
                    ProblemAttemptStatus.WRONG_FIRST
                }
            }
        }

    private fun markPartial(submission: AssignmentSubmission) {
        if (submission.status == SubmissionStatus.NOT_SUBMITTED) {
            submission.status = SubmissionStatus.PARTIAL
        }
        submission.updatedAt = Instant.now()
        assignmentSubmissionRepository.saveAndFlush(submission)
    }

    private fun submitExistingAnswers(context: StudentAssignmentSolveContext) {
        val assignmentProblems = assignmentProblemRepository.findByAssignmentIdOrderBySortOrderAsc(context.assignment.id!!)
        val problemIds = assignmentProblems.map { it.problemId }
        val answersByProblemId = submissionAnswerRepository.findBySubmissionIdAndProblemIdIn(
            submissionId = context.submission.id!!,
            problemIds = problemIds,
        ).associateBy { it.problemId }
        if (!answersByProblemId.keys.containsAll(problemIds)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "모든 문제를 풀거나 모르겠어요로 표시해야 제출할 수 있습니다.")
        }

        val now = Instant.now()
        val totalPoints = assignmentProblems.fold(BigDecimal.ZERO) { total, problem -> total + problem.points }
        val score = assignmentProblems.fold(BigDecimal.ZERO) { total, assignmentProblem ->
            val answer = answersByProblemId[assignmentProblem.problemId]
            if (answer?.isCorrect == true) total + assignmentProblem.points else total
        }
        val hasManualReview = answersByProblemId.values.any { answer ->
            !answer.unknown && answer.autoIsCorrect == null
        }

        context.submission.status = SubmissionStatus.SUBMITTED
        context.submission.gradingStatus = if (hasManualReview) GradingStatus.NOT_GRADED else GradingStatus.AUTO_GRADED
        context.submission.score = score
        context.submission.totalPoints = totalPoints
        context.submission.submittedAt = now
        context.submission.gradedAt = if (hasManualReview) null else now
        context.submission.updatedAt = now
        assignmentSubmissionRepository.saveAndFlush(context.submission)
    }

    private fun ProblemAnswerType.toAssignmentAnswerType(): com.tutorkim.backend.assignment.entity.AnswerType =
        com.tutorkim.backend.assignment.entity.AnswerType.valueOf(name)

    private fun findRelationship(
        teacherStudentId: UUID,
        studentId: UUID,
    ): TeacherStudent =
        teacherStudentRepository.findByIdAndStudentIdWithTeacher(
            id = teacherStudentId,
            studentId = studentId,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "학생 관계를 찾을 수 없습니다.")

    private fun findStudentId(studentUserId: UUID): UUID =
        studentProfileRepository.findByUser_IdAndDeletedAtIsNull(studentUserId)?.id
            ?: throw ApiException(ErrorCode.NOT_FOUND, "학생 프로필을 찾을 수 없습니다.")

    private companion object {
        val STUDENT_VISIBLE_STATUSES = setOf(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED)
        val RETRY_ELIGIBLE_ATTEMPT_STATUSES = setOf(
            ProblemAttemptStatus.WRONG_FIRST,
            ProblemAttemptStatus.CORRECT_RETRY,
        )
        val SUBMITTED_STATUSES = setOf(
            SubmissionStatus.SUBMITTED,
            SubmissionStatus.LATE_SUBMITTED,
        )
        const val STUDENT_ASSIGNMENT_LIMIT = 100
    }

    private data class StudentAssignmentSolveContext(
        val assignment: Assignment,
        val submission: AssignmentSubmission,
    )
}

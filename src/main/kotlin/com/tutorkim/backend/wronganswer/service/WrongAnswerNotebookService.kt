package com.tutorkim.backend.wronganswer.service

import com.tutorkim.backend.assignment.entity.Assignment
import com.tutorkim.backend.assignment.entity.AssignmentProblem
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.entity.AssignmentSubmission
import com.tutorkim.backend.assignment.entity.AssignmentTarget
import com.tutorkim.backend.assignment.entity.AssignmentType
import com.tutorkim.backend.assignment.entity.ProblemAttemptStatus
import com.tutorkim.backend.assignment.entity.ResultVisibility
import com.tutorkim.backend.assignment.entity.SubmissionAnswer
import com.tutorkim.backend.assignment.entity.SubmissionStatus
import com.tutorkim.backend.assignment.repository.AssignmentProblemRepository
import com.tutorkim.backend.assignment.repository.AssignmentRepository
import com.tutorkim.backend.assignment.repository.AssignmentSubmissionRepository
import com.tutorkim.backend.assignment.repository.AssignmentTargetRepository
import com.tutorkim.backend.assignment.repository.SubmissionAnswerRepository
import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.problem.entity.Problem
import com.tutorkim.backend.problem.repository.ProblemRepository
import com.tutorkim.backend.student.entity.TeacherStudent
import com.tutorkim.backend.student.repository.StudentProfileRepository
import com.tutorkim.backend.student.repository.TeacherProfileRepository
import com.tutorkim.backend.student.repository.TeacherStudentRepository
import com.tutorkim.backend.student.repository.TeacherStudentSubjectRepository
import com.tutorkim.backend.wronganswer.dto.CreateWrongAnswerNotebookRequest
import com.tutorkim.backend.wronganswer.dto.WrongAnswerNotebookListResponse
import com.tutorkim.backend.wronganswer.dto.WrongAnswerNotebookResponse
import com.tutorkim.backend.wronganswer.dto.WrongAnswerNotebookSourceItemResponse
import com.tutorkim.backend.wronganswer.dto.WrongAnswerNotebookSourceType
import com.tutorkim.backend.wronganswer.dto.WrongAnswerNotebookSourcesResponse
import com.tutorkim.backend.wronganswer.entity.WrongAnswerNotebook
import com.tutorkim.backend.wronganswer.entity.WrongAnswerNotebookProblem
import com.tutorkim.backend.wronganswer.entity.WrongAnswerNotebookStatus
import com.tutorkim.backend.wronganswer.repository.WrongAnswerNotebookProblemRepository
import com.tutorkim.backend.wronganswer.repository.WrongAnswerNotebookRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class WrongAnswerNotebookService(
    private val teacherProfileRepository: TeacherProfileRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val teacherStudentSubjectRepository: TeacherStudentSubjectRepository,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentProblemRepository: AssignmentProblemRepository,
    private val assignmentTargetRepository: AssignmentTargetRepository,
    private val assignmentSubmissionRepository: AssignmentSubmissionRepository,
    private val submissionAnswerRepository: SubmissionAnswerRepository,
    private val problemRepository: ProblemRepository,
    private val wrongAnswerNotebookRepository: WrongAnswerNotebookRepository,
    private val wrongAnswerNotebookProblemRepository: WrongAnswerNotebookProblemRepository,
) {
    @Transactional(readOnly = true)
    fun getSources(
        teacherUserId: UUID,
        studentId: UUID,
        subjectId: UUID,
    ): WrongAnswerNotebookSourcesResponse {
        val teacherId = findTeacherId(teacherUserId)
        val relationship = findActiveRelationshipReadOnly(teacherUserId, studentId)
        if (!teacherStudentSubjectRepository.existsByTeacherStudent_IdAndSubject_Id(relationship.id!!, subjectId)) {
            throw ApiException(ErrorCode.NOT_FOUND, "학생 과목을 찾을 수 없습니다.")
        }
        return WrongAnswerNotebookSourcesResponse(
            previousNotebooks = findPreviousNotebookSources(
                teacherId = teacherId,
                relationship = relationship,
                subjectId = subjectId,
            ),
            assignments = findAssignmentSources(
                teacherId = teacherId,
                relationship = relationship,
                subjectId = subjectId,
            ),
        )
    }

    @Transactional
    fun createDraft(
        teacherUserId: UUID,
        studentId: UUID,
        request: CreateWrongAnswerNotebookRequest,
    ): WrongAnswerNotebookResponse {
        val teacherId = findTeacherId(teacherUserId)
        val relationship = findActiveRelationship(teacherUserId, studentId)
        if (!teacherStudentSubjectRepository.existsByTeacherStudent_IdAndSubject_Id(relationship.id!!, request.subjectId)) {
            throw ApiException(ErrorCode.NOT_FOUND, "학생 과목을 찾을 수 없습니다.")
        }
        validateUniqueRefs(request)
        val sourceItems = resolveSourceProblems(
            teacherId = teacherId,
            relationship = relationship,
            subjectId = request.subjectId,
            request = request,
        )

        val now = Instant.now()
        val notebook = wrongAnswerNotebookRepository.saveAndFlush(
            WrongAnswerNotebook(
                teacherId = teacherId,
                teacherStudentId = relationship.id!!,
                subjectId = request.subjectId,
                title = request.title.trim(),
                sourceSummary = sourceSummary(sourceItems),
                dueAt = request.dueAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
        wrongAnswerNotebookProblemRepository.saveAll(
            sourceItems.mapIndexed { index, source ->
                WrongAnswerNotebookProblem(
                    notebookId = notebook.id!!,
                    problemId = source.problem.id!!,
                    sourceAssignmentProblemId = source.assignmentProblem.id!!,
                    uniqueProblemId = source.uniqueProblemId,
                    sortOrder = index + 1,
                    createdAt = now,
                )
            },
        )
        return WrongAnswerNotebookResponse.from(notebook, sourceItems.size)
    }

    @Transactional
    fun publish(
        teacherUserId: UUID,
        notebookId: UUID,
    ): WrongAnswerNotebookResponse {
        val teacherId = findTeacherId(teacherUserId)
        val notebook = wrongAnswerNotebookRepository.findByIdAndTeacherIdForUpdate(notebookId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "오답노트를 찾을 수 없습니다.")
        if (notebook.status != WrongAnswerNotebookStatus.DRAFT) {
            throw ApiException(ErrorCode.CONFLICT, "초안 오답노트만 발행할 수 있습니다.")
        }
        val notebookProblems = wrongAnswerNotebookProblemRepository.findByNotebookIdOrderBySortOrderAsc(notebook.id!!)
        if (notebookProblems.isEmpty()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "오답노트에는 최소 1개의 문제가 필요합니다.")
        }
        val relationship = findActiveRelationshipForPublish(notebook.teacherStudentId, teacherId, notebook.subjectId)

        val now = Instant.now()
        val assignment = assignmentRepository.saveAndFlush(
            Assignment(
                teacherId = teacherId,
                teacherStudentId = relationship.id!!,
                subjectId = notebook.subjectId,
                title = notebook.title,
                assignmentType = AssignmentType.REVIEW_SET,
                status = AssignmentStatus.PUBLISHED,
                resultVisibility = ResultVisibility.IMMEDIATE,
                dueAt = notebook.dueAt,
                publishedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        assignmentTargetRepository.save(
            AssignmentTarget(
                assignmentId = assignment.id!!,
                teacherStudentId = relationship.id!!,
                createdAt = now,
            ),
        )
        assignmentProblemRepository.saveAll(
            notebookProblems.mapIndexed { index, notebookProblem ->
                AssignmentProblem(
                    assignmentId = assignment.id!!,
                    problemId = notebookProblem.problemId,
                    sortOrder = index + 1,
                    points = BigDecimal.ONE,
                    createdAt = now,
                )
            },
        )
        assignmentSubmissionRepository.save(
            AssignmentSubmission(
                assignmentId = assignment.id!!,
                teacherStudentId = relationship.id!!,
                status = SubmissionStatus.NOT_SUBMITTED,
                totalPoints = BigDecimal.valueOf(notebookProblems.size.toLong()),
                createdAt = now,
                updatedAt = now,
            ),
        )

        notebook.assignmentId = assignment.id!!
        notebook.status = WrongAnswerNotebookStatus.PUBLISHED
        notebook.publishedAt = now
        notebook.updatedAt = now
        val savedNotebook = wrongAnswerNotebookRepository.saveAndFlush(notebook)

        return WrongAnswerNotebookResponse.from(savedNotebook, notebookProblems.size)
    }

    @Transactional(readOnly = true)
    fun listForTeacherStudent(
        teacherUserId: UUID,
        studentId: UUID,
        includeExpired: Boolean,
    ): List<WrongAnswerNotebookListResponse> {
        val relationship = findActiveRelationshipReadOnly(teacherUserId, studentId)
        return listForRelationships(
            teacherStudentIds = listOf(relationship.id!!),
            includeExpired = includeExpired,
            studentVisibleOnly = false,
        )
    }

    @Transactional(readOnly = true)
    fun listForStudent(
        studentUserId: UUID,
        includeExpired: Boolean,
    ): List<WrongAnswerNotebookListResponse> {
        val studentId = studentProfileRepository.findByUser_IdAndDeletedAtIsNull(studentUserId)?.id
            ?: throw ApiException(ErrorCode.NOT_FOUND, "학생 프로필을 찾을 수 없습니다.")
        val relationships = teacherStudentRepository.findActiveByStudentIdWithTeacher(studentId)
        if (relationships.isEmpty()) {
            return emptyList()
        }
        return listForRelationships(
            teacherStudentIds = relationships.map { it.id!! },
            includeExpired = includeExpired,
            studentVisibleOnly = true,
        )
    }

    private fun listForRelationships(
        teacherStudentIds: Collection<UUID>,
        includeExpired: Boolean,
        studentVisibleOnly: Boolean,
    ): List<WrongAnswerNotebookListResponse> {
        val now = Instant.now()
        val notebooks = if (studentVisibleOnly) {
            wrongAnswerNotebookRepository.findByTeacherStudentIdInAndStatusOrderByCreatedAtDesc(
                teacherStudentIds = teacherStudentIds,
                status = WrongAnswerNotebookStatus.PUBLISHED,
            )
        } else {
            wrongAnswerNotebookRepository.findByTeacherStudentIdInOrderByCreatedAtDesc(teacherStudentIds)
        }.filter { includeExpired || it.dueAt == null || !it.dueAt!!.isBefore(now) }
        if (notebooks.isEmpty()) {
            return emptyList()
        }
        val notebookIds = notebooks.map { it.id!! }
        val problemCountsByNotebookId = wrongAnswerNotebookProblemRepository.countByNotebookIdIn(notebookIds)
            .associate { it.notebookId to it.problemCount.toInt() }
        val assignmentIds = notebooks.mapNotNull { it.assignmentId }
        val submissionsByAssignmentId = if (assignmentIds.isEmpty()) {
            emptyMap()
        } else {
            assignmentSubmissionRepository.findByAssignmentIdIn(assignmentIds).associateBy { it.assignmentId }
        }
        val submissionIds = submissionsByAssignmentId.values.map { it.id!! }
        val answeredCountsBySubmissionId = if (submissionIds.isEmpty()) {
            emptyMap()
        } else {
            submissionAnswerRepository.countBySubmissionIdIn(submissionIds)
                .associate { it.submissionId to it.answerCount.toInt() }
        }

        return notebooks.map { notebook ->
            val submission = notebook.assignmentId?.let { submissionsByAssignmentId[it] }
            WrongAnswerNotebookListResponse.from(
                notebook = notebook,
                problemCount = problemCountsByNotebookId[notebook.id] ?: 0,
                answeredCount = submission?.id?.let { answeredCountsBySubmissionId[it] } ?: 0,
                submissionStatus = submission?.status ?: SubmissionStatus.NOT_SUBMITTED,
                now = now,
            )
        }
    }

    private fun validateUniqueRefs(request: CreateWrongAnswerNotebookRequest) {
        if (request.problemRefs.map { it.uniqueProblemId.trim() }.toSet().size != request.problemRefs.size) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "오답노트 문제는 중복될 수 없습니다.")
        }
        if (request.problemRefs.map { it.sourceAssignmentProblemId }.toSet().size != request.problemRefs.size) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "오답노트 문제는 중복될 수 없습니다.")
        }
    }

    private fun findAssignmentSources(
        teacherId: UUID,
        relationship: TeacherStudent,
        subjectId: UUID,
    ): List<WrongAnswerNotebookSourceItemResponse> {
        val relationshipId = relationship.id!!
        val assignments = assignmentRepository.findByTeacherStudentIdAndSubjectIdAndAssignmentTypeInAndStatusInOrderByCreatedAtDesc(
            teacherStudentId = relationshipId,
            subjectId = subjectId,
            assignmentTypes = WRONG_ANSWER_SOURCE_ASSIGNMENT_TYPES,
            statuses = WRONG_ANSWER_SOURCE_ASSIGNMENT_STATUSES,
        )
        if (assignments.isEmpty()) {
            return emptyList()
        }
        val assignmentIds = assignments.map { it.id!! }
        val assignmentsById = assignments.associateBy { it.id!! }
        val assignmentOrder = assignmentIds.withIndex().associate { it.value to it.index }
        val assignmentProblems = assignmentProblemRepository.findByAssignmentIdIn(assignmentIds)
            .sortedWith(
                compareBy<AssignmentProblem> { assignmentOrder[it.assignmentId] ?: Int.MAX_VALUE }
                    .thenBy { it.sortOrder },
            )
        if (assignmentProblems.isEmpty()) {
            return emptyList()
        }
        val submissionsByAssignmentId = assignmentSubmissionRepository.findByAssignmentIdIn(assignmentIds)
            .filter { it.teacherStudentId == relationshipId && it.status in SUBMITTED_SOURCE_STATUSES }
            .associateBy { it.assignmentId }
        if (submissionsByAssignmentId.isEmpty()) {
            return emptyList()
        }
        val problemIds = assignmentProblems.map { it.problemId }.toSet()
        val answersBySubmissionAndProblemId = submissionAnswerRepository.findBySubmissionIdInAndProblemIdIn(
            submissionIds = submissionsByAssignmentId.values.map { it.id!! },
            problemIds = problemIds,
        ).associateBy { it.submissionId to it.problemId }
        val problemsById = findActiveProblemsById(
            teacherId = teacherId,
            subjectId = subjectId,
            problemIds = problemIds,
        )
        if (problemsById.isEmpty()) {
            return emptyList()
        }
        data class AssignmentSourceCandidate(
            val assignmentProblem: AssignmentProblem,
            val problem: Problem,
            val submission: AssignmentSubmission,
            val answer: SubmissionAnswer,
            val assignmentOrder: Int,
        )

        val sourceCandidates = assignmentProblems.mapNotNull { assignmentProblem ->
            val problem = problemsById[assignmentProblem.problemId] ?: return@mapNotNull null
            val submission = submissionsByAssignmentId[assignmentProblem.assignmentId] ?: return@mapNotNull null
            val answer = answersBySubmissionAndProblemId[submission.id!! to problem.id!!] ?: return@mapNotNull null
            AssignmentSourceCandidate(
                assignmentProblem = assignmentProblem,
                problem = problem,
                submission = submission,
                answer = answer,
                assignmentOrder = assignmentOrder[assignmentProblem.assignmentId] ?: Int.MAX_VALUE,
            )
        }.sortedWith(
            compareByDescending<AssignmentSourceCandidate> {
                it.submission.submittedAt ?: assignmentsById[it.assignmentProblem.assignmentId]?.createdAt ?: Instant.EPOCH
            }.thenBy { it.assignmentOrder }
                .thenBy { it.assignmentProblem.sortOrder },
        )

        val seenProblemIds = mutableSetOf<UUID>()
        return sourceCandidates.mapNotNull { candidate ->
            val problemId = candidate.problem.id!!
            if (!seenProblemIds.add(problemId)) {
                return@mapNotNull null
            }
            if (candidate.answer.attemptStatus !in WRONG_ANSWER_SOURCE_STATUSES) {
                return@mapNotNull null
            }
            WrongAnswerNotebookSourceItemResponse(
                uniqueProblemId = problemId.toString(),
                assignmentProblemId = candidate.assignmentProblem.id!!,
                sourceType = WrongAnswerNotebookSourceType.ASSIGNMENT,
                attemptStatus = candidate.answer.attemptStatus,
                retryCount = candidate.answer.retryCount,
                selected = true,
            )
        }
    }

    private fun findPreviousNotebookSources(
        teacherId: UUID,
        relationship: TeacherStudent,
        subjectId: UUID,
    ): List<WrongAnswerNotebookSourceItemResponse> {
        val notebooks = wrongAnswerNotebookRepository.findByTeacherStudentIdAndSubjectIdAndStatusOrderByPublishedAtDescCreatedAtDesc(
            teacherStudentId = relationship.id!!,
            subjectId = subjectId,
            status = WrongAnswerNotebookStatus.PUBLISHED,
        )
        if (notebooks.isEmpty()) {
            return emptyList()
        }
        val notebookIds = notebooks.map { it.id!! }
        val notebookById = notebooks.associateBy { it.id!! }
        val notebookProblemsByNotebookId = wrongAnswerNotebookProblemRepository.findByNotebookIdInOrderBySortOrderAsc(notebookIds)
            .groupBy { it.notebookId }
        val notebookProblems = notebooks.flatMap { notebook ->
            notebookProblemsByNotebookId[notebook.id!!].orEmpty().sortedBy { it.sortOrder }
        }
        if (notebookProblems.isEmpty()) {
            return emptyList()
        }
        val problemsById = findActiveProblemsById(
            teacherId = teacherId,
            subjectId = subjectId,
            problemIds = notebookProblems.map { it.problemId }.toSet(),
        )
        if (problemsById.isEmpty()) {
            return emptyList()
        }
        val reviewAssignmentIds = notebooks.mapNotNull { it.assignmentId }
        val reviewAssignmentProblemsByAssignmentAndProblemId = if (reviewAssignmentIds.isEmpty()) {
            emptyMap()
        } else {
            assignmentProblemRepository.findByAssignmentIdIn(reviewAssignmentIds)
                .associateBy { it.assignmentId to it.problemId }
        }
        val reviewSubmissionsByAssignmentId = if (reviewAssignmentIds.isEmpty()) {
            emptyMap()
        } else {
            assignmentSubmissionRepository.findByAssignmentIdIn(reviewAssignmentIds)
                .filter { it.teacherStudentId == relationship.id!! }
                .associateBy { it.assignmentId }
        }
        val reviewSubmissionIds = reviewSubmissionsByAssignmentId.values.map { it.id!! }
        val reviewAnswersBySubmissionAndProblemId = if (reviewSubmissionIds.isEmpty()) {
            emptyMap()
        } else {
            submissionAnswerRepository.findBySubmissionIdInAndProblemIdIn(
                submissionIds = reviewSubmissionIds,
                problemIds = problemsById.keys,
            ).associateBy { it.submissionId to it.problemId }
        }

        val seenProblemIds = mutableSetOf<UUID>()
        return notebookProblems.mapNotNull { notebookProblem ->
            val notebook = notebookById[notebookProblem.notebookId] ?: return@mapNotNull null
            val problem = problemsById[notebookProblem.problemId] ?: return@mapNotNull null
            if (!seenProblemIds.add(problem.id!!)) {
                return@mapNotNull null
            }
            val reviewAnswer = notebook.assignmentId?.let { assignmentId ->
                val reviewSubmission = reviewSubmissionsByAssignmentId[assignmentId]
                val reviewAssignmentProblem = reviewAssignmentProblemsByAssignmentAndProblemId[assignmentId to problem.id!!]
                if (reviewSubmission == null || reviewAssignmentProblem == null) {
                    null
                } else {
                    reviewAnswersBySubmissionAndProblemId[reviewSubmission.id!! to problem.id!!]
                }
            }
            if (reviewAnswer?.attemptStatus == ProblemAttemptStatus.CORRECT_FIRST) {
                return@mapNotNull null
            }
            WrongAnswerNotebookSourceItemResponse(
                uniqueProblemId = problem.id!!.toString(),
                assignmentProblemId = notebookProblem.sourceAssignmentProblemId,
                sourceType = WrongAnswerNotebookSourceType.PREVIOUS_NOTEBOOK,
                attemptStatus = reviewAnswer?.attemptStatus ?: ProblemAttemptStatus.PENDING,
                retryCount = reviewAnswer?.retryCount ?: 0,
                selected = true,
            )
        }
    }

    private fun findActiveProblemsById(
        teacherId: UUID,
        subjectId: UUID,
        problemIds: Collection<UUID>,
    ): Map<UUID, Problem> {
        if (problemIds.isEmpty()) {
            return emptyMap()
        }
        return problemRepository.findAllById(problemIds.toSet())
            .filter {
                it.ownerTeacherId == teacherId &&
                    it.subjectId == subjectId &&
                    it.archivedAt == null &&
                    it.deletedAt == null
            }
            .associateBy { it.id!! }
    }

    private fun resolveSourceProblems(
        teacherId: UUID,
        relationship: TeacherStudent,
        subjectId: UUID,
        request: CreateWrongAnswerNotebookRequest,
    ): List<SourceProblem> {
        val assignmentProblems = assignmentProblemRepository.findAllById(
            request.problemRefs.map { it.sourceAssignmentProblemId },
        ).associateBy { it.id!! }
        val assignments = assignmentRepository.findAllById(
            assignmentProblems.values.map { it.assignmentId }.toSet(),
        ).associateBy { it.id!! }
        val problems = problemRepository.findAllById(
            assignmentProblems.values.map { it.problemId }.toSet(),
        ).associateBy { it.id!! }
        val relationshipId = relationship.id!!
        val submissionsByAssignmentId = if (assignments.isEmpty()) {
            emptyMap()
        } else {
            assignmentSubmissionRepository
                .findByAssignmentIdIn(assignments.keys)
                .filter { it.teacherStudentId == relationshipId }
                .associateBy { it.assignmentId }
        }
        val submissionIds = submissionsByAssignmentId.values.map { it.id!! }
        val problemIds = assignmentProblems.values.map { it.problemId }.toSet()
        val answersBySubmissionAndProblemId = if (submissionIds.isEmpty()) {
            emptyMap()
        } else {
            submissionAnswerRepository.findBySubmissionIdInAndProblemIdIn(submissionIds, problemIds)
                .associateBy { it.submissionId to it.problemId }
        }

        val sourceProblems = request.problemRefs.map { ref ->
            val assignmentProblem = assignmentProblems[ref.sourceAssignmentProblemId]
                ?: throw ApiException(ErrorCode.NOT_FOUND, "과제 문제를 찾을 수 없습니다.")
            val assignment = assignments[assignmentProblem.assignmentId]
                ?: throw ApiException(ErrorCode.NOT_FOUND, "과제를 찾을 수 없습니다.")
            if (assignment.teacherId != teacherId || assignment.teacherStudentId != relationship.id) {
                throw ApiException(ErrorCode.NOT_FOUND, "과제 문제를 찾을 수 없습니다.")
            }
            val problem = problems[assignmentProblem.problemId]
                ?: throw ApiException(ErrorCode.NOT_FOUND, "문제를 찾을 수 없습니다.")
            if (problem.ownerTeacherId != teacherId ||
                problem.subjectId != subjectId ||
                problem.archivedAt != null ||
                problem.deletedAt != null
            ) {
                throw ApiException(ErrorCode.NOT_FOUND, "문제를 찾을 수 없습니다.")
            }
            val submission = submissionsByAssignmentId[assignment.id!!]
            validateWrongAnswerHistory(
                submission = submission,
                answer = submission?.id?.let { submissionId ->
                    answersBySubmissionAndProblemId[submissionId to problem.id!!]
                },
            )
            SourceProblem(
                uniqueProblemId = ref.uniqueProblemId.trim(),
                assignmentTitle = assignment.title,
                assignmentProblem = assignmentProblem,
                problem = problem,
            )
        }
        if (sourceProblems.map { it.problem.id!! }.toSet().size != sourceProblems.size) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "오답노트 문제는 중복될 수 없습니다.")
        }
        return sourceProblems
    }

    private fun validateWrongAnswerHistory(
        submission: AssignmentSubmission?,
        answer: SubmissionAnswer?,
    ) {
        if (submission == null) {
            throw ApiException(ErrorCode.NOT_FOUND, "오답 제출 기록을 찾을 수 없습니다.")
        }
        if (submission.status !in SUBMITTED_SOURCE_STATUSES) {
            throw ApiException(ErrorCode.NOT_FOUND, "오답 제출 기록을 찾을 수 없습니다.")
        }
        if (answer == null) {
            throw ApiException(ErrorCode.NOT_FOUND, "오답 제출 기록을 찾을 수 없습니다.")
        }
        if (answer.attemptStatus !in WRONG_ANSWER_SOURCE_STATUSES) {
            throw ApiException(ErrorCode.NOT_FOUND, "오답 제출 기록을 찾을 수 없습니다.")
        }
    }

    private fun sourceSummary(sourceProblems: List<SourceProblem>): String? {
        val titles = sourceProblems.map { it.assignmentTitle }.distinct()
        return when (titles.size) {
            0 -> null
            1 -> titles.single()
            else -> "${titles.first()} 외 ${titles.size - 1}개"
        }
    }

    private fun findTeacherId(teacherUserId: UUID): UUID =
        teacherProfileRepository.findByUser_Id(teacherUserId)?.id
            ?: throw ApiException(ErrorCode.NOT_FOUND, "선생 프로필을 찾을 수 없습니다.")

    private fun findActiveRelationship(
        teacherUserId: UUID,
        studentId: UUID,
    ): TeacherStudent =
        teacherStudentRepository.findActiveByTeacherUserIdAndStudentIdForUpdate(
            teacherUserId = teacherUserId,
            studentId = studentId,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "학생 관계를 찾을 수 없습니다.")

    private fun findActiveRelationshipReadOnly(
        teacherUserId: UUID,
        studentId: UUID,
    ): TeacherStudent =
        teacherStudentRepository.findActiveByTeacherUserIdAndStudentId(
            teacherUserId = teacherUserId,
            studentId = studentId,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "학생 관계를 찾을 수 없습니다.")

    private fun findActiveRelationshipForPublish(
        teacherStudentId: UUID,
        teacherId: UUID,
        subjectId: UUID,
    ): TeacherStudent {
        val relationship = teacherStudentRepository.findActiveByIdAndTeacherIdForUpdate(
            id = teacherStudentId,
            teacherId = teacherId,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "학생 관계를 찾을 수 없습니다.")
        if (!teacherStudentSubjectRepository.existsByTeacherStudent_IdAndSubject_Id(relationship.id!!, subjectId)) {
            throw ApiException(ErrorCode.NOT_FOUND, "학생 과목을 찾을 수 없습니다.")
        }
        return relationship
    }

    private data class SourceProblem(
        val uniqueProblemId: String,
        val assignmentTitle: String,
        val assignmentProblem: AssignmentProblem,
        val problem: Problem,
    )

    companion object {
        private val WRONG_ANSWER_SOURCE_STATUSES = setOf(
            ProblemAttemptStatus.WRONG_FIRST,
            ProblemAttemptStatus.CORRECT_RETRY,
            ProblemAttemptStatus.UNKNOWN,
        )
        private val WRONG_ANSWER_SOURCE_ASSIGNMENT_TYPES = setOf(
            AssignmentType.HOMEWORK,
            AssignmentType.TEST,
        )
        private val WRONG_ANSWER_SOURCE_ASSIGNMENT_STATUSES = setOf(
            AssignmentStatus.PUBLISHED,
            AssignmentStatus.CLOSED,
        )
        private val SUBMITTED_SOURCE_STATUSES = setOf(
            SubmissionStatus.SUBMITTED,
            SubmissionStatus.LATE_SUBMITTED,
        )
    }
}

package com.tutorkim.backend.assignment.service

import com.tutorkim.backend.assignment.dto.AnswerAssignmentQuestionRequest
import com.tutorkim.backend.assignment.dto.AssignmentQuestionAnswerResponse
import com.tutorkim.backend.assignment.dto.AssignmentQuestionResponse
import com.tutorkim.backend.assignment.dto.CreateAssignmentQuestionRequest
import com.tutorkim.backend.assignment.entity.AssignmentProblem
import com.tutorkim.backend.assignment.entity.AssignmentProblemQuestion
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.repository.AssignmentProblemQuestionRepository
import com.tutorkim.backend.assignment.repository.AssignmentProblemRepository
import com.tutorkim.backend.assignment.repository.AssignmentRepository
import com.tutorkim.backend.assignment.repository.AssignmentSubmissionRepository
import com.tutorkim.backend.assignment.repository.SubmissionAnswerRepository
import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.file.repository.FileAssetRepository
import com.tutorkim.backend.problem.entity.ProblemExplanation
import com.tutorkim.backend.problem.entity.ProblemExplanationSourceType
import com.tutorkim.backend.problem.repository.ProblemExplanationRepository
import com.tutorkim.backend.problem.repository.ProblemRepository
import com.tutorkim.backend.student.entity.StudentProfile
import com.tutorkim.backend.student.entity.TeacherStudent
import com.tutorkim.backend.student.repository.StudentProfileRepository
import com.tutorkim.backend.student.repository.TeacherProfileRepository
import com.tutorkim.backend.student.repository.TeacherStudentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AssignmentQuestionService(
    private val studentProfileRepository: StudentProfileRepository,
    private val teacherProfileRepository: TeacherProfileRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentProblemRepository: AssignmentProblemRepository,
    private val assignmentSubmissionRepository: AssignmentSubmissionRepository,
    private val submissionAnswerRepository: SubmissionAnswerRepository,
    private val assignmentProblemQuestionRepository: AssignmentProblemQuestionRepository,
    private val fileAssetRepository: FileAssetRepository,
    private val problemRepository: ProblemRepository,
    private val problemExplanationRepository: ProblemExplanationRepository,
) {
    @Transactional
    fun createStudentQuestion(
        studentUserId: UUID,
        assignmentId: UUID,
        assignmentProblemId: UUID,
        request: CreateAssignmentQuestionRequest,
    ): AssignmentQuestionResponse {
        val student = findStudent(studentUserId)
        val assignment = assignmentRepository.findVisibleForStudent(
            assignmentId = assignmentId,
            studentId = student.id!!,
            statuses = STUDENT_QUESTION_STATUSES,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "과제를 찾을 수 없습니다.")
        val relationship = findRelationshipForStudent(assignment.teacherStudentId!!, student.id!!)
        val assignmentProblem = assignmentProblemRepository.findByIdAndAssignmentId(
            id = assignmentProblemId,
            assignmentId = assignment.id!!,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "과제 문제를 찾을 수 없습니다.")
        val submission = assignmentSubmissionRepository.findByAssignmentIdAndTeacherStudentId(
            assignmentId = assignment.id!!,
            teacherStudentId = relationship.id!!,
        )
        val answer = submission?.id?.let {
            submissionAnswerRepository.findBySubmissionIdAndProblemId(
                submissionId = it,
                problemId = assignmentProblem.problemId,
            )
        }

        val now = Instant.now()
        val question = assignmentProblemQuestionRepository.saveAndFlush(
            AssignmentProblemQuestion(
                assignmentId = assignment.id!!,
                assignmentProblemId = assignmentProblem.id!!,
                submissionAnswerId = answer?.id,
                teacherStudentId = relationship.id!!,
                body = request.body.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )

        return AssignmentQuestionResponse.from(question, assignmentProblem, student)
    }

    @Transactional(readOnly = true)
    fun listAssignmentQuestions(
        teacherUserId: UUID,
        assignmentId: UUID,
        unresolvedOnly: Boolean,
    ): List<AssignmentQuestionResponse> {
        val teacherId = findTeacherId(teacherUserId)
        val assignment = assignmentRepository.findByIdAndTeacherId(assignmentId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "과제를 찾을 수 없습니다.")
        val questions = if (unresolvedOnly) {
            assignmentProblemQuestionRepository.findByAssignmentIdAndResolvedAtIsNullOrderByCreatedAtDesc(assignment.id!!)
        } else {
            assignmentProblemQuestionRepository.findByAssignmentIdOrderByCreatedAtDesc(assignment.id!!)
        }
        if (questions.isEmpty()) {
            return emptyList()
        }
        val assignmentProblemsById = assignmentProblemRepository.findByAssignmentIdOrderBySortOrderAsc(assignment.id!!)
            .associateBy { it.id!! }
        val relationshipsById = teacherStudentRepository.findByIdInAndTeacherIdWithStudent(
            ids = questions.map { it.teacherStudentId }.toSet(),
            teacherId = teacherId,
        ).associateBy { it.id!! }

        return questions.map { question ->
            val assignmentProblem = assignmentProblemsById[question.assignmentProblemId]
                ?: throw ApiException(ErrorCode.NOT_FOUND, "과제 문제를 찾을 수 없습니다.")
            val relationship = relationshipsById[question.teacherStudentId]
                ?: throw ApiException(ErrorCode.NOT_FOUND, "학생 관계를 찾을 수 없습니다.")
            AssignmentQuestionResponse.from(question, assignmentProblem, relationship.student)
        }
    }

    @Transactional
    fun answerQuestionWithTeacherSolutionFile(
        teacherUserId: UUID,
        assignmentId: UUID,
        questionId: UUID,
        request: AnswerAssignmentQuestionRequest,
    ): AssignmentQuestionAnswerResponse {
        if (!request.persistToProblemBank) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "선생 풀이 파일은 문제은행에 저장해야 합니다.")
        }
        val teacherId = findTeacherId(teacherUserId)
        val assignment = assignmentRepository.findByIdAndTeacherId(assignmentId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "과제를 찾을 수 없습니다.")
        val question = assignmentProblemQuestionRepository.findByIdAndAssignmentIdForUpdate(questionId, assignment.id!!)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "질문을 찾을 수 없습니다.")
        if (question.resolvedAt != null) {
            throw ApiException(ErrorCode.CONFLICT, "이미 답변된 질문입니다.")
        }
        val assignmentProblem = assignmentProblemRepository.findByIdAndAssignmentId(
            id = question.assignmentProblemId,
            assignmentId = assignment.id!!,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "과제 문제를 찾을 수 없습니다.")
        val problem = problemRepository.findActiveByIdAndOwnerTeacherIdForUpdate(assignmentProblem.problemId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "문제를 찾을 수 없습니다.")
        fileAssetRepository.findByIdAndOwnerUserId(
            id = request.fileAssetId,
            ownerUserId = teacherUserId,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "파일을 찾을 수 없습니다.")

        val now = Instant.now()
        val teacherResponse = request.teacherResponse?.trim()?.takeIf { it.isNotBlank() }
        val explanation = problemExplanationRepository.saveAndFlush(
            ProblemExplanation(
                problemId = problem.id!!,
                sortOrder = nextActiveExplanationSortOrder(problem.id!!),
                sourceType = ProblemExplanationSourceType.TEACHER_SOLUTION_IMAGE,
                fileAssetId = request.fileAssetId,
                metadata = mapOf("teacherResponse" to teacherResponse).filterValues { it != null },
                visibleToStudent = request.visibleToStudent,
                createdFromQuestionId = question.id!!,
                createdBy = teacherUserId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        problem.hasExplanation = true
        problemRepository.saveAndFlush(problem)

        question.teacherResponse = teacherResponse
        question.persistedProblemExplanationId = explanation.id!!
        question.resolvedAt = now
        question.resolvedBy = teacherUserId
        question.updatedAt = now
        assignmentProblemQuestionRepository.saveAndFlush(question)

        return AssignmentQuestionAnswerResponse(
            questionId = question.id!!,
            problemId = problem.id!!,
            problemExplanationId = explanation.id!!,
            fileAssetId = request.fileAssetId,
            visibleToStudent = explanation.visibleToStudent,
            persistedToProblemBank = true,
        )
    }

    private fun nextActiveExplanationSortOrder(problemId: UUID): Int =
        (problemExplanationRepository.findMaxActiveSortOrderByProblemId(problemId) ?: 0) + 1

    private fun findStudent(studentUserId: UUID): StudentProfile =
        studentProfileRepository.findByUser_IdAndDeletedAtIsNull(studentUserId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "학생 프로필을 찾을 수 없습니다.")

    private fun findRelationshipForStudent(
        teacherStudentId: UUID,
        studentId: UUID,
    ): TeacherStudent =
        teacherStudentRepository.findByIdAndStudentIdWithTeacher(
            id = teacherStudentId,
            studentId = studentId,
        ) ?: throw ApiException(ErrorCode.NOT_FOUND, "학생 관계를 찾을 수 없습니다.")

    private fun findTeacherId(teacherUserId: UUID): UUID =
        teacherProfileRepository.findByUser_Id(teacherUserId)?.id
            ?: throw ApiException(ErrorCode.NOT_FOUND, "선생 프로필을 찾을 수 없습니다.")

    private companion object {
        val STUDENT_QUESTION_STATUSES = setOf(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED)
    }
}

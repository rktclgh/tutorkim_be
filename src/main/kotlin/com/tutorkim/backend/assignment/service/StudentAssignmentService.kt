package com.tutorkim.backend.assignment.service

import com.tutorkim.backend.assignment.dto.AssignmentProblemDetailResponse
import com.tutorkim.backend.assignment.dto.StudentAssignmentDetailResponse
import com.tutorkim.backend.assignment.dto.StudentAssignmentSummaryResponse
import com.tutorkim.backend.assignment.entity.Assignment
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.entity.AssignmentSubmission
import com.tutorkim.backend.assignment.entity.SubmissionStatus
import com.tutorkim.backend.assignment.repository.AssignmentProblemRepository
import com.tutorkim.backend.assignment.repository.AssignmentRepository
import com.tutorkim.backend.assignment.repository.AssignmentSubmissionRepository
import com.tutorkim.backend.assignment.repository.SubmissionAnswerRepository
import com.tutorkim.backend.assignment.repository.SubmissionSolutionFileRepository
import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
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
import org.springframework.data.domain.PageRequest
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
    private val assignmentSubmissionRepository: AssignmentSubmissionRepository,
    private val submissionAnswerRepository: SubmissionAnswerRepository,
    private val submissionSolutionFileRepository: SubmissionSolutionFileRepository,
    private val problemRepository: ProblemRepository,
    private val problemBlockRepository: ProblemBlockRepository,
    private val problemExplanationRepository: ProblemExplanationRepository,
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
                questionCount = 0,
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
            questionCount = 0,
            submissionStatus = submissionStatus,
            problems = buildProblemDetails(assignment, submission),
        )
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
        val teacherSolutionFilesByProblemId = if (problemIds.isEmpty()) {
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
        const val STUDENT_ASSIGNMENT_LIMIT = 100
    }
}

package com.tutorkim.backend.assignment.dto

import com.tutorkim.backend.assignment.entity.Assignment
import com.tutorkim.backend.assignment.entity.AssignmentProblem
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.entity.AssignmentType
import com.tutorkim.backend.assignment.entity.ProblemAttemptStatus
import com.tutorkim.backend.assignment.entity.ResultVisibility
import com.tutorkim.backend.assignment.entity.SubmissionAnswer
import com.tutorkim.backend.assignment.entity.SubmissionSolutionFile
import com.tutorkim.backend.assignment.entity.SubmissionStatus
import com.tutorkim.backend.problem.dto.ProblemBlockResponse
import com.tutorkim.backend.problem.entity.Problem
import com.tutorkim.backend.problem.entity.ProblemAnswerType
import com.tutorkim.backend.problem.entity.ProblemExplanation
import com.tutorkim.backend.student.entity.StudentProfile
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class AssignmentDetailResponse(
    val id: UUID,
    val title: String,
    val assignmentType: AssignmentType,
    val student: AssignmentStudentResponse,
    val subjectId: UUID,
    val dueAt: Instant?,
    val expired: Boolean,
    val canSolve: Boolean,
    val questionCount: Int,
    val status: AssignmentStatus,
    val submissionStatus: SubmissionStatus,
    val resultVisibility: ResultVisibility,
    val problems: List<AssignmentProblemDetailResponse>,
) {
    companion object {
        fun from(
            assignment: Assignment,
            student: StudentProfile,
            expired: Boolean,
            canSolve: Boolean,
            submissionStatus: SubmissionStatus,
            problems: List<AssignmentProblemDetailResponse>,
        ): AssignmentDetailResponse =
            AssignmentDetailResponse(
                id = assignment.id!!,
                title = assignment.title,
                assignmentType = assignment.assignmentType,
                student = AssignmentStudentResponse.from(student),
                subjectId = assignment.subjectId,
                dueAt = assignment.dueAt,
                expired = expired,
                canSolve = canSolve,
                questionCount = 0,
                status = assignment.status,
                submissionStatus = submissionStatus,
                resultVisibility = assignment.resultVisibility,
                problems = problems,
            )
    }
}

data class AssignmentProblemDetailResponse(
    val assignmentProblemId: UUID,
    val problemId: UUID,
    val number: Int,
    val points: BigDecimal,
    val blocks: List<ProblemBlockResponse>,
    val answerType: ProblemAnswerType,
    val attemptStatus: ProblemAttemptStatus,
    val retryCount: Int,
    val studentAnswer: AssignmentStudentAnswerResponse?,
    val studentSolutionFiles: List<AssignmentSolutionFileResponse>,
    val teacherSolutionFiles: List<AssignmentTeacherSolutionFileResponse>,
) {
    companion object {
        fun from(
            assignmentProblem: AssignmentProblem,
            problem: Problem,
            blocks: List<ProblemBlockResponse>,
            answer: SubmissionAnswer?,
            studentSolutionFiles: List<SubmissionSolutionFile>,
            teacherSolutionFiles: List<ProblemExplanation>,
        ): AssignmentProblemDetailResponse =
            AssignmentProblemDetailResponse(
                assignmentProblemId = assignmentProblem.id!!,
                problemId = assignmentProblem.problemId,
                number = assignmentProblem.sortOrder,
                points = assignmentProblem.points,
                blocks = blocks,
                answerType = problem.answerType,
                attemptStatus = answer?.attemptStatus ?: ProblemAttemptStatus.PENDING,
                retryCount = answer?.retryCount ?: 0,
                studentAnswer = answer?.let(AssignmentStudentAnswerResponse::from),
                studentSolutionFiles = studentSolutionFiles.map(AssignmentSolutionFileResponse::from),
                teacherSolutionFiles = teacherSolutionFiles.map(AssignmentTeacherSolutionFileResponse::from),
            )
    }
}

data class AssignmentStudentAnswerResponse(
    val selectedChoiceNumbers: List<Int>,
    val numericAnswer: BigDecimal?,
    val unknown: Boolean,
) {
    companion object {
        fun from(answer: SubmissionAnswer): AssignmentStudentAnswerResponse =
            AssignmentStudentAnswerResponse(
                selectedChoiceNumbers = answer.selectedChoiceNumbers.orEmpty().map { it.toInt() },
                numericAnswer = answer.numericAnswer,
                unknown = answer.unknown,
            )
    }
}

data class AssignmentSolutionFileResponse(
    val fileAssetId: UUID,
    val uploadedAt: Instant,
) {
    companion object {
        fun from(file: SubmissionSolutionFile): AssignmentSolutionFileResponse =
            AssignmentSolutionFileResponse(
                fileAssetId = file.fileAssetId,
                uploadedAt = file.createdAt,
            )
    }
}

data class AssignmentTeacherSolutionFileResponse(
    val explanationId: UUID,
    val fileAssetId: UUID,
    val visibleToStudent: Boolean,
) {
    companion object {
        fun from(explanation: ProblemExplanation): AssignmentTeacherSolutionFileResponse =
            AssignmentTeacherSolutionFileResponse(
                explanationId = explanation.id!!,
                fileAssetId = explanation.fileAssetId!!,
                visibleToStudent = explanation.visibleToStudent,
            )
    }
}

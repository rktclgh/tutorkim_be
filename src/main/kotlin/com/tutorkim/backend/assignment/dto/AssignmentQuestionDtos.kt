package com.tutorkim.backend.assignment.dto

import com.tutorkim.backend.assignment.entity.AssignmentProblem
import com.tutorkim.backend.assignment.entity.AssignmentProblemQuestion
import com.tutorkim.backend.student.entity.StudentProfile
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateAssignmentQuestionRequest(
    @field:NotBlank
    @field:Size(max = 1000)
    val body: String,
)

data class AnswerAssignmentQuestionRequest(
    @field:NotNull
    val fileAssetId: UUID,

    @field:Size(max = 1000)
    val teacherResponse: String? = null,

    val visibleToStudent: Boolean = true,

    val persistToProblemBank: Boolean = true,
)

data class AssignmentQuestionResponse(
    val id: UUID,
    val assignmentId: UUID,
    val assignmentProblemId: UUID,
    val problemId: UUID,
    val student: AssignmentStudentResponse,
    val body: String?,
    val teacherResponse: String?,
    val resolved: Boolean,
    val problemExplanationId: UUID?,
    val createdAt: Instant,
    val resolvedAt: Instant?,
) {
    companion object {
        fun from(
            question: AssignmentProblemQuestion,
            assignmentProblem: AssignmentProblem,
            student: StudentProfile,
        ): AssignmentQuestionResponse =
            AssignmentQuestionResponse(
                id = question.id!!,
                assignmentId = question.assignmentId,
                assignmentProblemId = question.assignmentProblemId,
                problemId = assignmentProblem.problemId,
                student = AssignmentStudentResponse.from(student),
                body = question.body,
                teacherResponse = question.teacherResponse,
                resolved = question.resolvedAt != null,
                problemExplanationId = question.persistedProblemExplanationId,
                createdAt = question.createdAt,
                resolvedAt = question.resolvedAt,
            )
    }
}

data class AssignmentQuestionAnswerResponse(
    val questionId: UUID,
    val problemId: UUID,
    val problemExplanationId: UUID,
    val fileAssetId: UUID,
    val visibleToStudent: Boolean,
    val persistedToProblemBank: Boolean,
)

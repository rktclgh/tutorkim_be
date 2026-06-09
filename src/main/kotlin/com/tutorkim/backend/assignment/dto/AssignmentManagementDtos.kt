package com.tutorkim.backend.assignment.dto

import com.tutorkim.backend.assignment.entity.Assignment
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.entity.AssignmentType
import com.tutorkim.backend.assignment.entity.ResultVisibility
import com.tutorkim.backend.assignment.entity.SubmissionStatus
import com.tutorkim.backend.student.entity.StudentProfile
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateAssignmentRequest(
    @field:NotNull
    val studentId: UUID,

    val lessonSessionId: UUID? = null,

    @field:NotNull
    val subjectId: UUID,

    @field:NotBlank
    @field:Size(max = 150)
    val title: String,

    @field:NotNull
    val assignmentType: AssignmentType,

    @field:NotNull
    val resultVisibility: ResultVisibility = ResultVisibility.HIDDEN_UNTIL_RELEASED,

    val dueAt: Instant? = null,

    @field:NotEmpty
    @field:Size(max = 100)
    val problemIds: List<UUID>,
)

data class ManualGradeSubmissionAnswerRequest(
    @field:NotNull
    val isCorrect: Boolean?,

    @field:Size(max = 1000)
    val reason: String? = null,
)

data class AssignmentStudentResponse(
    val id: UUID,
    val name: String,
) {
    companion object {
        fun from(student: StudentProfile): AssignmentStudentResponse =
            AssignmentStudentResponse(
                id = student.id!!,
                name = student.name,
            )
    }
}

data class AssignmentSummaryResponse(
    val id: UUID,
    val title: String,
    val assignmentType: AssignmentType,
    val student: AssignmentStudentResponse,
    val problemCount: Int,
    val questionCount: Int,
    val dueAt: Instant?,
    val expired: Boolean,
    val canSolve: Boolean,
    val status: AssignmentStatus,
    val submissionStatus: SubmissionStatus,
) {
    companion object {
        fun from(
            assignment: Assignment,
            student: StudentProfile,
            problemCount: Int,
            questionCount: Int,
            expired: Boolean,
            canSolve: Boolean,
            submissionStatus: SubmissionStatus,
        ): AssignmentSummaryResponse =
            AssignmentSummaryResponse(
                id = assignment.id!!,
                title = assignment.title,
                assignmentType = assignment.assignmentType,
                student = AssignmentStudentResponse.from(student),
                problemCount = problemCount,
                questionCount = questionCount,
                dueAt = assignment.dueAt,
                expired = expired,
                canSolve = canSolve,
                status = assignment.status,
                submissionStatus = submissionStatus,
            )
    }
}

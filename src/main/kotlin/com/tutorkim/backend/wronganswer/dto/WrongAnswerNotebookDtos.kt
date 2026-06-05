package com.tutorkim.backend.wronganswer.dto

import com.tutorkim.backend.assignment.entity.SubmissionStatus
import com.tutorkim.backend.assignment.entity.ProblemAttemptStatus
import com.tutorkim.backend.wronganswer.entity.WrongAnswerNotebook
import com.tutorkim.backend.wronganswer.entity.WrongAnswerNotebookStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateWrongAnswerNotebookRequest(
    @field:NotBlank
    @field:Size(max = 150)
    val title: String,

    @field:NotNull
    val subjectId: UUID,

    val dueAt: Instant? = null,

    @field:NotEmpty
    @field:Size(max = 100)
    @field:Valid
    val problemRefs: List<WrongAnswerProblemRefRequest>,
)

data class WrongAnswerProblemRefRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val uniqueProblemId: String,

    @field:NotNull
    val sourceAssignmentProblemId: UUID,
)

data class WrongAnswerNotebookSourcesResponse(
    val previousNotebooks: List<WrongAnswerNotebookSourceItemResponse>,
    val assignments: List<WrongAnswerNotebookSourceItemResponse>,
)

data class WrongAnswerNotebookSourceItemResponse(
    val uniqueProblemId: String,
    val assignmentProblemId: UUID,
    val sourceType: WrongAnswerNotebookSourceType,
    val attemptStatus: ProblemAttemptStatus,
    val retryCount: Int,
    val selected: Boolean,
)

enum class WrongAnswerNotebookSourceType {
    PREVIOUS_NOTEBOOK,
    ASSIGNMENT,
}

data class WrongAnswerNotebookResponse(
    val id: UUID,
    val assignmentId: UUID?,
    val title: String,
    val sourceSummary: String?,
    val subjectId: UUID,
    val status: WrongAnswerNotebookStatus,
    val problemCount: Int,
    val dueAt: Instant?,
    val publishedAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun from(
            notebook: WrongAnswerNotebook,
            problemCount: Int,
        ): WrongAnswerNotebookResponse =
            WrongAnswerNotebookResponse(
                id = notebook.id!!,
                assignmentId = notebook.assignmentId,
                title = notebook.title,
                sourceSummary = notebook.sourceSummary,
                subjectId = notebook.subjectId,
                status = notebook.status,
                problemCount = problemCount,
                dueAt = notebook.dueAt,
                publishedAt = notebook.publishedAt,
                createdAt = notebook.createdAt,
            )
    }
}

data class WrongAnswerNotebookListResponse(
    val id: UUID,
    val assignmentId: UUID?,
    val title: String,
    val sourceSummary: String?,
    val problemCount: Int,
    val remainingProblemCount: Int,
    val dueAt: Instant?,
    val expired: Boolean,
    val canSolve: Boolean,
    val submissionStatus: SubmissionStatus,
) {
    companion object {
        fun from(
            notebook: WrongAnswerNotebook,
            problemCount: Int,
            answeredCount: Int,
            submissionStatus: SubmissionStatus,
            now: Instant,
        ): WrongAnswerNotebookListResponse {
            val expired = notebook.dueAt?.isBefore(now) ?: false
            val remainingProblemCount = (problemCount - answeredCount).coerceAtLeast(0)
            return WrongAnswerNotebookListResponse(
                id = notebook.id!!,
                assignmentId = notebook.assignmentId,
                title = notebook.title,
                sourceSummary = notebook.sourceSummary,
                problemCount = problemCount,
                remainingProblemCount = remainingProblemCount,
                dueAt = notebook.dueAt,
                expired = expired,
                canSolve = notebook.status == WrongAnswerNotebookStatus.PUBLISHED &&
                    !expired &&
                    remainingProblemCount > 0 &&
                    submissionStatus != SubmissionStatus.SUBMITTED &&
                    submissionStatus != SubmissionStatus.LATE_SUBMITTED,
                submissionStatus = submissionStatus,
            )
        }
    }
}

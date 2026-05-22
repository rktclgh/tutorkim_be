package com.tutorkim.backend.lesson.dto

import com.tutorkim.backend.lesson.entity.AssignmentPerformance
import com.tutorkim.backend.lesson.entity.FocusLevel
import com.tutorkim.backend.lesson.entity.LessonSession
import com.tutorkim.backend.lesson.entity.LessonStatus
import com.tutorkim.backend.lesson.entity.UnderstandingLevel
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CompleteLessonSessionRequest(
    val currentCurriculumNodeId: UUID? = null,
    val previousProgress: String? = null,
    @field:NotBlank
    val currentProgress: String?,
    val nextProgress: String? = null,
    @field:NotNull
    val focusLevel: FocusLevel?,
    @field:NotNull
    val understandingLevel: UnderstandingLevel?,
    @field:NotNull
    val assignmentPerformance: AssignmentPerformance?,
    val lessonMemo: String? = null,
)

data class LessonSessionDetailResponse(
    val id: UUID,
    val studentId: UUID,
    val subjectId: UUID,
    val scheduledStartAt: Instant,
    val scheduledEndAt: Instant?,
    val actualStartAt: Instant?,
    val actualEndAt: Instant?,
    val status: LessonStatus,
    val currentCurriculumNodeId: UUID?,
    val previousProgress: String?,
    val currentProgress: String?,
    val nextProgress: String?,
    val focusLevel: FocusLevel?,
    val understandingLevel: UnderstandingLevel?,
    val assignmentPerformance: AssignmentPerformance?,
    val lessonMemo: String?,
) {
    companion object {
        fun from(
            session: LessonSession,
            studentId: UUID,
        ): LessonSessionDetailResponse =
            LessonSessionDetailResponse(
                id = session.id!!,
                studentId = studentId,
                subjectId = session.subjectId,
                scheduledStartAt = session.scheduledStartAt,
                scheduledEndAt = session.scheduledEndAt,
                actualStartAt = session.actualStartAt,
                actualEndAt = session.actualEndAt,
                status = session.status,
                currentCurriculumNodeId = session.currentCurriculumNodeId,
                previousProgress = session.previousProgressSummary,
                currentProgress = session.currentProgress,
                nextProgress = session.nextProgress,
                focusLevel = session.focusLevel,
                understandingLevel = session.understandingLevel,
                assignmentPerformance = session.assignmentPerformance,
                lessonMemo = session.lessonMemo,
            )
    }
}

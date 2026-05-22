package com.tutorkim.backend.lesson.dto

import com.tutorkim.backend.lesson.entity.LessonSchedule
import com.tutorkim.backend.lesson.entity.LessonSession
import com.tutorkim.backend.lesson.entity.LessonStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class CreateLessonScheduleRequest(
    val studentId: UUID,
    val subjectId: UUID,
    @field:Min(0)
    @field:Max(6)
    val dayOfWeek: Short,
    val startTime: LocalTime,
    val endTime: LocalTime,
    @field:NotBlank
    val timezone: String = "Asia/Seoul",
)

data class CreateLessonSessionRequest(
    val studentId: UUID,
    val subjectId: UUID,
    val scheduledStartAt: Instant,
    val scheduledEndAt: Instant,
)

data class LessonScheduleResponse(
    val id: UUID,
    val studentId: UUID,
    val subjectId: UUID,
    val dayOfWeek: Short?,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val timezone: String,
    val active: Boolean,
) {
    companion object {
        fun from(
            schedule: LessonSchedule,
            studentId: UUID,
        ): LessonScheduleResponse =
            LessonScheduleResponse(
                id = schedule.id!!,
                studentId = studentId,
                subjectId = schedule.subjectId,
                dayOfWeek = schedule.dayOfWeek,
                startTime = schedule.startTime,
                endTime = schedule.endTime,
                timezone = schedule.timezone,
                active = schedule.active,
            )
    }
}

data class LessonSessionResponse(
    val id: UUID,
    val studentId: UUID,
    val subjectId: UUID,
    val scheduledStartAt: Instant,
    val scheduledEndAt: Instant?,
    val status: LessonStatus,
) {
    companion object {
        fun from(
            session: LessonSession,
            studentId: UUID,
        ): LessonSessionResponse =
            LessonSessionResponse(
                id = session.id!!,
                studentId = studentId,
                subjectId = session.subjectId,
                scheduledStartAt = session.scheduledStartAt,
                scheduledEndAt = session.scheduledEndAt,
                status = session.status,
            )
    }
}

data class HomeTimetableDayResponse(
    val date: LocalDate,
    val lessons: List<LessonSessionResponse>,
)

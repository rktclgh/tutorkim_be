package com.tutorkim.backend.lesson.controller

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.common.web.API_PREFIX
import com.tutorkim.backend.lesson.dto.CreateLessonScheduleRequest
import com.tutorkim.backend.lesson.dto.CreateLessonSessionRequest
import com.tutorkim.backend.lesson.dto.HomeTimetableDayResponse
import com.tutorkim.backend.lesson.dto.LessonScheduleResponse
import com.tutorkim.backend.lesson.dto.LessonSessionResponse
import com.tutorkim.backend.lesson.service.LessonScheduleService
import com.tutorkim.backend.lesson.service.LessonSubjectNotFoundException
import com.tutorkim.backend.lesson.service.LessonTimeRangeException
import com.tutorkim.backend.lesson.service.LessonTimezoneException
import com.tutorkim.backend.lesson.service.LessonTimetableRangeException
import com.tutorkim.backend.student.service.TeacherStudentRelationshipNotFoundException
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping(API_PREFIX)
class LessonScheduleController(
    private val lessonScheduleService: LessonScheduleService,
) {
    @PostMapping("lesson-schedules")
    fun createLessonSchedule(
        authentication: Authentication,
        @Valid @RequestBody request: CreateLessonScheduleRequest,
    ): LessonScheduleResponse {
        val view = try {
            lessonScheduleService.createRecurringSchedule(
                teacherUserId = currentUserId(authentication),
                studentId = request.studentId,
                subjectId = request.subjectId,
                dayOfWeek = request.dayOfWeek,
                startTime = request.startTime,
                endTime = request.endTime,
                timezone = request.timezone,
            )
        } catch (exception: TeacherStudentRelationshipNotFoundException) {
            throw ApiException(ErrorCode.NOT_FOUND, "활성 학생 관계를 찾을 수 없습니다.", cause = exception)
        } catch (exception: LessonSubjectNotFoundException) {
            throw ApiException(ErrorCode.NOT_FOUND, "학생 과목을 찾을 수 없습니다.", cause = exception)
        } catch (exception: LessonTimeRangeException) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "종료 시간은 시작 시간 이후여야 합니다.", cause = exception)
        } catch (exception: LessonTimezoneException) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "시간대가 유효하지 않습니다.", cause = exception)
        }

        return LessonScheduleResponse.from(view.schedule, view.studentId)
    }

    @PostMapping("lesson-sessions")
    fun createLessonSession(
        authentication: Authentication,
        @Valid @RequestBody request: CreateLessonSessionRequest,
    ): LessonSessionResponse {
        val view = try {
            lessonScheduleService.createLessonSession(
                teacherUserId = currentUserId(authentication),
                studentId = request.studentId,
                subjectId = request.subjectId,
                scheduledStartAt = request.scheduledStartAt,
                scheduledEndAt = request.scheduledEndAt,
            )
        } catch (exception: TeacherStudentRelationshipNotFoundException) {
            throw ApiException(ErrorCode.NOT_FOUND, "활성 학생 관계를 찾을 수 없습니다.", cause = exception)
        } catch (exception: LessonSubjectNotFoundException) {
            throw ApiException(ErrorCode.NOT_FOUND, "학생 과목을 찾을 수 없습니다.", cause = exception)
        } catch (exception: LessonTimeRangeException) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "종료 시간은 시작 시간 이후여야 합니다.", cause = exception)
        }

        return LessonSessionResponse.from(view.session, view.studentId)
    }

    @GetMapping("home/timetable")
    fun getHomeTimetable(
        authentication: Authentication,
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
    ): List<HomeTimetableDayResponse> =
        try {
            lessonScheduleService.getHomeTimetable(
                teacherUserId = currentUserId(authentication),
                from = from,
                to = to,
            ).map { day ->
                HomeTimetableDayResponse(
                    date = day.date,
                    lessons = day.lessons.map { lesson ->
                        LessonSessionResponse.from(lesson.session, lesson.studentId)
                    },
                )
            }
        } catch (exception: LessonTimetableRangeException) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                if (to.isBefore(from)) {
                    "조회 종료일은 시작일 이후여야 합니다."
                } else {
                    "시간표 조회 범위는 최대 31일입니다."
                },
                cause = exception,
            )
        }

    private fun currentUserId(authentication: Authentication): UUID =
        try {
            UUID.fromString(authentication.name)
        } catch (exception: IllegalArgumentException) {
            throw ApiException(ErrorCode.UNAUTHORIZED, cause = exception)
        }
}

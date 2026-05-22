package com.tutorkim.backend.lesson.controller

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.common.web.API_PREFIX
import com.tutorkim.backend.lesson.dto.CompleteLessonSessionRequest
import com.tutorkim.backend.lesson.dto.LessonSessionDetailResponse
import com.tutorkim.backend.lesson.service.CompleteLessonSessionCommand
import com.tutorkim.backend.lesson.service.LessonCancelledException
import com.tutorkim.backend.lesson.service.LessonCurriculumNodeNotFoundException
import com.tutorkim.backend.lesson.service.LessonSessionNotFoundException
import com.tutorkim.backend.lesson.service.LessonSessionService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(API_PREFIX)
class LessonSessionController(
    private val lessonSessionService: LessonSessionService,
) {
    @GetMapping("lesson-sessions/{lessonSessionId}")
    fun getLessonDetail(
        authentication: Authentication,
        @PathVariable lessonSessionId: UUID,
    ): LessonSessionDetailResponse {
        val view = try {
            lessonSessionService.getLessonDetail(
                teacherUserId = currentUserId(authentication),
                lessonSessionId = lessonSessionId,
            )
        } catch (exception: LessonSessionNotFoundException) {
            throw ApiException(ErrorCode.NOT_FOUND, "수업 세션을 찾을 수 없습니다.", cause = exception)
        }

        return LessonSessionDetailResponse.from(view.session, view.studentId)
    }

    @PatchMapping("lesson-sessions/{lessonSessionId}/complete")
    fun completeLesson(
        authentication: Authentication,
        @PathVariable lessonSessionId: UUID,
        @Valid @RequestBody request: CompleteLessonSessionRequest,
    ): LessonSessionDetailResponse {
        val view = try {
            lessonSessionService.completeLesson(
                teacherUserId = currentUserId(authentication),
                lessonSessionId = lessonSessionId,
                command = CompleteLessonSessionCommand(
                    currentCurriculumNodeId = request.currentCurriculumNodeId,
                    previousProgress = request.previousProgress,
                    currentProgress = request.currentProgress!!,
                    nextProgress = request.nextProgress,
                    focusLevel = request.focusLevel!!,
                    understandingLevel = request.understandingLevel!!,
                    assignmentPerformance = request.assignmentPerformance!!,
                    lessonMemo = request.lessonMemo,
                ),
            )
        } catch (exception: LessonSessionNotFoundException) {
            throw ApiException(ErrorCode.NOT_FOUND, "수업 세션을 찾을 수 없습니다.", cause = exception)
        } catch (exception: LessonCurriculumNodeNotFoundException) {
            throw ApiException(ErrorCode.NOT_FOUND, "커리큘럼 노드를 찾을 수 없습니다.", cause = exception)
        } catch (exception: LessonCancelledException) {
            throw ApiException(ErrorCode.CONFLICT, "취소된 수업은 완료 처리할 수 없습니다.", cause = exception)
        }

        return LessonSessionDetailResponse.from(view.session, view.studentId)
    }

    private fun currentUserId(authentication: Authentication): UUID =
        try {
            UUID.fromString(authentication.name)
        } catch (exception: IllegalArgumentException) {
            throw ApiException(ErrorCode.UNAUTHORIZED, cause = exception)
        }
}

package com.tutorkim.backend.wronganswer.controller

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.common.web.API_PREFIX
import com.tutorkim.backend.wronganswer.dto.CreateWrongAnswerNotebookRequest
import com.tutorkim.backend.wronganswer.dto.WrongAnswerNotebookListResponse
import com.tutorkim.backend.wronganswer.dto.WrongAnswerNotebookResponse
import com.tutorkim.backend.wronganswer.dto.WrongAnswerNotebookSourcesResponse
import com.tutorkim.backend.wronganswer.dto.WrongAnswerReportResponse
import com.tutorkim.backend.wronganswer.service.WrongAnswerNotebookService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping(API_PREFIX)
class WrongAnswerNotebookController(
    private val wrongAnswerNotebookService: WrongAnswerNotebookService,
) {
    @PostMapping("/students/{studentId}/wrong-answer-notebooks")
    fun createDraft(
        authentication: Authentication,
        @PathVariable studentId: UUID,
        @Valid @RequestBody request: CreateWrongAnswerNotebookRequest,
    ): WrongAnswerNotebookResponse =
        wrongAnswerNotebookService.createDraft(
            teacherUserId = currentUserId(authentication),
            studentId = studentId,
            request = request,
        )

    @GetMapping("/students/{studentId}/wrong-answer-notebook-sources")
    fun getSources(
        authentication: Authentication,
        @PathVariable studentId: UUID,
        @RequestParam subjectId: UUID,
    ): WrongAnswerNotebookSourcesResponse =
        wrongAnswerNotebookService.getSources(
            teacherUserId = currentUserId(authentication),
            studentId = studentId,
            subjectId = subjectId,
        )

    @GetMapping("/students/{studentId}/wrong-answers")
    fun getWrongAnswers(
        authentication: Authentication,
        @PathVariable studentId: UUID,
        @RequestParam subjectId: UUID,
        @RequestParam(required = false) curriculumNodeId: UUID?,
        @RequestParam(defaultValue = "true") unresolvedOnly: Boolean,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): WrongAnswerReportResponse =
        wrongAnswerNotebookService.getWrongAnswers(
            teacherUserId = currentUserId(authentication),
            studentId = studentId,
            subjectId = subjectId,
            curriculumNodeId = curriculumNodeId,
            unresolvedOnly = unresolvedOnly,
            from = from,
            to = to,
        )

    @PostMapping("/wrong-answer-notebooks/{wrongAnswerNotebookId}/publish")
    fun publish(
        authentication: Authentication,
        @PathVariable wrongAnswerNotebookId: UUID,
    ): WrongAnswerNotebookResponse =
        wrongAnswerNotebookService.publish(
            teacherUserId = currentUserId(authentication),
            notebookId = wrongAnswerNotebookId,
        )

    @GetMapping("/students/{studentId}/wrong-answer-notebooks")
    fun listForTeacherStudent(
        authentication: Authentication,
        @PathVariable studentId: UUID,
        @RequestParam(defaultValue = "false") includeExpired: Boolean,
    ): List<WrongAnswerNotebookListResponse> =
        wrongAnswerNotebookService.listForTeacherStudent(
            teacherUserId = currentUserId(authentication),
            studentId = studentId,
            includeExpired = includeExpired,
        )

    @GetMapping("/student/wrong-answer-notebooks")
    fun listMine(
        authentication: Authentication,
        @RequestParam(defaultValue = "false") includeExpired: Boolean,
    ): List<WrongAnswerNotebookListResponse> =
        wrongAnswerNotebookService.listForStudent(
            studentUserId = currentUserId(authentication),
            includeExpired = includeExpired,
        )

    private fun currentUserId(authentication: Authentication): UUID =
        try {
            UUID.fromString(authentication.name)
        } catch (exception: IllegalArgumentException) {
            throw ApiException(ErrorCode.UNAUTHORIZED, cause = exception)
        }
}

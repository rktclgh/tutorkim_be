package com.tutorkim.backend.assignment.controller

import com.tutorkim.backend.assignment.dto.AssignmentDetailResponse
import com.tutorkim.backend.assignment.dto.AnswerAssignmentQuestionRequest
import com.tutorkim.backend.assignment.dto.AssignmentQuestionAnswerResponse
import com.tutorkim.backend.assignment.dto.AssignmentQuestionResponse
import com.tutorkim.backend.assignment.dto.AssignmentSummaryResponse
import com.tutorkim.backend.assignment.dto.CreateAssignmentRequest
import com.tutorkim.backend.assignment.dto.ManualGradeSubmissionAnswerRequest
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.entity.AssignmentType
import com.tutorkim.backend.assignment.service.AssignmentQuestionService
import com.tutorkim.backend.assignment.service.AssignmentManagementService
import com.tutorkim.backend.common.web.API_PREFIX
import com.tutorkim.backend.common.web.CurrentUser
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(API_PREFIX)
class AssignmentManagementController(
    private val assignmentManagementService: AssignmentManagementService,
    private val assignmentQuestionService: AssignmentQuestionService,
    private val currentUser: CurrentUser,
) {
    @PostMapping("/assignments")
    fun createAssignment(
        authentication: Authentication,
        @Valid @RequestBody request: CreateAssignmentRequest,
    ): AssignmentSummaryResponse =
        assignmentManagementService.createDraft(
            teacherUserId = currentUser.id(authentication),
            request = request,
        )

    @PostMapping("/assignments/{assignmentId}/publish")
    fun publishAssignment(
        authentication: Authentication,
        @PathVariable assignmentId: UUID,
    ): AssignmentSummaryResponse =
        assignmentManagementService.publish(
            teacherUserId = currentUser.id(authentication),
            assignmentId = assignmentId,
        )

    @PostMapping("/assignments/{assignmentId}/release-results")
    fun releaseAssignmentResults(
        authentication: Authentication,
        @PathVariable assignmentId: UUID,
    ): AssignmentDetailResponse =
        assignmentManagementService.releaseResults(
            teacherUserId = currentUser.id(authentication),
            assignmentId = assignmentId,
        )

    @PatchMapping("/submissions/{submissionId}/answers/{answerId}/grading")
    fun manuallyGradeSubmissionAnswer(
        authentication: Authentication,
        @PathVariable submissionId: UUID,
        @PathVariable answerId: UUID,
        @Valid @RequestBody request: ManualGradeSubmissionAnswerRequest,
    ): AssignmentDetailResponse =
        assignmentManagementService.manuallyGradeSubmissionAnswer(
            teacherUserId = currentUser.id(authentication),
            submissionId = submissionId,
            answerId = answerId,
            request = request,
        )

    @GetMapping("/assignments")
    fun listAssignments(
        authentication: Authentication,
        @RequestParam studentId: UUID?,
        @RequestParam type: AssignmentType?,
        @RequestParam status: AssignmentStatus?,
        @RequestParam(defaultValue = "100") limit: Int,
    ): List<AssignmentSummaryResponse> =
        assignmentManagementService.listAssignments(
            teacherUserId = currentUser.id(authentication),
            studentId = studentId,
            type = type,
            status = status,
            limit = limit,
        )

    @GetMapping("/assignments/{assignmentId}")
    fun getAssignmentDetail(
        authentication: Authentication,
        @PathVariable assignmentId: UUID,
    ): AssignmentDetailResponse =
        assignmentManagementService.getDetail(
            teacherUserId = currentUser.id(authentication),
            assignmentId = assignmentId,
        )

    @GetMapping("/assignments/{assignmentId}/questions")
    fun listAssignmentQuestions(
        authentication: Authentication,
        @PathVariable assignmentId: UUID,
        @RequestParam(defaultValue = "false") unresolvedOnly: Boolean,
    ): List<AssignmentQuestionResponse> =
        assignmentQuestionService.listAssignmentQuestions(
            teacherUserId = currentUser.id(authentication),
            assignmentId = assignmentId,
            unresolvedOnly = unresolvedOnly,
        )

    @PostMapping("/assignments/{assignmentId}/questions/{questionId}/teacher-solution-files")
    fun answerQuestionWithTeacherSolutionFile(
        authentication: Authentication,
        @PathVariable assignmentId: UUID,
        @PathVariable questionId: UUID,
        @Valid @RequestBody request: AnswerAssignmentQuestionRequest,
    ): AssignmentQuestionAnswerResponse =
        assignmentQuestionService.answerQuestionWithTeacherSolutionFile(
            teacherUserId = currentUser.id(authentication),
            assignmentId = assignmentId,
            questionId = questionId,
            request = request,
        )

}

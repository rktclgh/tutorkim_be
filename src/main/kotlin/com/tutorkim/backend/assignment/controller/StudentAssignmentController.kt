package com.tutorkim.backend.assignment.controller

import com.tutorkim.backend.assignment.dto.AttachStudentSolutionFileRequest
import com.tutorkim.backend.assignment.dto.AssignmentQuestionResponse
import com.tutorkim.backend.assignment.dto.CreateAssignmentQuestionRequest
import com.tutorkim.backend.assignment.dto.StudentAssignmentDetailResponse
import com.tutorkim.backend.assignment.dto.StudentAssignmentSummaryResponse
import com.tutorkim.backend.assignment.dto.SaveStudentAnswerRequest
import com.tutorkim.backend.assignment.dto.SubmitStudentAnswersRequest
import com.tutorkim.backend.assignment.service.AssignmentQuestionService
import com.tutorkim.backend.assignment.service.StudentAssignmentService
import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.common.web.API_PREFIX
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
class StudentAssignmentController(
    private val studentAssignmentService: StudentAssignmentService,
    private val assignmentQuestionService: AssignmentQuestionService,
) {
    @GetMapping("/student/assignments")
    fun listMyAssignments(
        authentication: Authentication,
        @RequestParam(defaultValue = "false") includeExpired: Boolean,
    ): List<StudentAssignmentSummaryResponse> =
        studentAssignmentService.listMyAssignments(
            studentUserId = currentUserId(authentication),
            includeExpired = includeExpired,
        )

    @GetMapping("/student/assignments/{assignmentId}")
    fun getMyAssignmentDetail(
        authentication: Authentication,
        @PathVariable assignmentId: UUID,
    ): StudentAssignmentDetailResponse =
        studentAssignmentService.getMyAssignmentDetail(
            studentUserId = currentUserId(authentication),
            assignmentId = assignmentId,
        )

    @PatchMapping("/student/assignments/{assignmentId}/answers/{assignmentProblemId}")
    fun saveMyAnswer(
        authentication: Authentication,
        @PathVariable assignmentId: UUID,
        @PathVariable assignmentProblemId: UUID,
        @Valid @RequestBody request: SaveStudentAnswerRequest,
    ): StudentAssignmentDetailResponse =
        studentAssignmentService.saveMyAnswer(
            studentUserId = currentUserId(authentication),
            assignmentId = assignmentId,
            assignmentProblemId = assignmentProblemId,
            request = request,
        )

    @PostMapping("/student/assignments/{assignmentId}/answers/{assignmentProblemId}/solution-files")
    fun attachMySolutionFile(
        authentication: Authentication,
        @PathVariable assignmentId: UUID,
        @PathVariable assignmentProblemId: UUID,
        @Valid @RequestBody request: AttachStudentSolutionFileRequest,
    ): StudentAssignmentDetailResponse =
        studentAssignmentService.attachMySolutionFile(
            studentUserId = currentUserId(authentication),
            assignmentId = assignmentId,
            assignmentProblemId = assignmentProblemId,
            request = request,
        )

    @PostMapping("/student/assignments/{assignmentId}/submit")
    fun submitMyAssignment(
        authentication: Authentication,
        @PathVariable assignmentId: UUID,
    ): StudentAssignmentDetailResponse =
        studentAssignmentService.submitMyAssignment(
            studentUserId = currentUserId(authentication),
            assignmentId = assignmentId,
        )

    @PostMapping("/student/assignments/{assignmentId}/submissions")
    fun submitMyAnswers(
        authentication: Authentication,
        @PathVariable assignmentId: UUID,
        @Valid @RequestBody request: SubmitStudentAnswersRequest,
    ): StudentAssignmentDetailResponse =
        studentAssignmentService.submitMyAnswers(
            studentUserId = currentUserId(authentication),
            assignmentId = assignmentId,
            request = request,
        )

    @PostMapping("/student/assignments/{assignmentId}/problems/{assignmentProblemId}/questions")
    fun createQuestion(
        authentication: Authentication,
        @PathVariable assignmentId: UUID,
        @PathVariable assignmentProblemId: UUID,
        @Valid @RequestBody request: CreateAssignmentQuestionRequest,
    ): AssignmentQuestionResponse =
        assignmentQuestionService.createStudentQuestion(
            studentUserId = currentUserId(authentication),
            assignmentId = assignmentId,
            assignmentProblemId = assignmentProblemId,
            request = request,
        )

    private fun currentUserId(authentication: Authentication): UUID =
        try {
            UUID.fromString(authentication.name)
        } catch (exception: IllegalArgumentException) {
            throw ApiException(ErrorCode.UNAUTHORIZED, cause = exception)
        }
}

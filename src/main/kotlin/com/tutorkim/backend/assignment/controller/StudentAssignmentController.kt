package com.tutorkim.backend.assignment.controller

import com.tutorkim.backend.assignment.dto.StudentAssignmentDetailResponse
import com.tutorkim.backend.assignment.dto.StudentAssignmentSummaryResponse
import com.tutorkim.backend.assignment.service.StudentAssignmentService
import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.common.web.API_PREFIX
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(API_PREFIX)
class StudentAssignmentController(
    private val studentAssignmentService: StudentAssignmentService,
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

    private fun currentUserId(authentication: Authentication): UUID =
        try {
            UUID.fromString(authentication.name)
        } catch (exception: IllegalArgumentException) {
            throw ApiException(ErrorCode.UNAUTHORIZED, cause = exception)
        }
}

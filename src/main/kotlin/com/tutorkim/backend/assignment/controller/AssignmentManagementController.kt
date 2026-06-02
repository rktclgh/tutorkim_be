package com.tutorkim.backend.assignment.controller

import com.tutorkim.backend.assignment.dto.AssignmentDetailResponse
import com.tutorkim.backend.assignment.dto.AssignmentSummaryResponse
import com.tutorkim.backend.assignment.dto.CreateAssignmentRequest
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.entity.AssignmentType
import com.tutorkim.backend.assignment.service.AssignmentManagementService
import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.common.web.API_PREFIX
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
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
) {
    @PostMapping("/assignments")
    fun createAssignment(
        authentication: Authentication,
        @Valid @RequestBody request: CreateAssignmentRequest,
    ): AssignmentSummaryResponse =
        assignmentManagementService.createDraft(
            teacherUserId = currentUserId(authentication),
            request = request,
        )

    @PostMapping("/assignments/{assignmentId}/publish")
    fun publishAssignment(
        authentication: Authentication,
        @PathVariable assignmentId: UUID,
    ): AssignmentSummaryResponse =
        assignmentManagementService.publish(
            teacherUserId = currentUserId(authentication),
            assignmentId = assignmentId,
        )

    @PostMapping("/assignments/{assignmentId}/release-results")
    fun releaseAssignmentResults(
        authentication: Authentication,
        @PathVariable assignmentId: UUID,
    ): AssignmentDetailResponse =
        assignmentManagementService.releaseResults(
            teacherUserId = currentUserId(authentication),
            assignmentId = assignmentId,
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
            teacherUserId = currentUserId(authentication),
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
            teacherUserId = currentUserId(authentication),
            assignmentId = assignmentId,
        )

    private fun currentUserId(authentication: Authentication): UUID =
        try {
            UUID.fromString(authentication.name)
        } catch (exception: IllegalArgumentException) {
            throw ApiException(ErrorCode.UNAUTHORIZED, cause = exception)
        }
}

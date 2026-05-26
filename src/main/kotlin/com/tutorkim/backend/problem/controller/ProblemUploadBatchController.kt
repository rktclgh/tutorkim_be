package com.tutorkim.backend.problem.controller

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.common.web.API_PREFIX
import com.tutorkim.backend.problem.dto.AttachProblemUploadFileRequest
import com.tutorkim.backend.problem.dto.CreateProblemUploadBatchRequest
import com.tutorkim.backend.problem.dto.ProblemUploadBatchResponse
import com.tutorkim.backend.problem.dto.ProblemUploadFileResponse
import com.tutorkim.backend.problem.dto.RetryProblemUploadBatchRequest
import com.tutorkim.backend.problem.dto.StartProblemParsingRequest
import com.tutorkim.backend.problem.service.ProblemUploadBatchService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(API_PREFIX)
class ProblemUploadBatchController(
    private val problemUploadBatchService: ProblemUploadBatchService,
) {
    @PostMapping("/problem-upload-batches")
    fun createBatch(
        authentication: Authentication,
        @Valid @RequestBody request: CreateProblemUploadBatchRequest,
    ): ProblemUploadBatchResponse =
        problemUploadBatchService.createBatch(
            teacherUserId = currentUserId(authentication),
            request = request,
        )

    @PostMapping("/problem-upload-batches/{batchId}/files")
    fun attachFile(
        authentication: Authentication,
        @PathVariable batchId: UUID,
        @Valid @RequestBody request: AttachProblemUploadFileRequest,
    ): ProblemUploadFileResponse =
        problemUploadBatchService.attachFile(
            teacherUserId = currentUserId(authentication),
            batchId = batchId,
            request = request,
        )

    @PostMapping("/problem-upload-batches/{batchId}/parse")
    fun startParsing(
        authentication: Authentication,
        @PathVariable batchId: UUID,
        @Valid @RequestBody request: StartProblemParsingRequest,
    ): ProblemUploadBatchResponse =
        problemUploadBatchService.startParsing(
            teacherUserId = currentUserId(authentication),
            batchId = batchId,
            request = request,
        )

    @PostMapping("/problem-upload-batches/{batchId}/retry")
    fun retryParsing(
        authentication: Authentication,
        @PathVariable batchId: UUID,
        @Valid @RequestBody request: RetryProblemUploadBatchRequest,
    ): ProblemUploadBatchResponse =
        problemUploadBatchService.retryParsing(
            teacherUserId = currentUserId(authentication),
            batchId = batchId,
            request = request,
        )

    @GetMapping("/problem-upload-batches/{batchId}")
    fun getBatch(
        authentication: Authentication,
        @PathVariable batchId: UUID,
    ): ProblemUploadBatchResponse =
        problemUploadBatchService.getBatch(
            teacherUserId = currentUserId(authentication),
            batchId = batchId,
        )

    private fun currentUserId(authentication: Authentication): UUID =
        try {
            UUID.fromString(authentication.name)
        } catch (exception: IllegalArgumentException) {
            throw ApiException(ErrorCode.UNAUTHORIZED, cause = exception)
        }
}

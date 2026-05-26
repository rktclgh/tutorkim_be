package com.tutorkim.backend.file.controller

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.common.web.API_PREFIX
import com.tutorkim.backend.file.dto.FileUploadUrlRequest
import com.tutorkim.backend.file.dto.FileUploadUrlResponse
import com.tutorkim.backend.file.service.FileAssetService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(API_PREFIX)
class FileAssetController(
    private val fileAssetService: FileAssetService,
) {
    @PostMapping("/files/upload-url")
    fun requestUploadUrl(
        authentication: Authentication,
        @Valid @RequestBody request: FileUploadUrlRequest,
    ): FileUploadUrlResponse =
        fileAssetService.createUploadReservation(
            ownerUserId = currentUserId(authentication),
            request = request,
        )

    private fun currentUserId(authentication: Authentication): UUID =
        try {
            UUID.fromString(authentication.name)
        } catch (exception: IllegalArgumentException) {
            throw ApiException(ErrorCode.UNAUTHORIZED, cause = exception)
        }
}

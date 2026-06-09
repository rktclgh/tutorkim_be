package com.tutorkim.backend.file.controller

import com.tutorkim.backend.common.web.API_PREFIX
import com.tutorkim.backend.common.web.CurrentUser
import com.tutorkim.backend.file.dto.FileUploadUrlRequest
import com.tutorkim.backend.file.dto.FileUploadUrlResponse
import com.tutorkim.backend.file.service.FileAssetService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(API_PREFIX)
class FileAssetController(
    private val fileAssetService: FileAssetService,
    private val currentUser: CurrentUser,
) {
    @PostMapping("/files/upload-url")
    fun requestUploadUrl(
        authentication: Authentication,
        @Valid @RequestBody request: FileUploadUrlRequest,
    ): FileUploadUrlResponse =
        fileAssetService.createUploadReservation(
            ownerUserId = currentUser.id(authentication),
            request = request,
        )
}

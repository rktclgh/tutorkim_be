package com.tutorkim.backend.file.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class FileUploadUrlRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val filename: String,

    @field:NotBlank
    @field:Size(max = 100)
    val contentType: String,

    @field:Min(1)
    val sizeBytes: Long,
)

data class FileUploadUrlResponse(
    val fileAssetId: UUID,
    val storageKey: String,
    val filename: String,
    val contentType: String,
    val sizeBytes: Long,
)

package com.tutorkim.backend.file.service

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.file.dto.FileUploadUrlRequest
import com.tutorkim.backend.file.dto.FileUploadUrlResponse
import com.tutorkim.backend.file.entity.FileAsset
import com.tutorkim.backend.file.repository.FileAssetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FileAssetService(
    private val fileAssetRepository: FileAssetRepository,
) {
    private val maxStorageFilenameLength = 120
    private val allowedContentTypes = setOf(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/webp",
    )
    private val maxSizeBytes = 50L * 1024L * 1024L

    @Transactional
    fun createUploadReservation(
        ownerUserId: UUID,
        request: FileUploadUrlRequest,
    ): FileUploadUrlResponse {
        validateUploadRequest(request)
        val storageKey = buildStorageKey(ownerUserId, request.filename)
        val fileAsset = fileAssetRepository.save(
            FileAsset(
                ownerUserId = ownerUserId,
                storageKey = storageKey,
                originalFilename = request.filename,
                contentType = request.contentType,
                sizeBytes = request.sizeBytes,
            ),
        )

        return FileUploadUrlResponse(
            fileAssetId = fileAsset.id!!,
            storageKey = fileAsset.storageKey,
            filename = request.filename,
            contentType = request.contentType,
            sizeBytes = request.sizeBytes,
        )
    }

    private fun validateUploadRequest(request: FileUploadUrlRequest) {
        if (request.contentType.lowercase() !in allowedContentTypes) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 파일 형식입니다.")
        }
        if (request.sizeBytes > maxSizeBytes) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "업로드 파일은 50MB를 초과할 수 없습니다.")
        }
    }

    private fun buildStorageKey(
        ownerUserId: UUID,
        filename: String,
    ): String {
        val safeFilename = filename
            .trim()
            .replace(Regex("""[^\w.\-가-힣]+"""), "_")
            .let { if (it.length > maxStorageFilenameLength) it.take(maxStorageFilenameLength) else it }
            .ifBlank { "upload" }
        return "problem-upload/$ownerUserId/${UUID.randomUUID()}-$safeFilename"
    }
}

package com.tutorkim.backend.problem.service

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.file.repository.FileAssetRepository
import com.tutorkim.backend.problem.dto.AttachProblemUploadFileRequest
import com.tutorkim.backend.problem.dto.CreateProblemUploadBatchRequest
import com.tutorkim.backend.problem.dto.DocumentIngestionStageRunResponse
import com.tutorkim.backend.problem.dto.ProblemUploadBatchResponse
import com.tutorkim.backend.problem.dto.ProblemUploadFileResponse
import com.tutorkim.backend.problem.dto.RetryProblemUploadBatchRequest
import com.tutorkim.backend.problem.dto.StartProblemParsingRequest
import com.tutorkim.backend.problem.entity.DocumentIngestionStageRun
import com.tutorkim.backend.problem.entity.IngestionStageType
import com.tutorkim.backend.problem.entity.ParseStatus
import com.tutorkim.backend.problem.entity.ProblemUploadBatch
import com.tutorkim.backend.problem.entity.ProblemUploadFile
import com.tutorkim.backend.problem.repository.DocumentIngestionStageRunRepository
import com.tutorkim.backend.problem.repository.ProblemUploadBatchRepository
import com.tutorkim.backend.problem.repository.ProblemUploadFileRepository
import com.tutorkim.backend.student.repository.TeacherProfileRepository
import com.tutorkim.backend.subject.repository.SubjectRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val SEMANTIC_FIRST_PARSE_MODE = "SEMANTIC_FIRST"

@Service
class ProblemUploadBatchService(
    private val teacherProfileRepository: TeacherProfileRepository,
    private val subjectRepository: SubjectRepository,
    private val fileAssetRepository: FileAssetRepository,
    private val uploadBatchRepository: ProblemUploadBatchRepository,
    private val uploadFileRepository: ProblemUploadFileRepository,
    private val stageRunRepository: DocumentIngestionStageRunRepository,
) {
    @Transactional
    fun createBatch(
        teacherUserId: UUID,
        request: CreateProblemUploadBatchRequest,
    ): ProblemUploadBatchResponse {
        val teacher = findTeacherProfile(teacherUserId)
        val batch = uploadBatchRepository.save(
            ProblemUploadBatch(
                teacherId = teacher.id!!,
                title = request.title?.trim()?.ifBlank { null },
                sourceType = request.sourceType,
            ),
        )

        return toResponse(batch)
    }

    @Transactional
    fun attachFile(
        teacherUserId: UUID,
        batchId: UUID,
        request: AttachProblemUploadFileRequest,
    ): ProblemUploadFileResponse {
        val teacher = findTeacherProfile(teacherUserId)
        val batch = findOwnedBatchForUpdate(batchId, teacher.id!!)
        if (batch.parseStatus != ParseStatus.PENDING) {
            throw ApiException(ErrorCode.CONFLICT, "현재 배치 상태에서는 파일을 첨부할 수 없습니다.")
        }
        fileAssetRepository.findByIdAndOwnerUserId(request.fileAssetId, teacherUserId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "업로드 파일을 찾을 수 없습니다.")

        if (uploadFileRepository.existsByBatchIdAndFileAssetId(batch.id!!, request.fileAssetId)) {
            throw ApiException(ErrorCode.CONFLICT, "이미 배치에 첨부된 파일입니다.")
        }

        val uploadFile = try {
            uploadFileRepository.saveAndFlush(
                ProblemUploadFile(
                    batchId = batch.id!!,
                    fileAssetId = request.fileAssetId,
                    sourceType = request.sourceType,
                    pageNumber = request.pageNumber,
                ),
            )
        } catch (exception: DataIntegrityViolationException) {
            throw ApiException(ErrorCode.CONFLICT, "이미 배치에 첨부된 파일입니다.", cause = exception)
        }

        return toFileResponse(uploadFile)
    }

    @Transactional
    fun startParsing(
        teacherUserId: UUID,
        batchId: UUID,
        request: StartProblemParsingRequest,
    ): ProblemUploadBatchResponse {
        val teacher = findTeacherProfile(teacherUserId)
        val batch = findOwnedBatchForUpdate(batchId, teacher.id!!)
        validateParseRequest(batch, request)

        val uploadFiles = uploadFileRepository.findByBatchIdOrderByCreatedAtAsc(batch.id!!)
        if (uploadFiles.isEmpty()) {
            throw ApiException(ErrorCode.CONFLICT, "파싱을 시작하려면 업로드 파일이 필요합니다.")
        }

        if (subjectRepository.findByIdAndActiveTrue(request.subjectId) == null) {
            throw ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없습니다.")
        }

        batch.parseStatus = ParseStatus.PROCESSING
        batch.pipelineVersion = request.pipelineVersion
        batch.parseModel = request.hermesReview?.model
        batch.parseError = null
        val savedBatch = uploadBatchRepository.save(batch)
        val stageRuns = request.deterministicStages.map { stageType ->
            DocumentIngestionStageRun(
                batchId = savedBatch.id!!,
                stageType = stageType,
                engineName = engineName(stageType, request),
                outputJson = mapOf(
                    "parseMode" to request.parseMode,
                    "subjectId" to request.subjectId.toString(),
                ),
            )
        }
        val savedStageRuns = stageRunRepository.saveAll(stageRuns)

        return toResponse(
            batch = savedBatch,
            files = uploadFiles,
            stageRuns = savedStageRuns,
        )
    }

    @Transactional
    fun retryParsing(
        teacherUserId: UUID,
        batchId: UUID,
        request: RetryProblemUploadBatchRequest,
    ): ProblemUploadBatchResponse {
        val teacher = findTeacherProfile(teacherUserId)
        val batch = findOwnedBatchForUpdate(batchId, teacher.id!!)
        validateRetryRequest(batch, request)

        val uploadFiles = uploadFileRepository.findByBatchIdOrderByCreatedAtAsc(batch.id!!)
        if (uploadFiles.isEmpty()) {
            throw ApiException(ErrorCode.CONFLICT, "재파싱을 시작하려면 업로드 파일이 필요합니다.")
        }

        val retriedBatch = ProblemUploadBatchRetryPolicy.retry(
            batch = batch,
            hasNarrowReviewScope = hasNarrowReviewScope(request),
        )
        val savedBatch = uploadBatchRepository.save(retriedBatch)
        val retryStageRuns = request.targetStages.map { stageType ->
            DocumentIngestionStageRun(
                batchId = savedBatch.id!!,
                stageType = stageType,
                engineName = retryEngineName(stageType, savedBatch),
                outputJson = mapOf(
                    "retry" to true,
                    "retryCount" to savedBatch.retryCount,
                    "temporaryProblemIds" to request.temporaryProblemIds,
                    "retryOnlyLowConfidenceItems" to request.retryOnlyLowConfidenceItems,
                ),
            )
        }
        stageRunRepository.saveAll(retryStageRuns)

        return toResponse(
            batch = savedBatch,
            files = uploadFiles,
            stageRuns = stageRunRepository.findByBatchIdOrderByCreatedAtAsc(savedBatch.id!!),
        )
    }

    @Transactional(readOnly = true)
    fun getBatch(
        teacherUserId: UUID,
        batchId: UUID,
    ): ProblemUploadBatchResponse {
        val teacher = findTeacherProfile(teacherUserId)
        val batch = findOwnedBatch(batchId, teacher.id!!)
        return toResponse(
            batch = batch,
            files = uploadFileRepository.findByBatchIdOrderByCreatedAtAsc(batch.id!!),
            stageRuns = stageRunRepository.findByBatchIdOrderByCreatedAtAsc(batch.id!!),
        )
    }

    private fun validateParseRequest(
        batch: ProblemUploadBatch,
        request: StartProblemParsingRequest,
    ) {
        if (request.parseMode != SEMANTIC_FIRST_PARSE_MODE) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 파싱 모드입니다.")
        }
        if (batch.parseStatus != ParseStatus.PENDING) {
            throw ApiException(ErrorCode.CONFLICT, "현재 배치 상태에서는 파싱을 시작할 수 없습니다.")
        }
        val hasHermesStage = request.deterministicStages.any { it.name.startsWith("HERMES_") }
        if (hasHermesStage && request.hermesReview == null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "HERMES 단계가 포함된 경우 hermesReview 설정이 필요합니다.")
        }
    }

    private fun validateRetryRequest(
        batch: ProblemUploadBatch,
        request: RetryProblemUploadBatchRequest,
    ) {
        if (request.targetStages.distinct().size != request.targetStages.size) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "재파싱 단계는 중복될 수 없습니다.")
        }
        if (batch.parseStatus != ParseStatus.FAILED && batch.parseStatus != ParseStatus.NEEDS_REVIEW) {
            throw ApiException(ErrorCode.CONFLICT, "현재 배치 상태에서는 재파싱을 요청할 수 없습니다.")
        }
        if (batch.parseStatus == ParseStatus.NEEDS_REVIEW && !hasNarrowReviewScope(request)) {
            throw ApiException(ErrorCode.CONFLICT, "검토 필요 배치는 낮은 신뢰도 항목 또는 임시 문제 단위로만 재파싱할 수 있습니다.")
        }
    }

    private fun hasNarrowReviewScope(request: RetryProblemUploadBatchRequest): Boolean =
        request.retryOnlyLowConfidenceItems || request.temporaryProblemIds.isNotEmpty()

    private fun engineName(
        stageType: IngestionStageType,
        request: StartProblemParsingRequest,
    ): String? {
        if (!stageType.name.startsWith("HERMES_")) {
            return null
        }
        val model = request.hermesReview?.model?.takeIf { it.isNotBlank() } ?: "unknown"
        return "hermes-agent-gateway:$model"
    }

    private fun retryEngineName(
        stageType: IngestionStageType,
        batch: ProblemUploadBatch,
    ): String? {
        if (!stageType.name.startsWith("HERMES_")) {
            return null
        }
        val model = batch.parseModel?.takeIf { it.isNotBlank() } ?: "unknown"
        return "hermes-agent-gateway:$model"
    }

    private fun findTeacherProfile(teacherUserId: UUID) =
        teacherProfileRepository.findByUser_Id(teacherUserId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "선생 프로필을 찾을 수 없습니다.")

    private fun findOwnedBatch(
        batchId: UUID,
        teacherId: UUID,
    ): ProblemUploadBatch =
        uploadBatchRepository.findByIdAndTeacherId(batchId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "업로드 배치를 찾을 수 없습니다.")

    private fun findOwnedBatchForUpdate(
        batchId: UUID,
        teacherId: UUID,
    ): ProblemUploadBatch =
        uploadBatchRepository.findByIdAndTeacherIdForUpdate(batchId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "업로드 배치를 찾을 수 없습니다.")

    private fun toResponse(
        batch: ProblemUploadBatch,
        files: List<ProblemUploadFile> = emptyList(),
        stageRuns: List<DocumentIngestionStageRun> = emptyList(),
    ): ProblemUploadBatchResponse =
        ProblemUploadBatchResponse(
            batchId = batch.id!!,
            title = batch.title,
            sourceType = batch.sourceType,
            parseStatus = batch.parseStatus,
            pipelineVersion = batch.pipelineVersion,
            parseModel = batch.parseModel,
            deterministicCoverageRate = batch.deterministicCoverageRate,
            hermesReviewRate = batch.hermesReviewRate,
            hermesTargetedRepairRate = batch.hermesTargetedRepairRate,
            averageConfidence = batch.averageConfidence,
            files = files.map(::toFileResponse),
            stageRuns = stageRuns.map(::toStageRunResponse),
        )

    private fun toFileResponse(file: ProblemUploadFile): ProblemUploadFileResponse =
        ProblemUploadFileResponse(
            id = file.id!!,
            fileAssetId = file.fileAssetId,
            sourceType = file.sourceType,
            pageNumber = file.pageNumber,
        )

    private fun toStageRunResponse(stageRun: DocumentIngestionStageRun): DocumentIngestionStageRunResponse =
        DocumentIngestionStageRunResponse(
            id = stageRun.id!!,
            stageType = stageRun.stageType,
            status = stageRun.status,
            engineName = stageRun.engineName,
            confidence = stageRun.confidence,
        )
}

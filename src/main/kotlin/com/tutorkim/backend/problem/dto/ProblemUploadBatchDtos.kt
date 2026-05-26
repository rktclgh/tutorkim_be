package com.tutorkim.backend.problem.dto

import com.tutorkim.backend.problem.entity.IngestionStageStatus
import com.tutorkim.backend.problem.entity.IngestionStageType
import com.tutorkim.backend.problem.entity.ParseStatus
import com.tutorkim.backend.problem.entity.UploadSourceType
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

data class CreateProblemUploadBatchRequest(
    @field:Size(max = 150)
    val title: String?,

    @field:NotNull
    val sourceType: UploadSourceType,
)

data class AttachProblemUploadFileRequest(
    @field:NotNull
    val fileAssetId: UUID,

    @field:NotNull
    val sourceType: UploadSourceType,

    @field:Min(1)
    val pageNumber: Int?,
)

data class StartProblemParsingRequest(
    @field:NotBlank
    val parseMode: String,

    @field:NotNull
    val subjectId: UUID,

    @field:NotBlank
    @field:Size(max = 80)
    val pipelineVersion: String,

    @field:NotEmpty
    val deterministicStages: List<IngestionStageType>,

    @field:Valid
    val hermesReview: HermesReviewRequest? = null,
)

data class RetryProblemUploadBatchRequest(
    @field:NotEmpty
    @field:Size(max = 10)
    val targetStages: List<IngestionStageType>,

    @field:Size(max = 100)
    val temporaryProblemIds: List<@NotBlank @Size(max = 80) String> = emptyList(),

    val retryOnlyLowConfidenceItems: Boolean = false,
)

data class HermesReviewRequest(
    @field:NotBlank
    val gateway: String,

    val enabled: Boolean = true,

    @field:Size(max = 100)
    val model: String? = null,

    @field:Size(max = 100)
    val reviewScope: String? = null,

    @field:DecimalMin("0.0")
    @field:DecimalMax("1.0")
    val targetedRepairThreshold: BigDecimal? = null,

    @field:Size(max = 100)
    val targetedRepairScope: String? = null,
)

data class ProblemUploadBatchResponse(
    val batchId: UUID,
    val title: String?,
    val sourceType: UploadSourceType,
    val parseStatus: ParseStatus,
    val pipelineVersion: String?,
    val parseModel: String?,
    val deterministicCoverageRate: BigDecimal?,
    val hermesReviewRate: BigDecimal?,
    val hermesTargetedRepairRate: BigDecimal?,
    val averageConfidence: BigDecimal?,
    val files: List<ProblemUploadFileResponse> = emptyList(),
    val stageRuns: List<DocumentIngestionStageRunResponse> = emptyList(),
)

data class ProblemUploadFileResponse(
    val id: UUID,
    val fileAssetId: UUID,
    val sourceType: UploadSourceType,
    val pageNumber: Int?,
)

data class DocumentIngestionStageRunResponse(
    val id: UUID,
    val stageType: IngestionStageType,
    val status: IngestionStageStatus,
    val engineName: String?,
    val confidence: BigDecimal?,
)

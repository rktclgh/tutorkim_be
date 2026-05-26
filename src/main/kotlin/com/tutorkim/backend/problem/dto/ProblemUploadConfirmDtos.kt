package com.tutorkim.backend.problem.dto

import com.tutorkim.backend.problem.entity.ParseStatus
import com.tutorkim.backend.problem.entity.ProblemAnswerType
import com.tutorkim.backend.problem.entity.ProblemBlockType
import com.tutorkim.backend.problem.entity.ProblemExplanationSourceType
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

data class ConfirmProblemUploadBatchRequest(
    @field:NotEmpty
    @field:Size(max = 200)
    @field:Valid
    val problems: List<ConfirmProblemRequest>,
)

data class ConfirmProblemRequest(
    @field:NotBlank
    @field:Size(max = 80)
    val temporaryProblemId: String,

    @field:NotNull
    val answerType: ProblemAnswerType,

    @field:Size(max = 5)
    val correctChoiceNumbers: List<@Min(1) @Max(5) Int> = emptyList(),

    @field:DecimalMin("0")
    val correctNumericAnswer: BigDecimal? = null,

    @field:Min(1)
    @field:Max(5)
    val difficulty: Int,

    @field:NotNull
    val labelDepth1Id: UUID,

    @field:NotNull
    val labelDepth2Id: UUID,

    @field:NotNull
    val labelDepth3Id: UUID,

    val labelDepth4Id: UUID? = null,

    @field:NotEmpty
    @field:Size(max = 80)
    @field:Valid
    val blocks: List<ConfirmProblemBlockRequest>,

    @field:Size(max = 80)
    @field:Valid
    val explanationBlocks: List<ConfirmProblemExplanationBlockRequest> = emptyList(),

    @field:Size(max = 20)
    @field:Valid
    val teacherSolutionAssets: List<ConfirmTeacherSolutionAssetRequest> = emptyList(),
)

data class ConfirmProblemBlockRequest(
    @field:NotNull
    val type: ProblemBlockType,

    val text: String? = null,

    val latex: String? = null,

    val fileAssetId: UUID? = null,

    val provenance: Map<String, Any?> = emptyMap(),

    val metadata: Map<String, Any?> = emptyMap(),
)

data class ConfirmProblemExplanationBlockRequest(
    @field:NotNull
    val type: ProblemBlockType = ProblemBlockType.EXPLANATION,

    val text: String? = null,

    val latex: String? = null,

    val metadata: Map<String, Any?> = emptyMap(),

    val visibleToStudent: Boolean = true,
)

data class ConfirmTeacherSolutionAssetRequest(
    @field:NotNull
    val fileAssetId: UUID,

    @field:NotNull
    val sourceType: ProblemExplanationSourceType = ProblemExplanationSourceType.TEACHER_SOLUTION_IMAGE,

    val visibleToStudent: Boolean = true,

    @field:Size(max = 500)
    val note: String? = null,
)

data class ConfirmProblemUploadBatchResponse(
    val batchId: UUID,
    val parseStatus: ParseStatus,
    val createdProblems: List<ConfirmedProblemResponse>,
)

data class ConfirmedProblemResponse(
    val temporaryProblemId: String,
    val problemId: UUID,
)

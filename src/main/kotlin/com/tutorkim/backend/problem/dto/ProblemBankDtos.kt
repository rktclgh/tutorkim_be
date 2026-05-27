package com.tutorkim.backend.problem.dto

import com.tutorkim.backend.problem.entity.ParseStatus
import com.tutorkim.backend.problem.entity.Problem
import com.tutorkim.backend.problem.entity.ProblemAnswerType
import com.tutorkim.backend.problem.entity.ProblemBlock
import com.tutorkim.backend.problem.entity.ProblemBlockType
import com.tutorkim.backend.problem.entity.ProblemExplanation
import com.tutorkim.backend.problem.entity.ProblemExplanationSourceType
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ProblemSummaryResponse(
    val id: UUID,
    val subjectId: UUID,
    val answerType: ProblemAnswerType,
    val difficulty: Int,
    val labelDepth1Id: UUID?,
    val labelDepth2Id: UUID?,
    val labelDepth3Id: UUID?,
    val labelDepth4Id: UUID?,
    val hasExplanation: Boolean,
    val blockPreview: String?,
    val blockCount: Int,
    val reviewedAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun from(
            problem: Problem,
            blocks: List<ProblemBlock>,
        ): ProblemSummaryResponse =
            ProblemSummaryResponse(
                id = problem.id!!,
                subjectId = problem.subjectId,
                answerType = problem.answerType,
                difficulty = problem.difficulty.toInt(),
                labelDepth1Id = problem.labelDepth1Id,
                labelDepth2Id = problem.labelDepth2Id,
                labelDepth3Id = problem.labelDepth3Id,
                labelDepth4Id = problem.labelDepth4Id,
                hasExplanation = problem.hasExplanation,
                blockPreview = blocks.firstNotNullOfOrNull { it.textContent ?: it.latexContent },
                blockCount = blocks.size,
                reviewedAt = problem.reviewedAt,
                createdAt = problem.createdAt,
            )
    }
}

data class ProblemDetailResponse(
    val id: UUID,
    val subjectId: UUID,
    val sourceBatchId: UUID?,
    val answerType: ProblemAnswerType,
    val correctChoiceNumbers: List<Int>?,
    val correctNumericAnswer: BigDecimal?,
    val difficulty: Int,
    val labelDepth1Id: UUID?,
    val labelDepth2Id: UUID?,
    val labelDepth3Id: UUID?,
    val labelDepth4Id: UUID?,
    val hasExplanation: Boolean,
    val parseStatus: ParseStatus,
    val reviewedAt: Instant?,
    val blocks: List<ProblemBlockResponse>,
    val explanations: List<ProblemExplanationResponse>,
) {
    companion object {
        fun from(
            problem: Problem,
            blocks: List<ProblemBlock>,
            explanations: List<ProblemExplanation>,
        ): ProblemDetailResponse =
            ProblemDetailResponse(
                id = problem.id!!,
                subjectId = problem.subjectId,
                sourceBatchId = problem.sourceBatchId,
                answerType = problem.answerType,
                correctChoiceNumbers = problem.correctChoiceNumbers?.map { it.toInt() },
                correctNumericAnswer = problem.correctNumericAnswer,
                difficulty = problem.difficulty.toInt(),
                labelDepth1Id = problem.labelDepth1Id,
                labelDepth2Id = problem.labelDepth2Id,
                labelDepth3Id = problem.labelDepth3Id,
                labelDepth4Id = problem.labelDepth4Id,
                hasExplanation = problem.hasExplanation,
                parseStatus = problem.parseStatus,
                reviewedAt = problem.reviewedAt,
                blocks = blocks.map(ProblemBlockResponse::from),
                explanations = explanations.map(ProblemExplanationResponse::from),
            )
    }
}

data class ProblemBlockResponse(
    val id: UUID,
    val type: ProblemBlockType,
    val sortOrder: Int,
    val text: String?,
    val latex: String?,
    val fileAssetId: UUID?,
    val metadata: Map<String, Any?>,
) {
    companion object {
        fun from(block: ProblemBlock): ProblemBlockResponse =
            ProblemBlockResponse(
                id = block.id!!,
                type = block.blockType,
                sortOrder = block.sortOrder,
                text = block.textContent,
                latex = block.latexContent,
                fileAssetId = block.fileAssetId,
                metadata = block.metadata,
            )
    }
}

data class ProblemExplanationResponse(
    val id: UUID,
    val sourceType: ProblemExplanationSourceType,
    val sortOrder: Int,
    val text: String?,
    val latex: String?,
    val fileAssetId: UUID?,
    val metadata: Map<String, Any?>,
    val visibleToStudent: Boolean,
) {
    companion object {
        fun from(explanation: ProblemExplanation): ProblemExplanationResponse =
            ProblemExplanationResponse(
                id = explanation.id!!,
                sourceType = explanation.sourceType,
                sortOrder = explanation.sortOrder,
                text = explanation.textContent,
                latex = explanation.latexContent,
                fileAssetId = explanation.fileAssetId,
                metadata = explanation.metadata,
                visibleToStudent = explanation.visibleToStudent,
            )
    }
}

data class UpdateProblemRequest(
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

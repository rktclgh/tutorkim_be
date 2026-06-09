package com.tutorkim.backend.problem.service

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.file.entity.FileAsset
import com.tutorkim.backend.content.entity.CurriculumNode
import com.tutorkim.backend.content.repository.CurriculumNodeRepository
import com.tutorkim.backend.file.repository.FileAssetRepository
import com.tutorkim.backend.problem.dto.ConfirmProblemBlockRequest
import com.tutorkim.backend.problem.dto.ConfirmProblemExplanationBlockRequest
import com.tutorkim.backend.problem.dto.ConfirmTeacherSolutionAssetRequest
import com.tutorkim.backend.problem.entity.ProblemAnswerType
import com.tutorkim.backend.problem.entity.ProblemBlock
import com.tutorkim.backend.problem.entity.ProblemBlockType
import com.tutorkim.backend.problem.entity.ProblemExplanation
import com.tutorkim.backend.problem.entity.ProblemExplanationSourceType
import com.tutorkim.backend.subject.repository.SubjectRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

@Component
class ProblemContentWriteSupport(
    private val subjectRepository: SubjectRepository,
    private val curriculumNodeRepository: CurriculumNodeRepository,
    private val fileAssetRepository: FileAssetRepository,
) {
    fun resolveLabels(
        teacherId: UUID,
        labelDepth1Id: UUID,
        labelDepth2Id: UUID,
        labelDepth3Id: UUID,
        labelDepth4Id: UUID?,
    ): ProblemLabelSet {
        val requestedLabels = listOfNotNull(labelDepth1Id, labelDepth2Id, labelDepth3Id, labelDepth4Id)
        val nodesById = curriculumNodeRepository.findAllById(requestedLabels).associateBy { it.id!! }
        if (nodesById.size != requestedLabels.size) {
            throw ApiException(ErrorCode.NOT_FOUND, "커리큘럼 라벨을 찾을 수 없습니다.")
        }

        val depth1 = nodesById.getValue(labelDepth1Id)
        val depth2 = nodesById.getValue(labelDepth2Id)
        val depth3 = nodesById.getValue(labelDepth3Id)
        val depth4 = labelDepth4Id?.let(nodesById::getValue)
        listOfNotNull(depth1, depth2, depth3, depth4).forEach { node ->
            if (!node.system && node.ownerTeacherId != teacherId) {
                throw ApiException(ErrorCode.NOT_FOUND, "커리큘럼 라벨을 찾을 수 없습니다.")
            }
        }

        val depth4Valid = depth4 == null || depth4.depth.toInt() == 4
        if (depth1.depth.toInt() != 1 || depth2.depth.toInt() != 2 || depth3.depth.toInt() != 3 || !depth4Valid) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "문제 라벨 depth가 올바르지 않습니다.")
        }
        val subjectIds = listOfNotNull(depth1, depth2, depth3, depth4).map { it.subjectId }.toSet()
        if (subjectIds.size != 1) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "문제 라벨은 같은 과목에 속해야 합니다.")
        }
        val depth4ParentValid = depth4 == null || depth4.parentId == depth3.id
        if (depth2.parentId != depth1.id || depth3.parentId != depth2.id || !depth4ParentValid) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "문제 라벨 계층이 올바르지 않습니다.")
        }
        val subjectId = subjectIds.single()
        if (subjectRepository.findByIdAndActiveTrue(subjectId) == null) {
            throw ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없습니다.")
        }

        return ProblemLabelSet(
            subjectId = subjectId,
            depth1 = depth1,
            depth2 = depth2,
            depth3 = depth3,
            depth4 = depth4,
        )
    }

    fun validateAnswer(
        answerType: ProblemAnswerType,
        choiceAnswers: List<Int>,
        numericAnswer: BigDecimal?,
        difficulty: Int,
    ) {
        try {
            ProblemAnswerPolicy.validate(
                ProblemAnswerSpec(
                    answerType = answerType,
                    choiceAnswers = choiceAnswers,
                    numericAnswer = numericAnswer,
                    difficulty = difficulty,
                ),
            )
        } catch (exception: IllegalArgumentException) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "문제 답안 형식이 올바르지 않습니다.", cause = exception)
        }
    }

    fun validateProblemBlocks(
        teacherUserId: UUID,
        blocks: List<ConfirmProblemBlockRequest>,
    ) {
        blocks.forEach { block ->
            if (block.fileAssetId != null && !block.type.isImageBlock()) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "파일 첨부는 이미지 블록에만 사용할 수 있습니다.")
            }
            when (block.type) {
                ProblemBlockType.TEXT -> requireBlockText(block)
                ProblemBlockType.MATH -> requireBlockLatex(block)
                ProblemBlockType.DIAGRAM_IMAGE, ProblemBlockType.TABLE_IMAGE -> requireOwnedImageFile(teacherUserId, block.fileAssetId)
                ProblemBlockType.CHOICE_LIST -> requireBlockText(block)
                ProblemBlockType.EXPLANATION -> throw ApiException(ErrorCode.VALIDATION_ERROR, "문제 본문 블록에는 풀이 블록을 사용할 수 없습니다.")
            }
        }
    }

    fun validateExplanationBlocks(blocks: List<ConfirmProblemExplanationBlockRequest>) {
        blocks.forEach { block ->
            if (block.type != ProblemBlockType.EXPLANATION) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "풀이 블록에는 EXPLANATION 타입만 사용할 수 있습니다.")
            }
            if (block.text.isNullOrBlank() && block.latex.isNullOrBlank()) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "풀이 블록에는 text 또는 latex가 필요합니다.")
            }
        }
    }

    fun validateTeacherSolutionAsset(
        teacherUserId: UUID,
        asset: ConfirmTeacherSolutionAssetRequest,
    ) {
        if (asset.sourceType != ProblemExplanationSourceType.TEACHER_SOLUTION_IMAGE) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "선생 풀이 파일은 TEACHER_SOLUTION_IMAGE만 사용할 수 있습니다.")
        }
        requireOwnedImageFile(teacherUserId, asset.fileAssetId)
    }

    fun buildProblemBlocks(
        problemId: UUID,
        blocks: List<ConfirmProblemBlockRequest>,
    ): List<ProblemBlock> =
        blocks.mapIndexed { index, block ->
            ProblemBlock(
                problemId = problemId,
                blockType = block.type,
                sortOrder = index + 1,
                textContent = block.text?.trim()?.ifBlank { null },
                latexContent = block.latex?.trim()?.ifBlank { null },
                fileAssetId = block.fileAssetId,
                metadata = blockMetadata(block),
            )
        }

    fun buildExplanations(
        teacherUserId: UUID,
        problemId: UUID,
        explanationBlocks: List<ConfirmProblemExplanationBlockRequest>,
        teacherSolutionAssets: List<ConfirmTeacherSolutionAssetRequest>,
    ): List<ProblemExplanation> {
        val textExplanations = explanationBlocks.mapIndexed { index, block ->
            ProblemExplanation(
                problemId = problemId,
                sortOrder = index + 1,
                sourceType = ProblemExplanationSourceType.TEACHER_TEXT,
                textContent = block.text?.trim()?.ifBlank { null },
                latexContent = block.latex?.trim()?.ifBlank { null },
                metadata = block.metadata,
                visibleToStudent = block.visibleToStudent,
                createdBy = teacherUserId,
            )
        }
        val assetOffset = textExplanations.size
        val assetExplanations = teacherSolutionAssets.mapIndexed { index, asset ->
            ProblemExplanation(
                problemId = problemId,
                sortOrder = assetOffset + index + 1,
                sourceType = asset.sourceType,
                fileAssetId = asset.fileAssetId,
                metadata = mapOf("note" to asset.note).filterValues { it != null },
                visibleToStudent = asset.visibleToStudent,
                createdBy = teacherUserId,
            )
        }
        return textExplanations + assetExplanations
    }

    private fun requireBlockText(block: ConfirmProblemBlockRequest) {
        if (block.text.isNullOrBlank()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "텍스트 블록에는 text가 필요합니다.")
        }
    }

    private fun requireBlockLatex(block: ConfirmProblemBlockRequest) {
        if (block.latex.isNullOrBlank()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "수식 블록에는 latex가 필요합니다.")
        }
    }

    private fun requireOwnedFile(
        teacherUserId: UUID,
        fileAssetId: UUID?,
    ): FileAsset {
        val fileAsset = fileAssetId?.let { fileAssetRepository.findByIdAndOwnerUserId(it, teacherUserId) }
        if (fileAsset == null) {
            throw ApiException(ErrorCode.NOT_FOUND, "첨부 파일을 찾을 수 없습니다.")
        }
        return fileAsset
    }

    private fun requireOwnedImageFile(
        teacherUserId: UUID,
        fileAssetId: UUID?,
    ) {
        val fileAsset = requireOwnedFile(teacherUserId, fileAssetId)
        if (fileAsset.contentType !in IMAGE_CONTENT_TYPES) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미지 첨부에는 이미지 파일만 사용할 수 있습니다.")
        }
    }

    private fun blockMetadata(block: ConfirmProblemBlockRequest): Map<String, Any?> =
        if (block.provenance.isEmpty()) {
            block.metadata
        } else {
            block.metadata + ("provenance" to block.provenance)
        }

    private fun ProblemBlockType.isImageBlock(): Boolean =
        this == ProblemBlockType.DIAGRAM_IMAGE || this == ProblemBlockType.TABLE_IMAGE

    companion object {
        private val IMAGE_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}

data class ProblemLabelSet(
    val subjectId: UUID,
    val depth1: CurriculumNode,
    val depth2: CurriculumNode,
    val depth3: CurriculumNode,
    val depth4: CurriculumNode?,
)

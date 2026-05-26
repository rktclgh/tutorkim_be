package com.tutorkim.backend.problem.service

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.content.entity.CurriculumNode
import com.tutorkim.backend.content.repository.CurriculumNodeRepository
import com.tutorkim.backend.file.repository.FileAssetRepository
import com.tutorkim.backend.problem.dto.ConfirmProblemBlockRequest
import com.tutorkim.backend.problem.dto.ConfirmProblemExplanationBlockRequest
import com.tutorkim.backend.problem.dto.ConfirmProblemRequest
import com.tutorkim.backend.problem.dto.ConfirmProblemUploadBatchRequest
import com.tutorkim.backend.problem.dto.ConfirmProblemUploadBatchResponse
import com.tutorkim.backend.problem.dto.ConfirmTeacherSolutionAssetRequest
import com.tutorkim.backend.problem.dto.ConfirmedProblemResponse
import com.tutorkim.backend.problem.entity.ParseStatus
import com.tutorkim.backend.problem.entity.Problem
import com.tutorkim.backend.problem.entity.ProblemBlock
import com.tutorkim.backend.problem.entity.ProblemBlockType
import com.tutorkim.backend.problem.entity.ProblemExplanation
import com.tutorkim.backend.problem.entity.ProblemExplanationSourceType
import com.tutorkim.backend.problem.repository.ProblemBlockRepository
import com.tutorkim.backend.problem.repository.ProblemExplanationRepository
import com.tutorkim.backend.problem.repository.ProblemRepository
import com.tutorkim.backend.problem.repository.ProblemUploadBatchRepository
import com.tutorkim.backend.student.repository.TeacherProfileRepository
import com.tutorkim.backend.subject.repository.SubjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ProblemUploadConfirmService(
    private val teacherProfileRepository: TeacherProfileRepository,
    private val subjectRepository: SubjectRepository,
    private val curriculumNodeRepository: CurriculumNodeRepository,
    private val fileAssetRepository: FileAssetRepository,
    private val uploadBatchRepository: ProblemUploadBatchRepository,
    private val problemRepository: ProblemRepository,
    private val problemBlockRepository: ProblemBlockRepository,
    private val problemExplanationRepository: ProblemExplanationRepository,
) {
    @Transactional
    fun confirm(
        teacherUserId: UUID,
        batchId: UUID,
        request: ConfirmProblemUploadBatchRequest,
    ): ConfirmProblemUploadBatchResponse {
        val teacher = teacherProfileRepository.findByUser_Id(teacherUserId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "선생 프로필을 찾을 수 없습니다.")
        val teacherId = teacher.id!!
        val batch = uploadBatchRepository.findByIdAndTeacherIdForUpdate(batchId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "업로드 배치를 찾을 수 없습니다.")

        if (batch.parseStatus != ParseStatus.NEEDS_REVIEW) {
            throw ApiException(ErrorCode.CONFLICT, "검토가 필요한 배치만 문제은행에 확정할 수 있습니다.")
        }
        if (request.problems.map { it.temporaryProblemId }.distinct().size != request.problems.size) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "임시 문제 ID는 중복될 수 없습니다.")
        }

        val now = Instant.now()
        val createdProblems = request.problems.map { problemRequest ->
            createProblem(
                teacherUserId = teacherUserId,
                teacherId = teacherId,
                batchId = batch.id!!,
                request = problemRequest,
                reviewedAt = now,
            )
        }

        batch.parseStatus = ParseStatus.REVIEWED
        uploadBatchRepository.save(batch)

        return ConfirmProblemUploadBatchResponse(
            batchId = batch.id!!,
            parseStatus = batch.parseStatus,
            createdProblems = createdProblems,
        )
    }

    private fun createProblem(
        teacherUserId: UUID,
        teacherId: UUID,
        batchId: UUID,
        request: ConfirmProblemRequest,
        reviewedAt: Instant,
    ): ConfirmedProblemResponse {
        val labels = resolveLabels(teacherId, request)
        validateAnswer(request)
        validateProblemBlocks(teacherUserId, request.blocks)
        validateExplanationBlocks(request.explanationBlocks)
        request.teacherSolutionAssets.forEach { validateTeacherSolutionAsset(teacherUserId, it) }

        val savedProblem = problemRepository.saveAndFlush(
            Problem(
                ownerTeacherId = teacherId,
                subjectId = labels.subjectId,
                sourceBatchId = batchId,
                answerType = request.answerType,
                correctChoiceNumbers = request.correctChoiceNumbers.map { it.toShort() }.ifEmpty { null },
                correctNumericAnswer = request.correctNumericAnswer,
                difficulty = request.difficulty.toShort(),
                labelDepth1Id = labels.depth1.id!!,
                labelDepth2Id = labels.depth2.id!!,
                labelDepth3Id = labels.depth3.id!!,
                labelDepth4Id = labels.depth4?.id,
                hasExplanation = request.explanationBlocks.isNotEmpty() || request.teacherSolutionAssets.isNotEmpty(),
                parseStatus = ParseStatus.REVIEWED,
                reviewedAt = reviewedAt,
            ),
        )
        val problemId = savedProblem.id!!
        problemBlockRepository.saveAll(
            request.blocks.mapIndexed { index, block ->
                ProblemBlock(
                    problemId = problemId,
                    blockType = block.type,
                    sortOrder = index + 1,
                    textContent = block.text?.trim()?.ifBlank { null },
                    latexContent = block.latex?.trim()?.ifBlank { null },
                    fileAssetId = block.fileAssetId,
                    metadata = blockMetadata(block),
                )
            },
        )
        problemExplanationRepository.saveAll(
            buildExplanations(
                teacherUserId = teacherUserId,
                problemId = problemId,
                request = request,
            ),
        )

        return ConfirmedProblemResponse(
            temporaryProblemId = request.temporaryProblemId,
            problemId = problemId,
        )
    }

    private fun resolveLabels(
        teacherId: UUID,
        request: ConfirmProblemRequest,
    ): LabelSet {
        val requestedLabels = listOfNotNull(
            request.labelDepth1Id,
            request.labelDepth2Id,
            request.labelDepth3Id,
            request.labelDepth4Id,
        )
        val nodesById = curriculumNodeRepository.findAllById(requestedLabels).associateBy { it.id!! }
        if (nodesById.size != requestedLabels.size) {
            throw ApiException(ErrorCode.NOT_FOUND, "커리큘럼 라벨을 찾을 수 없습니다.")
        }

        val depth1 = nodesById.getValue(request.labelDepth1Id)
        val depth2 = nodesById.getValue(request.labelDepth2Id)
        val depth3 = nodesById.getValue(request.labelDepth3Id)
        val depth4 = request.labelDepth4Id?.let(nodesById::getValue)
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

        return LabelSet(
            subjectId = subjectId,
            depth1 = depth1,
            depth2 = depth2,
            depth3 = depth3,
            depth4 = depth4,
        )
    }

    private fun validateAnswer(request: ConfirmProblemRequest) {
        try {
            ProblemAnswerPolicy.validate(
                ProblemAnswerSpec(
                    answerType = request.answerType,
                    choiceAnswers = request.correctChoiceNumbers,
                    numericAnswer = request.correctNumericAnswer,
                    difficulty = request.difficulty,
                ),
            )
        } catch (exception: IllegalArgumentException) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "문제 답안 형식이 올바르지 않습니다.", cause = exception)
        }
    }

    private fun validateProblemBlocks(
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
                ProblemBlockType.DIAGRAM_IMAGE, ProblemBlockType.TABLE_IMAGE -> requireOwnedFile(teacherUserId, block.fileAssetId)
                ProblemBlockType.CHOICE_LIST -> requireBlockText(block)
                ProblemBlockType.EXPLANATION -> throw ApiException(ErrorCode.VALIDATION_ERROR, "문제 본문 블록에는 풀이 블록을 사용할 수 없습니다.")
            }
        }
    }

    private fun validateExplanationBlocks(blocks: List<ConfirmProblemExplanationBlockRequest>) {
        blocks.forEach { block ->
            if (block.type != ProblemBlockType.EXPLANATION) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "풀이 블록에는 EXPLANATION 타입만 사용할 수 있습니다.")
            }
            if (block.text.isNullOrBlank() && block.latex.isNullOrBlank()) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "풀이 블록에는 text 또는 latex가 필요합니다.")
            }
        }
    }

    private fun validateTeacherSolutionAsset(
        teacherUserId: UUID,
        asset: ConfirmTeacherSolutionAssetRequest,
    ) {
        if (asset.sourceType != ProblemExplanationSourceType.TEACHER_SOLUTION_IMAGE) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "선생 풀이 파일은 TEACHER_SOLUTION_IMAGE만 사용할 수 있습니다.")
        }
        requireOwnedFile(teacherUserId, asset.fileAssetId)
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
    ) {
        if (fileAssetId == null || fileAssetRepository.findByIdAndOwnerUserId(fileAssetId, teacherUserId) == null) {
            throw ApiException(ErrorCode.NOT_FOUND, "첨부 파일을 찾을 수 없습니다.")
        }
    }

    private fun blockMetadata(block: ConfirmProblemBlockRequest): Map<String, Any?> =
        if (block.provenance.isEmpty()) {
            block.metadata
        } else {
            block.metadata + ("provenance" to block.provenance)
        }

    private fun buildExplanations(
        teacherUserId: UUID,
        problemId: UUID,
        request: ConfirmProblemRequest,
    ): List<ProblemExplanation> {
        val explanationBlocks = request.explanationBlocks.mapIndexed { index, block ->
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
        val assetOffset = explanationBlocks.size
        val teacherSolutionAssets = request.teacherSolutionAssets.mapIndexed { index, asset ->
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
        return explanationBlocks + teacherSolutionAssets
    }

    private fun ProblemBlockType.isImageBlock(): Boolean =
        this == ProblemBlockType.DIAGRAM_IMAGE || this == ProblemBlockType.TABLE_IMAGE

    private data class LabelSet(
        val subjectId: UUID,
        val depth1: CurriculumNode,
        val depth2: CurriculumNode,
        val depth3: CurriculumNode,
        val depth4: CurriculumNode?,
    )
}

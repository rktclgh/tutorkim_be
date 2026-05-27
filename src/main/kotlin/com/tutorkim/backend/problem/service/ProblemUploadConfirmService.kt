package com.tutorkim.backend.problem.service

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.problem.dto.ConfirmProblemRequest
import com.tutorkim.backend.problem.dto.ConfirmProblemUploadBatchRequest
import com.tutorkim.backend.problem.dto.ConfirmProblemUploadBatchResponse
import com.tutorkim.backend.problem.dto.ConfirmedProblemResponse
import com.tutorkim.backend.problem.entity.ParseStatus
import com.tutorkim.backend.problem.entity.Problem
import com.tutorkim.backend.problem.repository.ProblemBlockRepository
import com.tutorkim.backend.problem.repository.ProblemExplanationRepository
import com.tutorkim.backend.problem.repository.ProblemRepository
import com.tutorkim.backend.problem.repository.ProblemUploadBatchRepository
import com.tutorkim.backend.student.repository.TeacherProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ProblemUploadConfirmService(
    private val teacherProfileRepository: TeacherProfileRepository,
    private val problemContentWriteSupport: ProblemContentWriteSupport,
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
        val labels = problemContentWriteSupport.resolveLabels(
            teacherId = teacherId,
            labelDepth1Id = request.labelDepth1Id,
            labelDepth2Id = request.labelDepth2Id,
            labelDepth3Id = request.labelDepth3Id,
            labelDepth4Id = request.labelDepth4Id,
        )
        problemContentWriteSupport.validateAnswer(
            answerType = request.answerType,
            choiceAnswers = request.correctChoiceNumbers,
            numericAnswer = request.correctNumericAnswer,
            difficulty = request.difficulty,
        )
        problemContentWriteSupport.validateProblemBlocks(teacherUserId, request.blocks)
        problemContentWriteSupport.validateExplanationBlocks(request.explanationBlocks)
        request.teacherSolutionAssets.forEach { problemContentWriteSupport.validateTeacherSolutionAsset(teacherUserId, it) }

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
            problemContentWriteSupport.buildProblemBlocks(problemId, request.blocks),
        )
        problemExplanationRepository.saveAll(
            problemContentWriteSupport.buildExplanations(
                teacherUserId = teacherUserId,
                problemId = problemId,
                explanationBlocks = request.explanationBlocks,
                teacherSolutionAssets = request.teacherSolutionAssets,
            ),
        )

        return ConfirmedProblemResponse(
            temporaryProblemId = request.temporaryProblemId,
            problemId = problemId,
        )
    }
}

package com.tutorkim.backend.problem.service

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.problem.dto.AttachTeacherSolutionRequest
import com.tutorkim.backend.problem.dto.ConfirmTeacherSolutionAssetRequest
import com.tutorkim.backend.problem.dto.ProblemDetailResponse
import com.tutorkim.backend.problem.dto.ProblemSummaryResponse
import com.tutorkim.backend.problem.dto.UpdateProblemRequest
import com.tutorkim.backend.problem.entity.Problem
import com.tutorkim.backend.problem.entity.ProblemAnswerType
import com.tutorkim.backend.problem.entity.ProblemExplanation
import com.tutorkim.backend.problem.entity.ProblemExplanationSourceType
import com.tutorkim.backend.problem.repository.ProblemBlockRepository
import com.tutorkim.backend.problem.repository.ProblemExplanationRepository
import com.tutorkim.backend.problem.repository.ProblemRepository
import com.tutorkim.backend.student.repository.TeacherProfileRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ProblemBankService(
    private val teacherProfileRepository: TeacherProfileRepository,
    private val problemRepository: ProblemRepository,
    private val problemBlockRepository: ProblemBlockRepository,
    private val problemExplanationRepository: ProblemExplanationRepository,
    private val problemContentWriteSupport: ProblemContentWriteSupport,
) {
    @Transactional(readOnly = true)
    fun listProblems(
        teacherUserId: UUID,
        subjectId: UUID?,
        depth1Id: UUID?,
        depth2Id: UUID?,
        depth3Id: UUID?,
        depth4Id: UUID?,
        difficulty: Int?,
        answerType: ProblemAnswerType?,
        limit: Int,
    ): List<ProblemSummaryResponse> {
        val teacherId = findTeacherId(teacherUserId)
        val difficultyValue = difficulty?.also {
            if (it !in 1..5) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "난이도는 1에서 5 사이여야 합니다.")
            }
        }?.toShort()
        if (limit !in 1..200) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "문제 목록 조회 개수는 1에서 200 사이여야 합니다.")
        }

        val problems = problemRepository.searchActiveForOwner(
            ownerTeacherId = teacherId,
            subjectId = subjectId,
            depth1Id = depth1Id,
            depth2Id = depth2Id,
            depth3Id = depth3Id,
            depth4Id = depth4Id,
            difficulty = difficultyValue,
            answerType = answerType,
            pageable = PageRequest.of(0, limit),
        )
        if (problems.isEmpty()) {
            return emptyList()
        }

        val blocksByProblemId = problemBlockRepository.findByProblemIdIn(problems.mapNotNull { it.id })
            .groupBy { it.problemId }
            .mapValues { (_, blocks) -> blocks.sortedBy { it.sortOrder } }

        return problems.map { problem ->
            ProblemSummaryResponse.from(
                problem = problem,
                blocks = blocksByProblemId[problem.id!!].orEmpty(),
            )
        }
    }

    @Transactional(readOnly = true)
    fun getProblem(
        teacherUserId: UUID,
        problemId: UUID,
    ): ProblemDetailResponse {
        val teacherId = findTeacherId(teacherUserId)
        val problem = findOwnedActiveProblem(problemId, teacherId)
        return detail(problem.id!!, problem)
    }

    @Transactional
    fun updateProblem(
        teacherUserId: UUID,
        problemId: UUID,
        request: UpdateProblemRequest,
    ): ProblemDetailResponse {
        val teacherId = findTeacherId(teacherUserId)
        val problem = problemRepository.findActiveByIdAndOwnerTeacherIdForUpdate(problemId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "문제를 찾을 수 없습니다.")
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

        problem.subjectId = labels.subjectId
        problem.answerType = request.answerType
        problem.correctChoiceNumbers = request.correctChoiceNumbers.map { it.toShort() }.ifEmpty { null }
        problem.correctNumericAnswer = request.correctNumericAnswer
        problem.difficulty = request.difficulty.toShort()
        problem.labelDepth1Id = labels.depth1.id!!
        problem.labelDepth2Id = labels.depth2.id!!
        problem.labelDepth3Id = labels.depth3.id!!
        problem.labelDepth4Id = labels.depth4?.id
        problem.hasExplanation = request.explanationBlocks.isNotEmpty() || request.teacherSolutionAssets.isNotEmpty()

        val savedProblem = problemRepository.saveAndFlush(problem)
        replaceBlocksAndExplanations(
            teacherUserId = teacherUserId,
            problemId = savedProblem.id!!,
            request = request,
        )

        return detail(savedProblem.id!!, savedProblem)
    }

    @Transactional
    fun attachTeacherSolution(
        teacherUserId: UUID,
        problemId: UUID,
        request: AttachTeacherSolutionRequest,
    ): ProblemDetailResponse {
        val teacherId = findTeacherId(teacherUserId)
        val problem = problemRepository.findActiveByIdAndOwnerTeacherIdForUpdate(problemId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "문제를 찾을 수 없습니다.")
        val assetRequest = ConfirmTeacherSolutionAssetRequest(
            fileAssetId = request.fileAssetId,
            visibleToStudent = request.visibleToStudent,
            note = request.note,
        )
        problemContentWriteSupport.validateTeacherSolutionAsset(teacherUserId, assetRequest)

        val explanation = ProblemExplanation(
            problemId = problem.id!!,
            sortOrder = nextActiveExplanationSortOrder(problem.id!!),
            sourceType = ProblemExplanationSourceType.TEACHER_SOLUTION_IMAGE,
            fileAssetId = request.fileAssetId,
            metadata = mapOf("note" to request.note).filterValues { it != null },
            visibleToStudent = request.visibleToStudent,
            createdBy = teacherUserId,
        )
        problem.hasExplanation = true
        val savedProblem = problemRepository.saveAndFlush(problem)
        problemExplanationRepository.saveAndFlush(explanation)

        return detail(savedProblem.id!!, savedProblem)
    }

    @Transactional
    fun deleteTeacherSolution(
        teacherUserId: UUID,
        problemId: UUID,
        explanationId: UUID,
    ): ProblemDetailResponse {
        val teacherId = findTeacherId(teacherUserId)
        val problem = problemRepository.findActiveByIdAndOwnerTeacherIdForUpdate(problemId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "문제를 찾을 수 없습니다.")
        val explanation = problemExplanationRepository.findByIdAndProblemIdAndArchivedAtIsNull(explanationId, problem.id!!)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "선생 풀이 파일을 찾을 수 없습니다.")
        if (explanation.sourceType != ProblemExplanationSourceType.TEACHER_SOLUTION_IMAGE || explanation.fileAssetId == null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "선생 풀이 파일만 삭제할 수 있습니다.")
        }

        archiveExplanation(explanation, problem.id!!)
        problem.hasExplanation = problemExplanationRepository.existsByProblemIdAndArchivedAtIsNull(problem.id!!)
        val savedProblem = problemRepository.saveAndFlush(problem)

        return detail(savedProblem.id!!, savedProblem)
    }

    @Transactional
    fun archiveProblem(
        teacherUserId: UUID,
        problemId: UUID,
    ) {
        val teacherId = findTeacherId(teacherUserId)
        val problem = problemRepository.findActiveByIdAndOwnerTeacherIdForUpdate(problemId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "문제를 찾을 수 없습니다.")
        problem.archivedAt = Instant.now()
        problem.archivedBy = teacherUserId
        problemRepository.save(problem)
    }

    private fun replaceBlocksAndExplanations(
        teacherUserId: UUID,
        problemId: UUID,
        request: UpdateProblemRequest,
    ) {
        problemBlockRepository.deleteByProblemId(problemId)
        archiveActiveExplanations(problemId)
        problemExplanationRepository.flush()
        problemBlockRepository.saveAll(problemContentWriteSupport.buildProblemBlocks(problemId, request.blocks))
        problemExplanationRepository.saveAll(
            problemContentWriteSupport.buildExplanations(
                teacherUserId = teacherUserId,
                problemId = problemId,
                explanationBlocks = request.explanationBlocks,
                teacherSolutionAssets = request.teacherSolutionAssets,
            ),
        )
    }

    private fun archiveActiveExplanations(problemId: UUID) {
        val activeExplanations =
            problemExplanationRepository.findByProblemIdAndArchivedAtIsNullOrderBySortOrderAsc(problemId)
        if (activeExplanations.isEmpty()) {
            return
        }

        val minSortOrder = problemExplanationRepository.findMinSortOrderByProblemId(problemId) ?: 0
        val now = Instant.now()
        activeExplanations.forEachIndexed { index, explanation ->
            explanation.archivedAt = now
            explanation.sortOrder = minSortOrder - index - 1
        }
        problemExplanationRepository.saveAll(activeExplanations)
    }

    private fun archiveExplanation(
        explanation: ProblemExplanation,
        problemId: UUID,
    ) {
        val minSortOrder = problemExplanationRepository.findMinSortOrderByProblemId(problemId) ?: 0
        explanation.archivedAt = Instant.now()
        explanation.sortOrder = minSortOrder - 1
        problemExplanationRepository.saveAndFlush(explanation)
    }

    private fun nextActiveExplanationSortOrder(problemId: UUID): Int =
        (problemExplanationRepository.findMaxActiveSortOrderByProblemId(problemId) ?: 0) + 1

    private fun detail(
        problemId: UUID,
        problem: Problem,
    ): ProblemDetailResponse =
        ProblemDetailResponse.from(
            problem = problem,
            blocks = problemBlockRepository.findByProblemIdOrderBySortOrderAsc(problemId),
            explanations = problemExplanationRepository.findByProblemIdAndArchivedAtIsNullOrderBySortOrderAsc(problemId),
        )

    private fun findTeacherId(teacherUserId: UUID): UUID =
        teacherProfileRepository.findByUser_Id(teacherUserId)?.id
            ?: throw ApiException(ErrorCode.NOT_FOUND, "선생 프로필을 찾을 수 없습니다.")

    private fun findOwnedActiveProblem(
        problemId: UUID,
        teacherId: UUID,
    ): Problem =
        problemRepository.findByIdAndOwnerTeacherIdAndArchivedAtIsNullAndDeletedAtIsNull(problemId, teacherId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "문제를 찾을 수 없습니다.")
}

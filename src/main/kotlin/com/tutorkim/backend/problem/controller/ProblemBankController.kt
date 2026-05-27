package com.tutorkim.backend.problem.controller

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.common.web.API_PREFIX
import com.tutorkim.backend.problem.dto.ProblemDetailResponse
import com.tutorkim.backend.problem.dto.ProblemSummaryResponse
import com.tutorkim.backend.problem.dto.UpdateProblemRequest
import com.tutorkim.backend.problem.entity.ProblemAnswerType
import com.tutorkim.backend.problem.service.ProblemBankService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(API_PREFIX)
class ProblemBankController(
    private val problemBankService: ProblemBankService,
) {
    @GetMapping("/problems")
    fun listProblems(
        authentication: Authentication,
        @RequestParam subjectId: UUID?,
        @RequestParam depth1Id: UUID?,
        @RequestParam depth2Id: UUID?,
        @RequestParam depth3Id: UUID?,
        @RequestParam depth4Id: UUID?,
        @RequestParam difficulty: Int?,
        @RequestParam answerType: ProblemAnswerType?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<ProblemSummaryResponse> =
        problemBankService.listProblems(
            teacherUserId = currentUserId(authentication),
            subjectId = subjectId,
            depth1Id = depth1Id,
            depth2Id = depth2Id,
            depth3Id = depth3Id,
            depth4Id = depth4Id,
            difficulty = difficulty,
            answerType = answerType,
            limit = validatedLimit(limit),
        )

    @GetMapping("/problems/{problemId}")
    fun getProblem(
        authentication: Authentication,
        @PathVariable problemId: UUID,
    ): ProblemDetailResponse =
        problemBankService.getProblem(
            teacherUserId = currentUserId(authentication),
            problemId = problemId,
        )

    @PatchMapping("/problems/{problemId}")
    fun updateProblem(
        authentication: Authentication,
        @PathVariable problemId: UUID,
        @Valid @RequestBody request: UpdateProblemRequest,
    ): ProblemDetailResponse =
        problemBankService.updateProblem(
            teacherUserId = currentUserId(authentication),
            problemId = problemId,
            request = request,
        )

    private fun currentUserId(authentication: Authentication): UUID =
        try {
            UUID.fromString(authentication.name)
        } catch (exception: IllegalArgumentException) {
            throw ApiException(ErrorCode.UNAUTHORIZED, cause = exception)
        }

    private fun validatedLimit(limit: Int): Int {
        if (limit !in 1..200) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "문제 목록 조회 개수는 1에서 200 사이여야 합니다.")
        }
        return limit
    }
}

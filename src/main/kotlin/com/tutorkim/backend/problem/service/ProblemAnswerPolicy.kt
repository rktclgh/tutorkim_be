package com.tutorkim.backend.problem.service

import com.tutorkim.backend.problem.entity.ProblemAnswerType
import java.math.BigDecimal

data class ProblemAnswerSpec(
	val answerType: ProblemAnswerType,
	val choiceAnswers: List<Int> = emptyList(),
	val numericAnswer: BigDecimal? = null,
	val difficulty: Int,
)

object ProblemAnswerPolicy {
	fun validate(spec: ProblemAnswerSpec) {
		require(spec.difficulty in 1..5) { "difficulty must be between 1 and 5" }

		when (spec.answerType) {
			ProblemAnswerType.SINGLE_CHOICE -> {
				require(spec.numericAnswer == null) { "single choice answer must not include a numeric answer" }
				require(spec.choiceAnswers.size == 1) { "single choice answer must include exactly one choice" }
				require(spec.choiceAnswers.single() in VALID_CHOICE_RANGE) { "single choice answer must be between 1 and 5" }
			}

			ProblemAnswerType.MULTIPLE_CHOICE -> {
				require(spec.numericAnswer == null) { "multiple choice answer must not include a numeric answer" }
				require(spec.choiceAnswers.size >= 2) { "multiple choice answer must include at least two choices" }
				require(spec.choiceAnswers.size == spec.choiceAnswers.toSet().size) {
					"multiple choice answers must not include duplicates"
				}
				require(spec.choiceAnswers.all { it in VALID_CHOICE_RANGE }) {
					"multiple choice answers must be between 1 and 5"
				}
			}

			ProblemAnswerType.NUMERIC -> {
				require(spec.choiceAnswers.isEmpty()) { "numeric answer must not include choices" }
				require(spec.numericAnswer != null) { "numeric answer is required" }
			}
		}
	}

	private val VALID_CHOICE_RANGE = 1..5
}

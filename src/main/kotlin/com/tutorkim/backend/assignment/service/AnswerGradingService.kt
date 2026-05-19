package com.tutorkim.backend.assignment.service

import com.tutorkim.backend.assignment.entity.AnswerType
import com.tutorkim.backend.assignment.entity.GradingDecision
import java.math.BigDecimal

data class AnswerKey(
	val answerType: AnswerType,
	val correctChoiceNumbers: List<Int>?,
	val correctNumericAnswer: BigDecimal?,
)

data class StudentAnswer(
	val selectedChoiceNumbers: List<Int> = emptyList(),
	val numericAnswer: BigDecimal? = null,
	val unknown: Boolean = false,
)

data class GradingResult(
	val isCorrect: Boolean?,
	val decision: GradingDecision,
)

class AnswerGradingService {

	fun grade(answerKey: AnswerKey, studentAnswer: StudentAnswer): GradingResult {
		if (studentAnswer.unknown) {
			return GradingResult(isCorrect = null, decision = GradingDecision.UNKNOWN)
		}

		val isCorrect = when (answerKey.answerType) {
			AnswerType.SINGLE_CHOICE -> gradeSingleChoice(answerKey.correctChoiceNumbers, studentAnswer.selectedChoiceNumbers)
			AnswerType.MULTIPLE_CHOICE -> gradeMultipleChoice(answerKey.correctChoiceNumbers, studentAnswer.selectedChoiceNumbers)
			AnswerType.NUMERIC -> gradeNumeric(answerKey.correctNumericAnswer, studentAnswer.numericAnswer)
		} ?: return manualReview()

		return GradingResult(isCorrect = isCorrect, decision = GradingDecision.AUTO_GRADED)
	}

	private fun gradeSingleChoice(correct: List<Int>?, selected: List<Int>): Boolean? {
		if (correct == null || correct.size != 1) {
			return null
		}
		return selected.size == 1 && selected.single() == correct.single()
	}

	private fun gradeMultipleChoice(correct: List<Int>?, selected: List<Int>): Boolean? {
		if (correct == null || correct.size < 2) {
			return null
		}
		val correctSet = correct.toSet()
		if (correct.size != correctSet.size) {
			return null
		}
		val selectedSet = selected.toSet()
		return selectedSet == correctSet && selected.size == selectedSet.size
	}

	private fun gradeNumeric(correct: BigDecimal?, selected: BigDecimal?): Boolean? {
		if (correct == null) {
			return null
		}
		return selected?.compareTo(correct) == 0
	}

	private fun manualReview(): GradingResult =
		GradingResult(isCorrect = null, decision = GradingDecision.MANUAL_REVIEW_REQUIRED)
}

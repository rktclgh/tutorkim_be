package com.tutorkim.backend.assignment.service

import com.tutorkim.backend.assignment.entity.AnswerType
import com.tutorkim.backend.assignment.entity.GradingDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AnswerGradingServiceTest {

	private val service = AnswerGradingService()

	@Test
	fun `single choice is correct only when exactly one selected choice matches`() {
		val key = AnswerKey(
			answerType = AnswerType.SINGLE_CHOICE,
			correctChoiceNumbers = listOf(2),
			correctNumericAnswer = null,
		)

		assertEquals(true, service.grade(key, StudentAnswer(selectedChoiceNumbers = listOf(2))).isCorrect)
		assertEquals(false, service.grade(key, StudentAnswer(selectedChoiceNumbers = listOf(1))).isCorrect)
		assertEquals(false, service.grade(key, StudentAnswer(selectedChoiceNumbers = listOf(2, 3))).isCorrect)
		assertEquals(false, service.grade(key, StudentAnswer(selectedChoiceNumbers = emptyList())).isCorrect)
	}

	@Test
	fun `multiple choice uses exact set match and ignores selected order`() {
		val key = AnswerKey(
			answerType = AnswerType.MULTIPLE_CHOICE,
			correctChoiceNumbers = listOf(2, 5),
			correctNumericAnswer = null,
		)

		assertEquals(true, service.grade(key, StudentAnswer(selectedChoiceNumbers = listOf(5, 2))).isCorrect)
		assertEquals(false, service.grade(key, StudentAnswer(selectedChoiceNumbers = listOf(2))).isCorrect)
		assertEquals(false, service.grade(key, StudentAnswer(selectedChoiceNumbers = listOf(2, 3, 5))).isCorrect)
	}

	@Test
	fun `numeric grading compares exact numeric value`() {
		val key = AnswerKey(
			answerType = AnswerType.NUMERIC,
			correctChoiceNumbers = null,
			correctNumericAnswer = BigDecimal("42.0"),
		)

		assertEquals(true, service.grade(key, StudentAnswer(numericAnswer = BigDecimal("42.00"))).isCorrect)
		assertEquals(false, service.grade(key, StudentAnswer(numericAnswer = BigDecimal("42.01"))).isCorrect)
		assertEquals(false, service.grade(key, StudentAnswer(numericAnswer = null)).isCorrect)
	}

	@Test
	fun `unknown answer returns unknown grading result`() {
		val key = AnswerKey(
			answerType = AnswerType.SINGLE_CHOICE,
			correctChoiceNumbers = listOf(2),
			correctNumericAnswer = null,
		)

		val result = service.grade(key, StudentAnswer(selectedChoiceNumbers = listOf(2), unknown = true))

		assertNull(result.isCorrect)
		assertEquals(GradingDecision.UNKNOWN, result.decision)
	}

	@Test
	fun `missing answer key returns manual grading result`() {
		val key = AnswerKey(
			answerType = AnswerType.SINGLE_CHOICE,
			correctChoiceNumbers = null,
			correctNumericAnswer = null,
		)

		val result = service.grade(key, StudentAnswer(selectedChoiceNumbers = listOf(2)))

		assertNull(result.isCorrect)
		assertEquals(GradingDecision.MANUAL_REVIEW_REQUIRED, result.decision)
	}

	@Test
	fun `duplicate multiple choice answer key returns manual grading result`() {
		val key = AnswerKey(
			answerType = AnswerType.MULTIPLE_CHOICE,
			correctChoiceNumbers = listOf(2, 2),
			correctNumericAnswer = null,
		)

		val result = service.grade(key, StudentAnswer(selectedChoiceNumbers = listOf(2)))

		assertNull(result.isCorrect)
		assertEquals(GradingDecision.MANUAL_REVIEW_REQUIRED, result.decision)
	}
}

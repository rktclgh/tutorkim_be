package com.tutorkim.backend.problem.service

import com.tutorkim.backend.problem.entity.ProblemAnswerType
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ProblemAnswerPolicyTest {

	@Test
	fun `single choice requires exactly one choice from one to five`() {
		ProblemAnswerPolicy.validate(
			ProblemAnswerSpec(
				answerType = ProblemAnswerType.SINGLE_CHOICE,
				choiceAnswers = listOf(3),
				numericAnswer = null,
				difficulty = 4,
			),
		)

		assertFailsWith<IllegalArgumentException> {
			ProblemAnswerPolicy.validate(
				ProblemAnswerSpec(
					answerType = ProblemAnswerType.SINGLE_CHOICE,
					choiceAnswers = listOf(1, 2),
					numericAnswer = null,
					difficulty = 3,
				),
			)
		}

		assertFailsWith<IllegalArgumentException> {
			ProblemAnswerPolicy.validate(
				ProblemAnswerSpec(
					answerType = ProblemAnswerType.SINGLE_CHOICE,
					choiceAnswers = listOf(6),
					numericAnswer = null,
					difficulty = 3,
				),
			)
		}
	}

	@Test
	fun `multiple choice requires at least one unique choice from one to five`() {
		ProblemAnswerPolicy.validate(
			ProblemAnswerSpec(
				answerType = ProblemAnswerType.MULTIPLE_CHOICE,
				choiceAnswers = listOf(2),
				numericAnswer = null,
				difficulty = 2,
			),
		)

		assertFailsWith<IllegalArgumentException> {
			ProblemAnswerPolicy.validate(
				ProblemAnswerSpec(
					answerType = ProblemAnswerType.MULTIPLE_CHOICE,
					choiceAnswers = emptyList(),
					numericAnswer = null,
					difficulty = 2,
				),
			)
		}

		assertFailsWith<IllegalArgumentException> {
			ProblemAnswerPolicy.validate(
				ProblemAnswerSpec(
					answerType = ProblemAnswerType.MULTIPLE_CHOICE,
					choiceAnswers = listOf(0, 2),
					numericAnswer = null,
					difficulty = 2,
				),
			)
		}
	}

	@Test
	fun `numeric answer requires a numeric value and no choice list`() {
		ProblemAnswerPolicy.validate(
			ProblemAnswerSpec(
				answerType = ProblemAnswerType.NUMERIC,
				choiceAnswers = emptyList(),
				numericAnswer = BigDecimal("12.5"),
				difficulty = 5,
			),
		)

		assertFailsWith<IllegalArgumentException> {
			ProblemAnswerPolicy.validate(
				ProblemAnswerSpec(
					answerType = ProblemAnswerType.NUMERIC,
					choiceAnswers = listOf(1),
					numericAnswer = BigDecimal("12.5"),
					difficulty = 5,
				),
			)
		}

		assertFailsWith<IllegalArgumentException> {
			ProblemAnswerPolicy.validate(
				ProblemAnswerSpec(
					answerType = ProblemAnswerType.NUMERIC,
					choiceAnswers = emptyList(),
					numericAnswer = null,
					difficulty = 5,
				),
			)
		}
	}

	@Test
	fun `difficulty must be between one and five`() {
		assertFailsWith<IllegalArgumentException> {
			ProblemAnswerPolicy.validate(
				ProblemAnswerSpec(
					answerType = ProblemAnswerType.SINGLE_CHOICE,
					choiceAnswers = listOf(1),
					numericAnswer = null,
					difficulty = 0,
				),
			)
		}

		assertFailsWith<IllegalArgumentException> {
			ProblemAnswerPolicy.validate(
				ProblemAnswerSpec(
					answerType = ProblemAnswerType.SINGLE_CHOICE,
					choiceAnswers = listOf(1),
					numericAnswer = null,
					difficulty = 6,
				),
			)
		}
	}
}

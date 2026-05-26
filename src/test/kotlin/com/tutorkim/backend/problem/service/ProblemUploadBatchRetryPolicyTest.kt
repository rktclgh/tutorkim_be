package com.tutorkim.backend.problem.service

import com.tutorkim.backend.problem.entity.ParseStatus
import com.tutorkim.backend.problem.entity.ProblemUploadBatch
import com.tutorkim.backend.problem.entity.UploadSourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProblemUploadBatchRetryPolicyTest {

	@Test
	fun `retry from failed increments retry count and returns batch to pending`() {
		val batch = ProblemUploadBatch(
			teacherId = java.util.UUID.randomUUID(),
			sourceType = UploadSourceType.PAGE_IMAGE,
			parseStatus = ParseStatus.FAILED,
			retryCount = 2,
		)

		ProblemUploadBatchRetryPolicy.retry(batch, hasNarrowReviewScope = false)

		assertEquals(ParseStatus.PENDING, batch.parseStatus)
		assertEquals(3, batch.retryCount)
	}

	@Test
	fun `retry from needs review requires a narrow review scope`() {
		val batch = ProblemUploadBatch(
			teacherId = java.util.UUID.randomUUID(),
			sourceType = UploadSourceType.PAGE_IMAGE,
			parseStatus = ParseStatus.NEEDS_REVIEW,
			retryCount = 1,
		)

		ProblemUploadBatchRetryPolicy.retry(batch, hasNarrowReviewScope = true)

		assertEquals(ParseStatus.PENDING, batch.parseStatus)
		assertEquals(2, batch.retryCount)
	}

	@Test
	fun `retry is rejected from non retryable statuses`() {
		ParseStatus.entries
			.filterNot { it == ParseStatus.FAILED || it == ParseStatus.NEEDS_REVIEW }
			.forEach { status ->
				val batch = ProblemUploadBatch(
					teacherId = java.util.UUID.randomUUID(),
					sourceType = UploadSourceType.PAGE_IMAGE,
					parseStatus = status,
				)

				assertFailsWith<IllegalStateException> {
					ProblemUploadBatchRetryPolicy.retry(batch, hasNarrowReviewScope = false)
				}
			}
	}

	@Test
	fun `needs review retry without a narrow scope is rejected`() {
		val batch = ProblemUploadBatch(
			teacherId = java.util.UUID.randomUUID(),
			sourceType = UploadSourceType.PAGE_IMAGE,
			parseStatus = ParseStatus.NEEDS_REVIEW,
		)

		assertFailsWith<IllegalStateException> {
			ProblemUploadBatchRetryPolicy.retry(batch, hasNarrowReviewScope = false)
		}
	}
}

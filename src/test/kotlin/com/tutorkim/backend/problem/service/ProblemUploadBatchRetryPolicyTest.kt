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

			ProblemUploadBatchRetryPolicy.retry(batch)

			assertEquals(ParseStatus.PENDING, batch.parseStatus)
			assertEquals(3, batch.retryCount)
		}

	@Test
	fun `retry is allowed only from failed status`() {
			ParseStatus.entries
				.filterNot { it == ParseStatus.FAILED }
				.forEach { status ->
					val batch = ProblemUploadBatch(
						teacherId = java.util.UUID.randomUUID(),
						sourceType = UploadSourceType.PAGE_IMAGE,
						parseStatus = status,
					)

				assertFailsWith<IllegalStateException> {
					ProblemUploadBatchRetryPolicy.retry(batch)
				}
			}
	}
}

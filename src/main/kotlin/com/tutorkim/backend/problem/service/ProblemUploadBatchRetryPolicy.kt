package com.tutorkim.backend.problem.service

import com.tutorkim.backend.problem.entity.ParseStatus
import com.tutorkim.backend.problem.entity.ProblemUploadBatch

object ProblemUploadBatchRetryPolicy {
	/**
	 * A retry returns the batch to PENDING instead of PROCESSING so the parser queue
	 * can claim it through the same path as a first-time upload.
	 */
	fun retry(
		batch: ProblemUploadBatch,
		hasNarrowReviewScope: Boolean,
	): ProblemUploadBatch {
		when (batch.parseStatus) {
			ParseStatus.FAILED -> Unit
			ParseStatus.NEEDS_REVIEW -> check(hasNarrowReviewScope) {
				"needs-review retry requires low-confidence or temporary-problem scope"
			}
			else -> error("problem upload batch retry is allowed only from retryable statuses")
		}

		batch.retryCount += 1
		batch.parseStatus = ParseStatus.PENDING
		batch.parseError = null
		return batch
	}
}

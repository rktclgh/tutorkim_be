package com.tutorkim.backend.assignment

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AssignmentAvailabilityPolicyTest {

	private val policy = AssignmentAvailabilityPolicy()
	private val now = Instant.parse("2026-05-19T12:00:00Z")

	@Test
	fun `expired is true only when dueAt is before current time`() {
		assertTrue(policy.isExpired(Instant.parse("2026-05-19T11:59:59Z"), now))
		assertFalse(policy.isExpired(now, now))
		assertFalse(policy.isExpired(Instant.parse("2026-05-19T12:00:01Z"), now))
		assertFalse(policy.isExpired(null, now))
	}

	@Test
	fun `published assignment can be solved when not expired and submission is not submitted`() {
		val assignment = AssignmentAvailability(
			status = AssignmentStatus.PUBLISHED,
			dueAt = Instant.parse("2026-05-19T12:00:01Z"),
			submissionStatus = SubmissionStatus.NOT_SUBMITTED,
		)

		assertTrue(policy.canSolve(assignment, now))
		assertTrue(policy.canSolve(assignment.copy(submissionStatus = null), now))
	}

	@Test
	fun `canSolve blocks drafts closed expired submitted and late submitted assignments`() {
		val available = AssignmentAvailability(
			status = AssignmentStatus.PUBLISHED,
			dueAt = Instant.parse("2026-05-19T12:00:01Z"),
			submissionStatus = SubmissionStatus.NOT_SUBMITTED,
		)

		assertFalse(policy.canSolve(available.copy(status = AssignmentStatus.DRAFT), now))
		assertFalse(policy.canSolve(available.copy(status = AssignmentStatus.CLOSED), now))
		assertFalse(policy.canSolve(available.copy(dueAt = Instant.parse("2026-05-19T11:59:59Z")), now))
		assertFalse(policy.canSolve(available.copy(submissionStatus = SubmissionStatus.SUBMITTED), now))
		assertFalse(policy.canSolve(available.copy(submissionStatus = SubmissionStatus.LATE_SUBMITTED), now))
	}
}

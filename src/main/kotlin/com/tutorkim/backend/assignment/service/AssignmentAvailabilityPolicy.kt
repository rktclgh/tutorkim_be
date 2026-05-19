package com.tutorkim.backend.assignment.service

import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.entity.SubmissionStatus
import java.time.Instant

data class AssignmentAvailability(
	val status: AssignmentStatus,
	val dueAt: Instant?,
	val submissionStatus: SubmissionStatus?,
)

class AssignmentAvailabilityPolicy {

	fun isExpired(dueAt: Instant?, now: Instant): Boolean =
		dueAt?.isBefore(now) ?: false

	fun canSolve(availability: AssignmentAvailability, now: Instant): Boolean =
		availability.status == AssignmentStatus.PUBLISHED &&
			!isExpired(availability.dueAt, now) &&
			availability.submissionStatus !in setOf(
				SubmissionStatus.SUBMITTED,
				SubmissionStatus.LATE_SUBMITTED,
			)
}

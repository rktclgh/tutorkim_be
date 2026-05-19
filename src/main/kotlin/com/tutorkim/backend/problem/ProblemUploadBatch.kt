package com.tutorkim.backend.problem

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class UploadSourceType {
	PAGE_IMAGE,
	PROBLEM_IMAGE,
	TEXT,
	ANSWER_IMAGE,
	EXPLANATION_IMAGE,
	PDF,
}

enum class ParseStatus {
	PENDING,
	PROCESSING,
	NEEDS_REVIEW,
	REVIEWED,
	FAILED,
}

@Entity
@Table(name = "problem_upload_batches")
class ProblemUploadBatch(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	var id: UUID? = null,

	@Column(name = "teacher_id", nullable = false)
	var teacherId: UUID,

	@Column(name = "organization_id")
	var organizationId: UUID? = null,

	@Column(name = "title", length = 150)
	var title: String? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 30)
	var sourceType: UploadSourceType,

	@Enumerated(EnumType.STRING)
	@Column(name = "parse_status", nullable = false, length = 30)
	var parseStatus: ParseStatus = ParseStatus.PENDING,

	@Column(name = "parse_model", length = 100)
	var parseModel: String? = null,

	@Column(name = "parse_error", columnDefinition = "text")
	var parseError: String? = null,

	@Column(name = "retry_count", nullable = false)
	var retryCount: Int = 0,

	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant = Instant.now(),

	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant = Instant.now(),
) {
	@PreUpdate
	fun touchUpdatedAt() {
		updatedAt = Instant.now()
	}
}

package com.tutorkim.backend.problem.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import java.math.BigDecimal
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
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "source_type", nullable = false, columnDefinition = "upload_source_type")
	var sourceType: UploadSourceType,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "parse_status", nullable = false, columnDefinition = "parse_status")
	var parseStatus: ParseStatus = ParseStatus.PENDING,

	@Column(name = "pipeline_version", length = 80)
	var pipelineVersion: String? = null,

	@Column(name = "deterministic_coverage_rate", columnDefinition = "numeric")
	var deterministicCoverageRate: BigDecimal? = null,

	@Column(name = "hermes_review_rate", columnDefinition = "numeric")
	var hermesReviewRate: BigDecimal? = null,

	@Column(name = "hermes_targeted_repair_rate", columnDefinition = "numeric")
	var hermesTargetedRepairRate: BigDecimal? = null,

	@Column(name = "average_confidence", columnDefinition = "numeric")
	var averageConfidence: BigDecimal? = null,

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

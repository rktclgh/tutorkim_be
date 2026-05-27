package com.tutorkim.backend.problem.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcType
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

enum class ProblemExplanationSourceType {
	TEACHER_SOLUTION_IMAGE,
	TEACHER_TEXT,
	AI_GENERATED,
}

@Entity
@Table(
	name = "problem_explanations",
	uniqueConstraints = [
		UniqueConstraint(name = "problem_explanations_order_unique", columnNames = ["problem_id", "sort_order"]),
	],
)
class ProblemExplanation(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	var id: UUID? = null,

	@Column(name = "problem_id", nullable = false)
	var problemId: UUID,

	@Column(name = "sort_order", nullable = false)
	var sortOrder: Int,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "block_type", nullable = false, columnDefinition = "block_type")
	var blockType: ProblemBlockType = ProblemBlockType.EXPLANATION,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "source_type", nullable = false, columnDefinition = "explanation_source_type")
	var sourceType: ProblemExplanationSourceType = ProblemExplanationSourceType.TEACHER_SOLUTION_IMAGE,

	@Column(name = "text_content", columnDefinition = "text")
	var textContent: String? = null,

	@Column(name = "latex_content", columnDefinition = "text")
	var latexContent: String? = null,

	@Column(name = "file_asset_id")
	var fileAssetId: UUID? = null,

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
	var metadata: Map<String, Any?> = emptyMap(),

	@Column(name = "visible_to_student", nullable = false)
	var visibleToStudent: Boolean = true,

	@Column(name = "created_from_question_id")
	var createdFromQuestionId: UUID? = null,

	@Column(name = "created_by")
	var createdBy: UUID? = null,

	@Column(name = "archived_at")
	var archivedAt: Instant? = null,

	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant = Instant.now(),

	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant = Instant.now(),
) {
	@PrePersist
	@PreUpdate
	fun validateAndTouch() {
		require(textContent != null || latexContent != null || fileAssetId != null) {
			"Problem explanation requires text, latex, or file content."
		}
		updatedAt = Instant.now()
	}
}

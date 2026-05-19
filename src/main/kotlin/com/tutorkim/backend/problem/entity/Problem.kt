package com.tutorkim.backend.problem.entity

import com.tutorkim.backend.problem.service.ProblemAnswerPolicy
import com.tutorkim.backend.problem.service.ProblemAnswerSpec
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class ProblemAnswerType {
	SINGLE_CHOICE,
	MULTIPLE_CHOICE,
	NUMERIC,
}

@Entity
@Table(
	name = "problems",
	indexes = [
		Index(name = "problems_owner_subject_idx", columnList = "owner_teacher_id,subject_id"),
		Index(name = "problems_labels_idx", columnList = "label_depth1_id,label_depth2_id,label_depth3_id,label_depth4_id"),
	],
)
class Problem(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	var id: UUID? = null,

	@Column(name = "owner_teacher_id", nullable = false)
	var ownerTeacherId: UUID,

	@Column(name = "organization_id")
	var organizationId: UUID? = null,

	@Column(name = "subject_id", nullable = false)
	var subjectId: UUID,

	@Column(name = "source_batch_id")
	var sourceBatchId: UUID? = null,

	@Column(name = "title", length = 150)
	var title: String? = null,

	@Column(name = "original_problem_number", length = 50)
	var originalProblemNumber: String? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "answer_type", nullable = false, length = 30)
	var answerType: ProblemAnswerType,

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "correct_choice_numbers")
	var correctChoiceNumbers: List<Short>? = null,

	@Column(name = "correct_numeric_answer", precision = 20, scale = 6)
	var correctNumericAnswer: BigDecimal? = null,

	@Column(name = "difficulty", nullable = false)
	var difficulty: Int,

	@Column(name = "label_depth1_id")
	var labelDepth1Id: UUID? = null,

	@Column(name = "label_depth2_id")
	var labelDepth2Id: UUID? = null,

	@Column(name = "label_depth3_id")
	var labelDepth3Id: UUID? = null,

	@Column(name = "label_depth4_id")
	var labelDepth4Id: UUID? = null,

	@Column(name = "has_explanation", nullable = false)
	var hasExplanation: Boolean = false,

	@Enumerated(EnumType.STRING)
	@Column(name = "parse_status", nullable = false, length = 30)
	var parseStatus: ParseStatus = ParseStatus.NEEDS_REVIEW,

	@Column(name = "reviewed_at")
	var reviewedAt: Instant? = null,

	@Column(name = "archived_at")
	var archivedAt: Instant? = null,

	@Column(name = "archived_by")
	var archivedBy: UUID? = null,

	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant = Instant.now(),

	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant = Instant.now(),

	@Column(name = "deleted_at")
	var deletedAt: Instant? = null,
) {
	@PrePersist
	@PreUpdate
	fun validateAndTouch() {
		ProblemAnswerPolicy.validate(
			ProblemAnswerSpec(
				answerType = answerType,
				choiceAnswers = correctChoiceNumbers.orEmpty().map { it.toInt() },
				numericAnswer = correctNumericAnswer,
				difficulty = difficulty,
			),
		)
		updatedAt = Instant.now()
	}
}

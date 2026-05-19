package com.tutorkim.backend.content.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
	name = "curriculum_nodes",
	indexes = [
		Index(name = "curriculum_nodes_subject_depth_idx", columnList = "subject_id,depth"),
		Index(name = "curriculum_nodes_parent_idx", columnList = "parent_id"),
	],
)
class CurriculumNode(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	var id: UUID? = null,

	@Column(name = "subject_id", nullable = false)
	var subjectId: UUID,

	@Column(name = "parent_id")
	var parentId: UUID? = null,

	@Column(name = "depth", nullable = false)
	var depth: Short,

	@Column(name = "name", nullable = false, length = 120)
	var name: String,

	@Column(name = "owner_teacher_id")
	var ownerTeacherId: UUID? = null,

	@Column(name = "organization_id")
	var organizationId: UUID? = null,

	@Column(name = "is_system", nullable = false)
	var system: Boolean = false,

	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant = Instant.now(),

	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant = Instant.now(),
) {
	@PrePersist
	@PreUpdate
	fun validateAndTouch() {
		require(depth in 1..4) {
			"Curriculum depth must be between 1 and 4."
		}
		require(system || ownerTeacherId != null || organizationId != null) {
			"Custom curriculum nodes require an owner teacher or organization."
		}
		updatedAt = Instant.now()
	}
}

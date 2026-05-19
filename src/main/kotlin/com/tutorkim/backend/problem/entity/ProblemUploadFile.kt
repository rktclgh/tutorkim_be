package com.tutorkim.backend.problem.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "problem_upload_files")
class ProblemUploadFile(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	var id: UUID? = null,

	@Column(name = "batch_id", nullable = false)
	var batchId: UUID,

	@Column(name = "file_asset_id", nullable = false)
	var fileAssetId: UUID,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "source_type", nullable = false, columnDefinition = "upload_source_type")
	var sourceType: UploadSourceType,

	@Column(name = "page_number")
	var pageNumber: Int? = null,

	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant = Instant.now(),
)

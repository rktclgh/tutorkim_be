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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class IngestionStageType {
	PDF_TEXT_EXTRACTION,
	PDF_BOX_EXTRACTION,
	OCR,
	LAYOUT_SEGMENTATION,
	SEMANTIC_GROUPING,
	PROBLEM_BOUNDARY_DETECTION,
	ANSWER_MAPPING,
	HERMES_VISUAL_SEMANTIC_REVIEW,
	HERMES_TARGETED_REPAIR,
	REVIEW_TASK_GENERATION,
}

enum class IngestionStageStatus {
	PENDING,
	RUNNING,
	SUCCEEDED,
	PARTIAL,
	FAILED,
}

@Entity
@Table(name = "document_ingestion_stage_runs")
class DocumentIngestionStageRun(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	var id: UUID? = null,

	@Column(name = "batch_id", nullable = false)
	var batchId: UUID,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "stage_type", nullable = false, columnDefinition = "ingestion_stage_type")
	var stageType: IngestionStageType,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "status", nullable = false, columnDefinition = "ingestion_stage_status")
	var status: IngestionStageStatus = IngestionStageStatus.PENDING,

	@Column(name = "engine_name", length = 120)
	var engineName: String? = null,

	@Column(name = "confidence", columnDefinition = "numeric")
	var confidence: BigDecimal? = null,

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "input_artifact_ids", nullable = false)
	var inputArtifactIds: List<UUID> = emptyList(),

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "output_json", nullable = false, columnDefinition = "jsonb")
	var outputJson: Map<String, Any?> = emptyMap(),

	@Column(name = "error_message", columnDefinition = "text")
	var errorMessage: String? = null,

	@Column(name = "started_at")
	var startedAt: Instant? = null,

	@Column(name = "completed_at")
	var completedAt: Instant? = null,

	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "document_ingestion_artifacts")
class DocumentIngestionArtifact(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	var id: UUID? = null,

	@Column(name = "batch_id", nullable = false)
	var batchId: UUID,

	@Column(name = "upload_file_id")
	var uploadFileId: UUID? = null,

	@Column(name = "stage_run_id")
	var stageRunId: UUID? = null,

	@Column(name = "artifact_type", nullable = false, length = 80)
	var artifactType: String,

	@Column(name = "page_number")
	var pageNumber: Int? = null,

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "bounding_box", columnDefinition = "jsonb")
	var boundingBox: Map<String, Any?>? = null,

	@Column(name = "text_content", columnDefinition = "text")
	var textContent: String? = null,

	@Column(name = "latex_content", columnDefinition = "text")
	var latexContent: String? = null,

	@Column(name = "file_asset_id")
	var fileAssetId: UUID? = null,

	@Column(name = "confidence", columnDefinition = "numeric")
	var confidence: BigDecimal? = null,

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
	var metadata: Map<String, Any?> = emptyMap(),

	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant = Instant.now(),
)

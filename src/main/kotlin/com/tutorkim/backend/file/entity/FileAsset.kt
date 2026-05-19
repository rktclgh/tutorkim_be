package com.tutorkim.backend.file.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "file_assets")
class FileAsset(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	var id: UUID? = null,

	@Column(name = "owner_user_id", nullable = false)
	var ownerUserId: UUID,

	@Column(name = "organization_id")
	var organizationId: UUID? = null,

	@Column(name = "storage_key", nullable = false, columnDefinition = "text")
	var storageKey: String,

	@Column(name = "original_filename", length = 255)
	var originalFilename: String? = null,

	@Column(name = "content_type", length = 100)
	var contentType: String? = null,

	@Column(name = "size_bytes")
	var sizeBytes: Long? = null,

	@Column(name = "checksum_sha256", length = 64)
	var checksumSha256: String? = null,

	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant = Instant.now(),
)

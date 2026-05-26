package com.tutorkim.backend.file.repository

import com.tutorkim.backend.file.entity.FileAsset
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FileAssetRepository : JpaRepository<FileAsset, UUID> {
	fun findByStorageKey(storageKey: String): FileAsset?

	fun findByIdAndOwnerUserId(
		id: UUID,
		ownerUserId: UUID,
	): FileAsset?
}

package com.tutorkim.backend.file

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FileAssetRepository : JpaRepository<FileAsset, UUID> {
	fun findByStorageKey(storageKey: String): FileAsset?
}

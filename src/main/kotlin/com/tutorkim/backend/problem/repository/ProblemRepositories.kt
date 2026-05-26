package com.tutorkim.backend.problem.repository

import com.tutorkim.backend.problem.entity.ParseStatus
import com.tutorkim.backend.problem.entity.DocumentIngestionArtifact
import com.tutorkim.backend.problem.entity.DocumentIngestionStageRun
import com.tutorkim.backend.problem.entity.Problem
import com.tutorkim.backend.problem.entity.ProblemBlock
import com.tutorkim.backend.problem.entity.ProblemExplanation
import com.tutorkim.backend.problem.entity.ProblemUploadBatch
import com.tutorkim.backend.problem.entity.ProblemUploadFile
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ProblemUploadBatchRepository : JpaRepository<ProblemUploadBatch, UUID> {
	fun findByParseStatus(parseStatus: ParseStatus): List<ProblemUploadBatch>

	fun findByIdAndTeacherId(
		id: UUID,
		teacherId: UUID,
	): ProblemUploadBatch?

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(
		"""
		select batch
		from ProblemUploadBatch batch
		where batch.id = :id
		  and batch.teacherId = :teacherId
		""",
	)
	fun findByIdAndTeacherIdForUpdate(
		@Param("id") id: UUID,
		@Param("teacherId") teacherId: UUID,
	): ProblemUploadBatch?
}

interface ProblemUploadFileRepository : JpaRepository<ProblemUploadFile, UUID> {
	fun findByBatchIdOrderByCreatedAtAsc(batchId: UUID): List<ProblemUploadFile>

	fun existsByBatchIdAndFileAssetId(
		batchId: UUID,
		fileAssetId: UUID,
	): Boolean
}

interface DocumentIngestionStageRunRepository : JpaRepository<DocumentIngestionStageRun, UUID> {
	fun findByBatchIdOrderByCreatedAtAsc(batchId: UUID): List<DocumentIngestionStageRun>
}

interface DocumentIngestionArtifactRepository : JpaRepository<DocumentIngestionArtifact, UUID> {
	fun findByBatchIdOrderByCreatedAtAsc(batchId: UUID): List<DocumentIngestionArtifact>

	fun findByBatchIdAndArtifactTypeOrderByCreatedAtAsc(
		batchId: UUID,
		artifactType: String,
	): List<DocumentIngestionArtifact>
}

interface ProblemRepository : JpaRepository<Problem, UUID> {
	@Query(
		"""
		select problem
		from Problem problem
		where :labelId in (
		  problem.labelDepth1Id,
		  problem.labelDepth2Id,
		  problem.labelDepth3Id,
		  problem.labelDepth4Id
		)
		""",
	)
	fun findByAnyLabelId(@Param("labelId") labelId: UUID): List<Problem>

	@Query(
		"""
		select problem
		from Problem problem
		where problem.ownerTeacherId = :ownerTeacherId
		  and problem.subjectId = :subjectId
		  and problem.archivedAt is null
		  and problem.deletedAt is null
		  and (
		    problem.labelDepth1Id = :labelId
		    or problem.labelDepth2Id = :labelId
		    or problem.labelDepth3Id = :labelId
		    or problem.labelDepth4Id = :labelId
		  )
		""",
	)
	fun findActiveByOwnerSubjectAndLabel(
		@Param("ownerTeacherId") ownerTeacherId: UUID,
		@Param("subjectId") subjectId: UUID,
		@Param("labelId") labelId: UUID,
	): List<Problem>
}

interface ProblemBlockRepository : JpaRepository<ProblemBlock, UUID> {
	fun findByProblemIdOrderBySortOrderAsc(problemId: UUID): List<ProblemBlock>
}

interface ProblemExplanationRepository : JpaRepository<ProblemExplanation, UUID> {
	fun findByProblemIdOrderBySortOrderAsc(problemId: UUID): List<ProblemExplanation>
}

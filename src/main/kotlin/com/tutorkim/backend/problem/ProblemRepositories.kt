package com.tutorkim.backend.problem

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ProblemUploadBatchRepository : JpaRepository<ProblemUploadBatch, UUID> {
	fun findByParseStatus(parseStatus: ParseStatus): List<ProblemUploadBatch>
}

interface ProblemUploadFileRepository : JpaRepository<ProblemUploadFile, UUID> {
	fun findByBatchIdOrderByCreatedAtAsc(batchId: UUID): List<ProblemUploadFile>
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

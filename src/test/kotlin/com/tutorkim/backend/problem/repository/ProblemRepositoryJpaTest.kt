package com.tutorkim.backend.problem.repository

import com.tutorkim.backend.content.entity.CurriculumNode
import com.tutorkim.backend.content.repository.CurriculumNodeRepository
import com.tutorkim.backend.file.entity.FileAsset
import com.tutorkim.backend.file.repository.FileAssetRepository
import com.tutorkim.backend.problem.entity.DocumentIngestionArtifact
import com.tutorkim.backend.problem.entity.DocumentIngestionStageRun
import com.tutorkim.backend.problem.entity.IngestionStageStatus
import com.tutorkim.backend.problem.entity.IngestionStageType
import com.tutorkim.backend.problem.entity.ParseStatus
import com.tutorkim.backend.problem.entity.Problem
import com.tutorkim.backend.problem.entity.ProblemAnswerType
import com.tutorkim.backend.problem.entity.ProblemBlock
import com.tutorkim.backend.problem.entity.ProblemBlockType
import com.tutorkim.backend.problem.entity.ProblemExplanation
import com.tutorkim.backend.problem.entity.ProblemExplanationSourceType
import com.tutorkim.backend.problem.entity.ProblemUploadBatch
import com.tutorkim.backend.problem.entity.ProblemUploadFile
import com.tutorkim.backend.problem.entity.UploadSourceType
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.InvalidDataAccessApiUsageException
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

@DataJpaTest
class ProblemRepositoryJpaTest @Autowired constructor(
	private val curriculumNodeRepository: CurriculumNodeRepository,
	private val fileAssetRepository: FileAssetRepository,
	private val uploadBatchRepository: ProblemUploadBatchRepository,
	private val uploadFileRepository: ProblemUploadFileRepository,
	private val stageRunRepository: DocumentIngestionStageRunRepository,
	private val ingestionArtifactRepository: DocumentIngestionArtifactRepository,
	private val problemRepository: ProblemRepository,
	private val problemBlockRepository: ProblemBlockRepository,
	private val problemExplanationRepository: ProblemExplanationRepository,
	private val entityManager: EntityManager,
) {

	@Test
	fun `repositories persist learning content and problem records using DDL-aligned columns`() {
		val teacherId = UUID.randomUUID()
		val ownerUserId = UUID.randomUUID()
		val subjectId = UUID.randomUUID()
		val curriculumNode = curriculumNodeRepository.save(
			CurriculumNode(
				subjectId = subjectId,
				depth = 3,
				name = "Quadratic equations",
				ownerTeacherId = teacherId,
			),
		)
		val fileAsset = fileAssetRepository.save(
			FileAsset(
				ownerUserId = ownerUserId,
				storageKey = "problem-upload/worksheet.png",
				originalFilename = "worksheet.png",
				contentType = "image/png",
				sizeBytes = 4096,
			),
		)
		val batch = uploadBatchRepository.save(
			ProblemUploadBatch(
				teacherId = teacherId,
				title = "확통 조합 프린트 1",
				sourceType = UploadSourceType.PAGE_IMAGE,
				pipelineVersion = "semantic-first-v1",
				deterministicCoverageRate = BigDecimal("0.88"),
				hermesReviewRate = BigDecimal.ONE,
				hermesTargetedRepairRate = BigDecimal("0.12"),
				averageConfidence = BigDecimal("0.91"),
			),
		)
		val uploadFile = uploadFileRepository.save(
			ProblemUploadFile(
				batchId = batch.id!!,
				fileAssetId = fileAsset.id!!,
				sourceType = UploadSourceType.PAGE_IMAGE,
				pageNumber = 1,
			),
		)
		val problem = problemRepository.saveAndFlush(
			Problem(
				ownerTeacherId = teacherId,
				subjectId = subjectId,
				sourceBatchId = batch.id,
				answerType = ProblemAnswerType.NUMERIC,
				correctNumericAnswer = BigDecimal("7"),
				difficulty = 3.toShort(),
				labelDepth3Id = curriculumNode.id,
			),
		)
		val sourceArtifact = ingestionArtifactRepository.save(
			DocumentIngestionArtifact(
				batchId = batch.id!!,
				uploadFileId = uploadFile.id,
				artifactType = "PDF_TEXT_BLOCK",
				pageNumber = 1,
				textContent = "Find x when 2x + 1 = 15.",
				confidence = BigDecimal("0.97"),
				metadata = mapOf(
					"extractor" to "PDF_TEXT_EXTRACTION",
					"readingOrder" to listOf(1, 2, 3),
				),
			),
		)
		val stageRun = stageRunRepository.save(
			DocumentIngestionStageRun(
				batchId = batch.id!!,
				stageType = IngestionStageType.HERMES_VISUAL_SEMANTIC_REVIEW,
				status = IngestionStageStatus.SUCCEEDED,
				engineName = "hermes-agent-gateway:gpt-5.4-mini",
				confidence = BigDecimal("0.90"),
				inputArtifactIds = listOf(sourceArtifact.id!!),
				outputJson = mapOf(
					"reviewScope" to "ALL_PAGES_WITH_STRUCTURED_EVIDENCE",
					"riskSignals" to listOf("LOW_CONFIDENCE_ANSWER_MAPPING"),
					"candidate" to mapOf("temporaryProblemId" to "tmp-1"),
				),
			),
		)
		val ingestionArtifact = ingestionArtifactRepository.save(
			DocumentIngestionArtifact(
				batchId = batch.id!!,
				uploadFileId = uploadFile.id,
				stageRunId = stageRun.id,
				artifactType = "PROBLEM_CROP",
				pageNumber = 1,
				boundingBox = mapOf(
					"x" to 10,
					"y" to 20,
					"width" to 300,
					"height" to 160,
				),
				fileAssetId = uploadFile.fileAssetId,
				confidence = BigDecimal("0.90"),
				metadata = mapOf(
					"extractor" to "HERMES_VISUAL_SEMANTIC_REVIEW",
					"provenance" to mapOf("artifactId" to sourceArtifact.id.toString()),
				),
			),
		)

		problemBlockRepository.save(
			ProblemBlock(
				problemId = problem.id!!,
				sortOrder = 1,
				blockType = ProblemBlockType.DIAGRAM_IMAGE,
				fileAssetId = uploadFile.fileAssetId,
				metadata = mapOf("crop" to mapOf("x" to 10, "y" to 20, "width" to 300, "height" to 160)),
			),
		)
		problemExplanationRepository.save(
			ProblemExplanation(
				problemId = problem.id!!,
				sortOrder = 1,
				sourceType = ProblemExplanationSourceType.TEACHER_TEXT,
				textContent = "Substitute and simplify.",
				visibleToStudent = true,
				createdBy = teacherId,
			),
		)
		entityManager.flush()
		entityManager.clear()

		assertNotNull(problem.id)
		assertNotNull(stageRun.id)
		assertNotNull(ingestionArtifact.id)
		val savedBatch = uploadBatchRepository.findById(batch.id!!).orElseThrow()
		val savedStageRun = stageRunRepository.findByBatchIdOrderByCreatedAtAsc(batch.id!!).single()
		val savedSourceArtifact = ingestionArtifactRepository
			.findByBatchIdAndArtifactTypeOrderByCreatedAtAsc(batch.id!!, "PDF_TEXT_BLOCK")
			.single()
		val savedProblemCrop = ingestionArtifactRepository
			.findByBatchIdAndArtifactTypeOrderByCreatedAtAsc(batch.id!!, "PROBLEM_CROP")
			.single()
		assertEquals("semantic-first-v1", savedBatch.pipelineVersion)
		assertEquals(IngestionStageType.HERMES_VISUAL_SEMANTIC_REVIEW, savedStageRun.stageType)
		assertEquals(listOf(sourceArtifact.id), savedStageRun.inputArtifactIds)
		assertEquals("ALL_PAGES_WITH_STRUCTURED_EVIDENCE", savedStageRun.outputJson["reviewScope"])
		assertEquals("PDF_TEXT_EXTRACTION", savedSourceArtifact.metadata["extractor"])
		assertEquals("PROBLEM_CROP", savedProblemCrop.artifactType)
		assertEquals(mapOf("x" to 10, "y" to 20, "width" to 300, "height" to 160), savedProblemCrop.boundingBox)
		assertEquals(1, problemRepository.findByAnyLabelId(curriculumNode.id!!).size)
		assertEquals(1, uploadFileRepository.findByBatchIdOrderByCreatedAtAsc(batch.id!!).size)
		assertEquals(ProblemBlockType.DIAGRAM_IMAGE, problemBlockRepository.findByProblemIdOrderBySortOrderAsc(problem.id!!).single().blockType)
		assertEquals("Substitute and simplify.", problemExplanationRepository.findByProblemIdOrderBySortOrderAsc(problem.id!!).single().textContent)
		assertEquals(1, problemRepository.findActiveByOwnerSubjectAndLabel(teacherId, subjectId, curriculumNode.id!!).size)
	}

	@Test
	fun `invalid problem answer shape fails before persist`() {
		assertThrows<InvalidDataAccessApiUsageException> {
			problemRepository.saveAndFlush(
				Problem(
					ownerTeacherId = UUID.randomUUID(),
					subjectId = UUID.randomUUID(),
					answerType = ProblemAnswerType.SINGLE_CHOICE,
					correctChoiceNumbers = listOf(1, 2).map { it.toShort() },
					difficulty = 3.toShort(),
				),
			)
		}
	}

	@Test
	fun `invalid problem block without content fails before persist`() {
		assertThrows<InvalidDataAccessApiUsageException> {
			problemBlockRepository.saveAndFlush(
				ProblemBlock(
					problemId = UUID.randomUUID(),
					sortOrder = 1,
					blockType = ProblemBlockType.TEXT,
				),
			)
		}
	}

	@Test
	fun `invalid curriculum owner scope fails before persist`() {
		assertThrows<InvalidDataAccessApiUsageException> {
			curriculumNodeRepository.saveAndFlush(
				CurriculumNode(
					subjectId = UUID.randomUUID(),
					depth = 2,
					name = "ownerless custom node",
				),
			)
		}
	}
}

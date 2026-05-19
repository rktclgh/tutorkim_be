package com.tutorkim.backend.problem.repository

import com.tutorkim.backend.content.entity.CurriculumNode
import com.tutorkim.backend.content.repository.CurriculumNodeRepository
import com.tutorkim.backend.file.entity.FileAsset
import com.tutorkim.backend.file.repository.FileAssetRepository
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
	private val problemRepository: ProblemRepository,
	private val problemBlockRepository: ProblemBlockRepository,
	private val problemExplanationRepository: ProblemExplanationRepository,
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

		assertNotNull(problem.id)
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

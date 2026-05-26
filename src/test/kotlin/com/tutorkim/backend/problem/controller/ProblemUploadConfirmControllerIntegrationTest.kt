package com.tutorkim.backend.problem.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tutorkim.backend.content.entity.CurriculumNode
import com.tutorkim.backend.content.repository.CurriculumNodeRepository
import com.tutorkim.backend.file.entity.FileAsset
import com.tutorkim.backend.file.repository.FileAssetRepository
import com.tutorkim.backend.identity.entity.User
import com.tutorkim.backend.identity.entity.UserRole
import com.tutorkim.backend.identity.repository.UserRepository
import com.tutorkim.backend.problem.entity.ParseStatus
import com.tutorkim.backend.problem.entity.ProblemUploadBatch
import com.tutorkim.backend.problem.entity.UploadSourceType
import com.tutorkim.backend.problem.repository.ProblemBlockRepository
import com.tutorkim.backend.problem.repository.ProblemExplanationRepository
import com.tutorkim.backend.problem.repository.ProblemRepository
import com.tutorkim.backend.problem.repository.ProblemUploadBatchRepository
import com.tutorkim.backend.student.entity.StudentProfile
import com.tutorkim.backend.student.entity.TeacherProfile
import com.tutorkim.backend.student.repository.StudentProfileRepository
import com.tutorkim.backend.student.repository.TeacherProfileRepository
import com.tutorkim.backend.subject.entity.Subject
import com.tutorkim.backend.subject.repository.SubjectRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ProblemUploadConfirmControllerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val teacherProfileRepository: TeacherProfileRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val subjectRepository: SubjectRepository,
    private val curriculumNodeRepository: CurriculumNodeRepository,
    private val fileAssetRepository: FileAssetRepository,
    private val uploadBatchRepository: ProblemUploadBatchRepository,
    private val problemRepository: ProblemRepository,
    private val problemBlockRepository: ProblemBlockRepository,
    private val problemExplanationRepository: ProblemExplanationRepository,
) {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `teacher can confirm parsed problems into the problem bank`() {
        val fixture = createTeacherFixture()
        val subject = createSubject()
        val labels = createLabelFixture(subject.id!!)
        val diagramAsset = createFileAsset(fixture.teacherUser.id!!, "diagram.png")
        val solutionAsset = createFileAsset(fixture.teacherUser.id!!, "solution.png")
        val batchId = createBatch(fixture.teacherProfile.id!!, ParseStatus.NEEDS_REVIEW)

        val response = mockMvc.post("/api/v1/problem-upload-batches/$batchId/confirm") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = confirmBody(
                labels = labels,
                diagramAssetId = diagramAsset.id!!,
                solutionAssetId = solutionAsset.id!!,
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.batchId") { value(batchId.toString()) }
            jsonPath("$.data.parseStatus") { value("REVIEWED") }
            jsonPath("$.data.createdProblems.length()") { value(1) }
            jsonPath("$.data.createdProblems[0].temporaryProblemId") { value("tmp-1") }
            jsonPath("$.data.createdProblems[0].problemId") { exists() }
        }.andReturn().response.contentAsString

        val problemId = UUID.fromString(
            objectMapper.readTree(response)
                .path("data")
                .path("createdProblems")
                .first()
                .path("problemId")
                .asText(),
        )
        val savedProblem = problemRepository.findById(problemId).orElseThrow()
        assertThat(savedProblem.ownerTeacherId).isEqualTo(fixture.teacherProfile.id!!)
        assertThat(savedProblem.subjectId).isEqualTo(subject.id!!)
        assertThat(savedProblem.sourceBatchId).isEqualTo(batchId)
        assertThat(savedProblem.parseStatus).isEqualTo(ParseStatus.REVIEWED)
        assertThat(savedProblem.hasExplanation).isTrue()
        assertThat(uploadBatchRepository.findById(batchId).orElseThrow().parseStatus).isEqualTo(ParseStatus.REVIEWED)
        assertThat(problemBlockRepository.findByProblemIdOrderBySortOrderAsc(problemId)).hasSize(3)
        val explanations = problemExplanationRepository.findByProblemIdOrderBySortOrderAsc(problemId)
        assertThat(explanations).hasSize(1)
        assertThat(explanations.first().createdBy).isEqualTo(fixture.teacherUser.id!!)
    }

    @Test
    fun `confirm rejects active batches and prevents duplicate confirmation`() {
        val fixture = createTeacherFixture()
        val subject = createSubject()
        val labels = createLabelFixture(subject.id!!)
        val diagramAsset = createFileAsset(fixture.teacherUser.id!!, "diagram.png")
        val solutionAsset = createFileAsset(fixture.teacherUser.id!!, "solution.png")
        val processingBatchId = createBatch(fixture.teacherProfile.id!!, ParseStatus.PROCESSING)

        mockMvc.post("/api/v1/problem-upload-batches/$processingBatchId/confirm") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = confirmBody(labels, diagramAsset.id!!, solutionAsset.id!!)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("CONFLICT") }
            jsonPath("$.error.message") { value("검토가 필요한 배치만 문제은행에 확정할 수 있습니다.") }
        }

        val reviewBatchId = createBatch(fixture.teacherProfile.id!!, ParseStatus.NEEDS_REVIEW)
        mockMvc.post("/api/v1/problem-upload-batches/$reviewBatchId/confirm") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = confirmBody(labels, diagramAsset.id!!, solutionAsset.id!!)
        }.andExpect {
            status { isOk() }
        }

        mockMvc.post("/api/v1/problem-upload-batches/$reviewBatchId/confirm") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = confirmBody(labels, diagramAsset.id!!, solutionAsset.id!!)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("CONFLICT") }
        }
    }

    @Test
    fun `confirm enforces owner role label subject and file ownership`() {
        val owner = createTeacherFixture(displayName = "소유 선생")
        val other = createTeacherFixture(displayName = "다른 선생")
        val studentUser = userRepository.save(
            User(
                email = "student-${UUID.randomUUID()}@example.com",
                name = "학생",
                role = UserRole.STUDENT,
            ),
        )
        studentProfileRepository.save(StudentProfile(user = studentUser, name = "학생"))
        val subject = createSubject()
        val otherSubject = createSubject()
        val labels = createLabelFixture(subject.id!!)
        val mixedSubjectLabels = labels.copy(depth3Id = createLabelFixture(otherSubject.id!!).depth3Id)
        val ownerAsset = createFileAsset(owner.teacherUser.id!!, "diagram.png")
        val otherAsset = createFileAsset(other.teacherUser.id!!, "solution.png")
        val batchId = createBatch(owner.teacherProfile.id!!, ParseStatus.NEEDS_REVIEW)

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/confirm") {
            with(user(other.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = confirmBody(labels, ownerAsset.id!!, ownerAsset.id!!)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/confirm") {
            with(user(studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = confirmBody(labels, ownerAsset.id!!, ownerAsset.id!!)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/confirm") {
            with(user(owner.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = confirmBody(mixedSubjectLabels, ownerAsset.id!!, ownerAsset.id!!)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("문제 라벨은 같은 과목에 속해야 합니다.") }
        }

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/confirm") {
            with(user(owner.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = confirmBody(labels, ownerAsset.id!!, otherAsset.id!!)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("첨부 파일을 찾을 수 없습니다.") }
        }
    }

    @Test
    fun `confirm validates label hierarchy and explanation block content`() {
        val fixture = createTeacherFixture()
        val subject = createSubject()
        val labels = createLabelFixture(subject.id!!)
        val detachedDepth3 = curriculumNodeRepository.save(
            CurriculumNode(subjectId = subject.id!!, depth = 3, name = "계층 밖 단원", system = true),
        )
        val asset = createFileAsset(fixture.teacherUser.id!!, "diagram.png")
        val batchId = createBatch(fixture.teacherProfile.id!!, ParseStatus.NEEDS_REVIEW)

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/confirm") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = confirmBody(
                labels = labels.copy(depth3Id = detachedDepth3.id!!),
                diagramAssetId = asset.id!!,
                solutionAssetId = asset.id!!,
            )
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("문제 라벨 계층이 올바르지 않습니다.") }
        }

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/confirm") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = confirmBody(
                labels = labels,
                diagramAssetId = asset.id!!,
                solutionAssetId = asset.id!!,
                problemOverrides = mapOf(
                    "explanationBlocks" to listOf(
                        mapOf(
                            "type" to "EXPLANATION",
                            "visibleToStudent" to true,
                        ),
                    ),
                ),
            )
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("풀이 블록에는 text 또는 latex가 필요합니다.") }
        }
    }

    @Test
    fun `confirm rejects foreign file assets on text blocks`() {
        val owner = createTeacherFixture(displayName = "소유 선생")
        val other = createTeacherFixture(displayName = "다른 선생")
        val subject = createSubject()
        val labels = createLabelFixture(subject.id!!)
        val ownerAsset = createFileAsset(owner.teacherUser.id!!, "diagram.png")
        val otherAsset = createFileAsset(other.teacherUser.id!!, "foreign.png")
        val batchId = createBatch(owner.teacherProfile.id!!, ParseStatus.NEEDS_REVIEW)
        val problemCountBefore = problemRepository.count()

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/confirm") {
            with(user(owner.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = confirmBody(
                labels = labels,
                diagramAssetId = ownerAsset.id!!,
                solutionAssetId = ownerAsset.id!!,
                problemOverrides = mapOf(
                    "blocks" to listOf(
                        mapOf(
                            "type" to "TEXT",
                            "text" to "파일이 붙으면 안 되는 텍스트 블록",
                            "fileAssetId" to otherAsset.id!!.toString(),
                        ),
                    ),
                ),
            )
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("파일 첨부는 이미지 블록에만 사용할 수 있습니다.") }
        }

        assertThat(problemRepository.count()).isEqualTo(problemCountBefore)
        assertThat(uploadBatchRepository.findById(batchId).orElseThrow().parseStatus).isEqualTo(ParseStatus.NEEDS_REVIEW)
    }

    @Test
    fun `confirm rolls back all created rows when a later problem is invalid`() {
        val fixture = createTeacherFixture()
        val subject = createSubject()
        val labels = createLabelFixture(subject.id!!)
        val asset = createFileAsset(fixture.teacherUser.id!!, "diagram.png")
        val batchId = createBatch(fixture.teacherProfile.id!!, ParseStatus.NEEDS_REVIEW)
        val problemCountBefore = problemRepository.count()
        val blockCountBefore = problemBlockRepository.count()
        val explanationCountBefore = problemExplanationRepository.count()

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/confirm") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "problems" to listOf(
                        validProblem(
                            labels = labels,
                            diagramAssetId = asset.id!!,
                            solutionAssetId = asset.id!!,
                            temporaryProblemId = "tmp-valid",
                        ),
                        validProblem(
                            labels = labels,
                            diagramAssetId = asset.id!!,
                            solutionAssetId = asset.id!!,
                            temporaryProblemId = "tmp-invalid",
                        ) + mapOf(
                            "answerType" to "MULTIPLE_CHOICE",
                            "correctChoiceNumbers" to listOf(2),
                        ),
                    ),
                ),
            )
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("문제 답안 형식이 올바르지 않습니다.") }
        }

        assertThat(problemRepository.count()).isEqualTo(problemCountBefore)
        assertThat(problemBlockRepository.count()).isEqualTo(blockCountBefore)
        assertThat(problemExplanationRepository.count()).isEqualTo(explanationCountBefore)
        assertThat(uploadBatchRepository.findById(batchId).orElseThrow().parseStatus).isEqualTo(ParseStatus.NEEDS_REVIEW)
    }

    private fun confirmBody(
        labels: LabelFixture,
        diagramAssetId: UUID,
        solutionAssetId: UUID,
        problemOverrides: Map<String, Any?> = emptyMap(),
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "problems" to listOf(
                    validProblem(
                        labels = labels,
                        diagramAssetId = diagramAssetId,
                        solutionAssetId = solutionAssetId,
                    ) + problemOverrides,
                ),
            ),
        )

    private fun validProblem(
        labels: LabelFixture,
        diagramAssetId: UUID,
        solutionAssetId: UUID,
        temporaryProblemId: String = "tmp-1",
    ): Map<String, Any?> =
        mapOf(
            "temporaryProblemId" to temporaryProblemId,
            "answerType" to "SINGLE_CHOICE",
            "correctChoiceNumbers" to listOf(3),
            "difficulty" to 3,
            "labelDepth1Id" to labels.depth1Id.toString(),
            "labelDepth2Id" to labels.depth2Id.toString(),
            "labelDepth3Id" to labels.depth3Id.toString(),
            "blocks" to listOf(
                mapOf(
                    "type" to "TEXT",
                    "text" to "다음 조건을 만족하는 경우의 수를 구하시오.",
                    "provenance" to mapOf("extractor" to "pdfbox", "confidence" to 0.96),
                ),
                mapOf(
                    "type" to "MATH",
                    "latex" to "y = a\\\\sin(bx)",
                    "provenance" to mapOf("extractor" to "ocr-math", "confidence" to 0.89),
                ),
                mapOf(
                    "type" to "DIAGRAM_IMAGE",
                    "fileAssetId" to diagramAssetId.toString(),
                    "metadata" to mapOf("crop" to mapOf("x" to 10, "y" to 20, "width" to 300, "height" to 160)),
                ),
            ),
            "teacherSolutionAssets" to listOf(
                mapOf(
                    "fileAssetId" to solutionAssetId.toString(),
                    "sourceType" to "TEACHER_SOLUTION_IMAGE",
                    "visibleToStudent" to true,
                    "note" to "선생이 직접 푼 풀이 사진",
                ),
            ),
        )

    private fun createBatch(
        teacherId: UUID,
        parseStatus: ParseStatus,
    ): UUID =
        uploadBatchRepository.save(
            ProblemUploadBatch(
                teacherId = teacherId,
                sourceType = UploadSourceType.PAGE_IMAGE,
                parseStatus = parseStatus,
            ),
        ).id!!

    private fun createSubject(): Subject =
        subjectRepository.save(
            Subject(
                code = "math-${UUID.randomUUID()}",
                name = "수학",
                active = true,
            ),
        )

    private fun createLabelFixture(subjectId: UUID): LabelFixture {
        val depth1 = curriculumNodeRepository.save(CurriculumNode(subjectId = subjectId, depth = 1, name = "대수", system = true))
        val depth2 = curriculumNodeRepository.save(CurriculumNode(subjectId = subjectId, parentId = depth1.id, depth = 2, name = "함수", system = true))
        val depth3 = curriculumNodeRepository.save(CurriculumNode(subjectId = subjectId, parentId = depth2.id, depth = 3, name = "삼각함수", system = true))
        return LabelFixture(depth1.id!!, depth2.id!!, depth3.id!!)
    }

    private fun createFileAsset(
        ownerUserId: UUID,
        filename: String,
    ): FileAsset =
        fileAssetRepository.save(
            FileAsset(
                ownerUserId = ownerUserId,
                storageKey = "problem-upload/${UUID.randomUUID()}-$filename",
                originalFilename = filename,
                contentType = "image/png",
                sizeBytes = 1024,
            ),
        )

    private fun createTeacherFixture(displayName: String = "김선생"): TeacherFixture {
        val teacherUser = userRepository.save(
            User(
                email = "teacher-${UUID.randomUUID()}@example.com",
                name = displayName,
                role = UserRole.TEACHER,
            ),
        )
        val teacherProfile = teacherProfileRepository.save(
            TeacherProfile(
                user = teacherUser,
                displayName = displayName,
            ),
        )
        return TeacherFixture(teacherUser, teacherProfile)
    }

    private data class TeacherFixture(
        val teacherUser: User,
        val teacherProfile: TeacherProfile,
    )

    private data class LabelFixture(
        val depth1Id: UUID,
        val depth2Id: UUID,
        val depth3Id: UUID,
    )
}

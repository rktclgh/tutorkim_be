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
import com.tutorkim.backend.problem.entity.Problem
import com.tutorkim.backend.problem.entity.ProblemAnswerType
import com.tutorkim.backend.problem.entity.ProblemBlock
import com.tutorkim.backend.problem.entity.ProblemBlockType
import com.tutorkim.backend.problem.entity.ProblemExplanation
import com.tutorkim.backend.problem.entity.ProblemExplanationSourceType
import com.tutorkim.backend.problem.repository.ProblemBlockRepository
import com.tutorkim.backend.problem.repository.ProblemExplanationRepository
import com.tutorkim.backend.problem.repository.ProblemRepository
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ProblemBankControllerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val teacherProfileRepository: TeacherProfileRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val subjectRepository: SubjectRepository,
    private val curriculumNodeRepository: CurriculumNodeRepository,
    private val fileAssetRepository: FileAssetRepository,
    private val problemRepository: ProblemRepository,
    private val problemBlockRepository: ProblemBlockRepository,
    private val problemExplanationRepository: ProblemExplanationRepository,
) {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `teacher lists own active problems with filters`() {
        val owner = createTeacherFixture("목록 선생")
        val other = createTeacherFixture("다른 선생")
        val subject = createSubject("수학")
        val otherSubject = createSubject("과학")
        val labels = createLabelFixture(subject.id!!)
        val otherLabels = createLabelFixture(otherSubject.id!!)
        val visibleProblem = createProblem(owner.teacherProfile.id!!, subject.id!!, labels, "보이는 문제")
        createProblem(owner.teacherProfile.id!!, otherSubject.id!!, otherLabels, "다른 과목 문제")
        createProblem(other.teacherProfile.id!!, subject.id!!, labels, "다른 선생 문제")
        createProblem(owner.teacherProfile.id!!, subject.id!!, labels, "보관된 문제", archivedAt = Instant.now())

        mockMvc.get("/api/v1/problems") {
            with(user(owner.teacherUser.id!!.toString()).roles("TEACHER"))
            param("subjectId", subject.id!!.toString())
            param("depth1Id", labels.depth1Id.toString())
            param("difficulty", "3")
            param("answerType", "SINGLE_CHOICE")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].id") { value(visibleProblem.id!!.toString()) }
            jsonPath("$.data[0].subjectId") { value(subject.id!!.toString()) }
            jsonPath("$.data[0].answerType") { value("SINGLE_CHOICE") }
            jsonPath("$.data[0].difficulty") { value(3) }
            jsonPath("$.data[0].blockPreview") { value("보이는 문제") }
            jsonPath("$.data[0].blockCount") { value(1) }
        }
    }

    @Test
    fun `teacher list rejects limit outside safe range`() {
        val owner = createTeacherFixture("목록 제한 선생")

        mockMvc.get("/api/v1/problems") {
            with(user(owner.teacherUser.id!!.toString()).roles("TEACHER"))
            param("limit", "201")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `teacher gets problem detail and ownership is enforced`() {
        val owner = createTeacherFixture("소유 선생")
        val other = createTeacherFixture("다른 선생")
        val studentUser = userRepository.save(
            User(
                email = "student-${UUID.randomUUID()}@example.com",
                name = "학생",
                role = UserRole.STUDENT,
            ),
        )
        studentProfileRepository.save(StudentProfile(user = studentUser, name = "학생"))
        val subject = createSubject("수학")
        val labels = createLabelFixture(subject.id!!)
        val solutionAsset = createFileAsset(owner.teacherUser.id!!, "solution.png")
        val problem = createProblem(owner.teacherProfile.id!!, subject.id!!, labels, "상세 문제", solutionAsset)

        mockMvc.get("/api/v1/problems/${problem.id}") {
            with(user(owner.teacherUser.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(problem.id!!.toString()) }
            jsonPath("$.data.correctChoiceNumbers[0]") { value(3) }
            jsonPath("$.data.blocks.length()") { value(1) }
            jsonPath("$.data.blocks[0].text") { value("상세 문제") }
            jsonPath("$.data.explanations.length()") { value(1) }
            jsonPath("$.data.explanations[0].fileAssetId") { value(solutionAsset.id!!.toString()) }
        }

        mockMvc.get("/api/v1/problems/${problem.id}") {
            with(user(other.teacherUser.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }

        mockMvc.get("/api/v1/problems/${problem.id}") {
            with(user(studentUser.id!!.toString()).roles("STUDENT"))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `teacher updates problem content with full block and explanation replacement`() {
        val owner = createTeacherFixture("수정 선생")
        val subject = createSubject("수학")
        val oldLabels = createLabelFixture(subject.id!!)
        val newLabels = createLabelFixture(subject.id!!)
        val oldAsset = createFileAsset(owner.teacherUser.id!!, "old-solution.png")
        val newAsset = createFileAsset(owner.teacherUser.id!!, "new-solution.png")
        val problem = createProblem(owner.teacherProfile.id!!, subject.id!!, oldLabels, "수정 전 문제", oldAsset)
        val oldExplanationId = problemExplanationRepository.findByProblemIdOrderBySortOrderAsc(problem.id!!).single().id!!

        mockMvc.patch("/api/v1/problems/${problem.id}") {
            with(user(owner.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = updateBody(newLabels, newAsset.id!!)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(problem.id!!.toString()) }
            jsonPath("$.data.answerType") { value("MULTIPLE_CHOICE") }
            jsonPath("$.data.correctChoiceNumbers.length()") { value(2) }
            jsonPath("$.data.difficulty") { value(4) }
            jsonPath("$.data.labelDepth1Id") { value(newLabels.depth1Id.toString()) }
            jsonPath("$.data.blocks.length()") { value(2) }
            jsonPath("$.data.explanations.length()") { value(2) }
        }

        val savedProblem = problemRepository.findById(problem.id!!).orElseThrow()
        assertThat(savedProblem.answerType).isEqualTo(ProblemAnswerType.MULTIPLE_CHOICE)
        assertThat(savedProblem.correctChoiceNumbers).containsExactly(2, 5)
        assertThat(savedProblem.labelDepth1Id).isEqualTo(newLabels.depth1Id)
        assertThat(savedProblem.hasExplanation).isTrue()
        val blocks = problemBlockRepository.findByProblemIdOrderBySortOrderAsc(problem.id!!)
        assertThat(blocks.map { it.textContent }).containsExactly("수정된 문제 본문", null)
        val allExplanations = problemExplanationRepository.findByProblemIdOrderBySortOrderAsc(problem.id!!)
        assertThat(allExplanations).hasSize(3)
        assertThat(allExplanations.first { it.id == oldExplanationId }.archivedAt).isNotNull()
        val activeExplanations = problemExplanationRepository.findByProblemIdAndArchivedAtIsNullOrderBySortOrderAsc(problem.id!!)
        assertThat(activeExplanations).hasSize(2)
        assertThat(activeExplanations.map { it.fileAssetId }).contains(newAsset.id!!)
    }

    @Test
    fun `update enforces problem and file ownership`() {
        val owner = createTeacherFixture("수정 소유 선생")
        val other = createTeacherFixture("수정 다른 선생")
        val studentUser = userRepository.save(
            User(
                email = "student-${UUID.randomUUID()}@example.com",
                name = "학생",
                role = UserRole.STUDENT,
            ),
        )
        studentProfileRepository.save(StudentProfile(user = studentUser, name = "학생"))
        val subject = createSubject("수학")
        val labels = createLabelFixture(subject.id!!)
        val ownerAsset = createFileAsset(owner.teacherUser.id!!, "owner.png")
        val otherAsset = createFileAsset(other.teacherUser.id!!, "other.png")
        val problem = createProblem(owner.teacherProfile.id!!, subject.id!!, labels, "권한 문제", ownerAsset)

        mockMvc.patch("/api/v1/problems/${problem.id}") {
            with(user(other.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = updateBody(labels, ownerAsset.id!!)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }

        mockMvc.patch("/api/v1/problems/${problem.id}") {
            with(user(studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = updateBody(labels, ownerAsset.id!!)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }

        mockMvc.patch("/api/v1/problems/${problem.id}") {
            with(user(owner.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                updateProblemMap(labels, ownerAsset.id!!) + mapOf(
                    "blocks" to listOf(
                        mapOf(
                            "type" to "DIAGRAM_IMAGE",
                            "fileAssetId" to otherAsset.id!!.toString(),
                        ),
                    ),
                ),
            )
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("첨부 파일을 찾을 수 없습니다.") }
        }

        mockMvc.patch("/api/v1/problems/${problem.id}") {
            with(user(owner.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = updateBody(labels, otherAsset.id!!)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("첨부 파일을 찾을 수 없습니다.") }
        }
    }

    @Test
    fun `update rejects file assets on non image body blocks`() {
        val owner = createTeacherFixture("검증 선생")
        val subject = createSubject("수학")
        val labels = createLabelFixture(subject.id!!)
        val asset = createFileAsset(owner.teacherUser.id!!, "solution.png")
        val problem = createProblem(owner.teacherProfile.id!!, subject.id!!, labels, "검증 문제", asset)

        mockMvc.patch("/api/v1/problems/${problem.id}") {
            with(user(owner.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                updateProblemMap(labels, asset.id!!) + mapOf(
                    "blocks" to listOf(
                        mapOf(
                            "type" to "TEXT",
                            "text" to "텍스트 블록",
                            "fileAssetId" to asset.id!!.toString(),
                        ),
                    ),
                ),
            )
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("파일 첨부는 이미지 블록에만 사용할 수 있습니다.") }
        }
    }

    private fun updateBody(
        labels: LabelFixture,
        solutionAssetId: UUID,
    ): String = objectMapper.writeValueAsString(updateProblemMap(labels, solutionAssetId))

    private fun updateProblemMap(
        labels: LabelFixture,
        solutionAssetId: UUID,
    ): Map<String, Any?> =
        mapOf(
            "answerType" to "MULTIPLE_CHOICE",
            "correctChoiceNumbers" to listOf(2, 5),
            "difficulty" to 4,
            "labelDepth1Id" to labels.depth1Id.toString(),
            "labelDepth2Id" to labels.depth2Id.toString(),
            "labelDepth3Id" to labels.depth3Id.toString(),
            "blocks" to listOf(
                mapOf(
                    "type" to "TEXT",
                    "text" to "수정된 문제 본문",
                ),
                mapOf(
                    "type" to "MATH",
                    "latex" to "x^2 + y^2 = 1",
                ),
            ),
            "explanationBlocks" to listOf(
                mapOf(
                    "type" to "EXPLANATION",
                    "text" to "선생 보충 풀이",
                    "visibleToStudent" to true,
                ),
            ),
            "teacherSolutionAssets" to listOf(
                mapOf(
                    "fileAssetId" to solutionAssetId.toString(),
                    "sourceType" to "TEACHER_SOLUTION_IMAGE",
                    "visibleToStudent" to true,
                    "note" to "새 풀이",
                ),
            ),
        )

    private fun createProblem(
        teacherId: UUID,
        subjectId: UUID,
        labels: LabelFixture,
        text: String,
        solutionAsset: FileAsset? = null,
        archivedAt: Instant? = null,
    ): Problem {
        val problem = problemRepository.saveAndFlush(
            Problem(
                ownerTeacherId = teacherId,
                subjectId = subjectId,
                answerType = ProblemAnswerType.SINGLE_CHOICE,
                correctChoiceNumbers = listOf(3.toShort()),
                difficulty = 3,
                labelDepth1Id = labels.depth1Id,
                labelDepth2Id = labels.depth2Id,
                labelDepth3Id = labels.depth3Id,
                hasExplanation = solutionAsset != null,
                parseStatus = ParseStatus.REVIEWED,
                reviewedAt = Instant.now(),
                archivedAt = archivedAt,
            ),
        )
        problemBlockRepository.save(
            ProblemBlock(
                problemId = problem.id!!,
                blockType = ProblemBlockType.TEXT,
                sortOrder = 1,
                textContent = text,
            ),
        )
        if (solutionAsset != null) {
            problemExplanationRepository.save(
                ProblemExplanation(
                    problemId = problem.id!!,
                    sortOrder = 1,
                    sourceType = ProblemExplanationSourceType.TEACHER_SOLUTION_IMAGE,
                    fileAssetId = solutionAsset.id!!,
                    metadata = mapOf("note" to "기존 풀이"),
                    visibleToStudent = true,
                ),
            )
        }
        return problem
    }

    private fun createSubject(name: String): Subject =
        subjectRepository.save(
            Subject(
                code = "subject-${UUID.randomUUID()}",
                name = name,
                active = true,
            ),
        )

    private fun createLabelFixture(subjectId: UUID): LabelFixture {
        val depth1 = curriculumNodeRepository.save(CurriculumNode(subjectId = subjectId, depth = 1, name = "대단원-${UUID.randomUUID()}", system = true))
        val depth2 = curriculumNodeRepository.save(CurriculumNode(subjectId = subjectId, parentId = depth1.id, depth = 2, name = "중단원", system = true))
        val depth3 = curriculumNodeRepository.save(CurriculumNode(subjectId = subjectId, parentId = depth2.id, depth = 3, name = "소단원", system = true))
        return LabelFixture(depth1.id!!, depth2.id!!, depth3.id!!)
    }

    private fun createFileAsset(
        ownerUserId: UUID,
        filename: String,
    ): FileAsset =
        fileAssetRepository.save(
            FileAsset(
                ownerUserId = ownerUserId,
                storageKey = "problem-bank/${UUID.randomUUID()}-$filename",
                originalFilename = filename,
                contentType = "image/png",
                sizeBytes = 1024,
            ),
        )

    private fun createTeacherFixture(displayName: String): TeacherFixture {
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

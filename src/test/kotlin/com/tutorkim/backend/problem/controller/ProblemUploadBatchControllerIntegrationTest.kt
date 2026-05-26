package com.tutorkim.backend.problem.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tutorkim.backend.file.repository.FileAssetRepository
import com.tutorkim.backend.identity.entity.User
import com.tutorkim.backend.identity.entity.UserRole
import com.tutorkim.backend.identity.repository.UserRepository
import com.tutorkim.backend.problem.entity.ParseStatus
import com.tutorkim.backend.problem.repository.DocumentIngestionStageRunRepository
import com.tutorkim.backend.problem.repository.ProblemUploadBatchRepository
import com.tutorkim.backend.problem.repository.ProblemUploadFileRepository
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
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ProblemUploadBatchControllerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val teacherProfileRepository: TeacherProfileRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val subjectRepository: SubjectRepository,
    private val fileAssetRepository: FileAssetRepository,
    private val uploadBatchRepository: ProblemUploadBatchRepository,
    private val uploadFileRepository: ProblemUploadFileRepository,
    private val stageRunRepository: DocumentIngestionStageRunRepository,
) {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `teacher can create a problem upload batch attach a file and start parsing`() {
        val fixture = createTeacherFixture()
        val subject = createSubject()
        val uploadFileId = requestFileUpload(fixture.teacherUser.id!!)
        val batchId = createUploadBatch(fixture.teacherUser.id!!)
        val attachBody = objectMapper.writeValueAsString(
            mapOf(
                "fileAssetId" to uploadFileId.toString(),
                "sourceType" to "PAGE_IMAGE",
                "pageNumber" to 1,
            ),
        )

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/files") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = attachBody
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.fileAssetId") { value(uploadFileId.toString()) }
            jsonPath("$.data.sourceType") { value("PAGE_IMAGE") }
            jsonPath("$.data.pageNumber") { value(1) }
        }

        val parseBody = objectMapper.writeValueAsString(
            mapOf(
                "parseMode" to "SEMANTIC_FIRST",
                "subjectId" to subject.id!!.toString(),
                "pipelineVersion" to "semantic-first-v1",
                "deterministicStages" to listOf(
                    "PDF_TEXT_EXTRACTION",
                    "OCR",
                    "HERMES_VISUAL_SEMANTIC_REVIEW",
                ),
                "hermesReview" to mapOf(
                    "gateway" to "HERMES",
                    "enabled" to true,
                    "model" to "gpt-5.4-mini",
                    "reviewScope" to "ALL_PAGES_WITH_STRUCTURED_EVIDENCE",
                    "targetedRepairThreshold" to 0.86,
                    "targetedRepairScope" to "RISKY_OR_CONFLICTED_ITEMS",
                ),
            ),
        )

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/parse") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = parseBody
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.batchId") { value(batchId.toString()) }
            jsonPath("$.data.parseStatus") { value("PROCESSING") }
            jsonPath("$.data.pipelineVersion") { value("semantic-first-v1") }
            jsonPath("$.data.stageRuns.length()") { value(3) }
            jsonPath("$.data.stageRuns[0].stageType") { value("PDF_TEXT_EXTRACTION") }
            jsonPath("$.data.stageRuns[0].status") { value("PENDING") }
            jsonPath("$.data.stageRuns[2].engineName") { value("hermes-agent-gateway:gpt-5.4-mini") }
        }

        mockMvc.get("/api/v1/problem-upload-batches/$batchId") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.batchId") { value(batchId.toString()) }
            jsonPath("$.data.files.length()") { value(1) }
            jsonPath("$.data.stageRuns.length()") { value(3) }
        }

        val savedBatch = uploadBatchRepository.findById(batchId).orElseThrow()
        assertThat(savedBatch.parseStatus).isEqualTo(ParseStatus.PROCESSING)
        assertThat(uploadFileRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).hasSize(1)
        assertThat(stageRunRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).hasSize(3)
    }

    @Test
    fun `student role cannot access problem upload write APIs`() {
        val studentUser = userRepository.save(
            User(
                email = "student-${UUID.randomUUID()}@example.com",
                name = "학생",
                role = UserRole.STUDENT,
            ),
        )
        studentProfileRepository.save(StudentProfile(user = studentUser, name = "학생"))
        val body = objectMapper.writeValueAsString(
            mapOf(
                "title" to "확통 조합 프린트 1",
                "sourceType" to "PAGE_IMAGE",
            ),
        )

        mockMvc.post("/api/v1/problem-upload-batches") {
            with(user(studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }

        mockMvc.post("/api/v1/files/upload-url") {
            with(user(studentUser.id!!.toString()).roles("STUDENT"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "filename" to "page-1.png",
                    "contentType" to "image/png",
                    "sizeBytes" to 1234567,
                ),
            )
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `file upload reservation rejects unsupported type oversized file and malformed principals`() {
        val fixture = createTeacherFixture()

        mockMvc.post("/api/v1/files/upload-url") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "filename" to "page-1.txt",
                    "contentType" to "text/plain",
                    "sizeBytes" to 1024,
                ),
            )
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("지원하지 않는 파일 형식입니다.") }
        }

        mockMvc.post("/api/v1/files/upload-url") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "filename" to "huge.pdf",
                    "contentType" to "application/pdf",
                    "sizeBytes" to 50L * 1024L * 1024L + 1L,
                ),
            )
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("업로드 파일은 50MB를 초과할 수 없습니다.") }
        }

        mockMvc.post("/api/v1/files/upload-url") {
            with(user("not-a-uuid").roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "filename" to "page-1.png",
                    "contentType" to "image/png",
                    "sizeBytes" to 1024,
                ),
            )
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `teacher cannot attach another teachers file asset`() {
        val owner = createTeacherFixture(displayName = "소유 선생")
        val other = createTeacherFixture(displayName = "다른 선생")
        val ownerFileId = requestFileUpload(owner.teacherUser.id!!)
        val otherBatchId = createUploadBatch(other.teacherUser.id!!)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "fileAssetId" to ownerFileId.toString(),
                "sourceType" to "PAGE_IMAGE",
                "pageNumber" to 1,
            ),
        )

        mockMvc.post("/api/v1/problem-upload-batches/$otherBatchId/files") {
            with(user(other.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun `teacher cannot read attach or parse another teachers upload batch`() {
        val owner = createTeacherFixture(displayName = "소유 선생")
        val other = createTeacherFixture(displayName = "다른 선생")
        val subject = createSubject()
        val ownerBatchId = createUploadBatch(owner.teacherUser.id!!)
        val otherFileAssetId = requestFileUpload(other.teacherUser.id!!)

        mockMvc.get("/api/v1/problem-upload-batches/$ownerBatchId") {
            with(user(other.teacherUser.id!!.toString()).roles("TEACHER"))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }

        mockMvc.post("/api/v1/problem-upload-batches/$ownerBatchId/files") {
            with(user(other.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "fileAssetId" to otherFileAssetId.toString(),
                    "sourceType" to "PAGE_IMAGE",
                    "pageNumber" to 1,
                ),
            )
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }

        mockMvc.post("/api/v1/problem-upload-batches/$ownerBatchId/parse") {
            with(user(other.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = parseBody(subject.id!!)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun `parsing requires at least one attached file`() {
        val fixture = createTeacherFixture()
        val subject = createSubject()
        val batchId = createUploadBatch(fixture.teacherUser.id!!)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "parseMode" to "SEMANTIC_FIRST",
                "subjectId" to subject.id!!.toString(),
                "pipelineVersion" to "semantic-first-v1",
                "deterministicStages" to listOf("OCR"),
            ),
        )

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/parse") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("CONFLICT") }
            jsonPath("$.error.message") { value("파싱을 시작하려면 업로드 파일이 필요합니다.") }
        }
    }

    @Test
    fun `parsing rejects inactive subjects and repeated starts`() {
        val fixture = createTeacherFixture()
        val inactiveSubject = createSubject(active = false)
        val activeSubject = createSubject()
        val fileAssetId = requestFileUpload(fixture.teacherUser.id!!)
        val batchId = createUploadBatch(fixture.teacherUser.id!!)
        attachFile(
            teacherUserId = fixture.teacherUser.id!!,
            batchId = batchId,
            fileAssetId = fileAssetId,
        )

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/parse") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = parseBody(inactiveSubject.id!!)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("과목을 찾을 수 없습니다.") }
        }

        startParsing(
            teacherUserId = fixture.teacherUser.id!!,
            batchId = batchId,
            subjectId = activeSubject.id!!,
        )

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/parse") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = parseBody(activeSubject.id!!)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("CONFLICT") }
            jsonPath("$.error.message") { value("현재 배치 상태에서는 파싱을 시작할 수 없습니다.") }
        }
    }

    @Test
    fun `teacher cannot attach the same file twice to one batch`() {
        val fixture = createTeacherFixture()
        val fileAssetId = requestFileUpload(fixture.teacherUser.id!!)
        val batchId = createUploadBatch(fixture.teacherUser.id!!)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "fileAssetId" to fileAssetId.toString(),
                "sourceType" to "PAGE_IMAGE",
                "pageNumber" to 1,
            ),
        )

        repeat(2) { attempt ->
            mockMvc.post("/api/v1/problem-upload-batches/$batchId/files") {
                with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                if (attempt == 0) {
                    status { isOk() }
                } else {
                    status { isConflict() }
                    jsonPath("$.error.code") { value("CONFLICT") }
                }
            }
        }
    }

    @Test
    fun `teacher cannot attach a file after parsing has started`() {
        val fixture = createTeacherFixture()
        val subject = createSubject()
        val firstFileAssetId = requestFileUpload(fixture.teacherUser.id!!)
        val secondFileAssetId = requestFileUpload(fixture.teacherUser.id!!)
        val batchId = createUploadBatch(fixture.teacherUser.id!!)
        attachFile(
            teacherUserId = fixture.teacherUser.id!!,
            batchId = batchId,
            fileAssetId = firstFileAssetId,
        )
        startParsing(
            teacherUserId = fixture.teacherUser.id!!,
            batchId = batchId,
            subjectId = subject.id!!,
        )

        mockMvc.post("/api/v1/problem-upload-batches/$batchId/files") {
            with(user(fixture.teacherUser.id!!.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "fileAssetId" to secondFileAssetId.toString(),
                    "sourceType" to "PAGE_IMAGE",
                    "pageNumber" to 2,
                ),
            )
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("CONFLICT") }
            jsonPath("$.error.message") { value("현재 배치 상태에서는 파일을 첨부할 수 없습니다.") }
        }
    }

    private fun requestFileUpload(teacherUserId: UUID): UUID {
        val response = mockMvc.post("/api/v1/files/upload-url") {
            with(user(teacherUserId.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "filename" to "page-1.png",
                    "contentType" to "image/png",
                    "sizeBytes" to 1234567,
                ),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.fileAssetId") { exists() }
            jsonPath("$.data.storageKey") { exists() }
            jsonPath("$.data.filename") { value("page-1.png") }
        }.andReturn().response.contentAsString

        val fileAssetId = objectMapper.readTree(response).path("data").path("fileAssetId").asText()
        val savedAsset = fileAssetRepository.findById(UUID.fromString(fileAssetId)).orElseThrow()
        assertThat(savedAsset.ownerUserId).isEqualTo(teacherUserId)
        assertThat(savedAsset.storageKey).startsWith("problem-upload/")
        return savedAsset.id!!
    }

    private fun createUploadBatch(teacherUserId: UUID): UUID {
        val body = objectMapper.writeValueAsString(
            mapOf(
                "title" to "확통 조합 프린트 1",
                "sourceType" to "PAGE_IMAGE",
            ),
        )

        val response = mockMvc.post("/api/v1/problem-upload-batches") {
            with(user(teacherUserId.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.batchId") { exists() }
            jsonPath("$.data.title") { value("확통 조합 프린트 1") }
            jsonPath("$.data.sourceType") { value("PAGE_IMAGE") }
            jsonPath("$.data.parseStatus") { value("PENDING") }
        }.andReturn().response.contentAsString

        return UUID.fromString(objectMapper.readTree(response).path("data").path("batchId").asText())
    }

    private fun parseBody(subjectId: UUID): String =
        objectMapper.writeValueAsString(
            mapOf(
                "parseMode" to "SEMANTIC_FIRST",
                "subjectId" to subjectId.toString(),
                "pipelineVersion" to "semantic-first-v1",
                "deterministicStages" to listOf("OCR"),
            ),
        )

    private fun attachFile(
        teacherUserId: UUID,
        batchId: UUID,
        fileAssetId: UUID,
    ) {
        mockMvc.post("/api/v1/problem-upload-batches/$batchId/files") {
            with(user(teacherUserId.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "fileAssetId" to fileAssetId.toString(),
                    "sourceType" to "PAGE_IMAGE",
                    "pageNumber" to 1,
                ),
            )
        }.andExpect {
            status { isOk() }
        }
    }

    private fun startParsing(
        teacherUserId: UUID,
        batchId: UUID,
        subjectId: UUID,
    ) {
        mockMvc.post("/api/v1/problem-upload-batches/$batchId/parse") {
            with(user(teacherUserId.toString()).roles("TEACHER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "parseMode" to "SEMANTIC_FIRST",
                    "subjectId" to subjectId.toString(),
                    "pipelineVersion" to "semantic-first-v1",
                    "deterministicStages" to listOf("OCR"),
                ),
            )
        }.andExpect {
            status { isOk() }
        }
    }

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

        return TeacherFixture(
            teacherUser = teacherUser,
            teacherProfile = teacherProfile,
        )
    }

    private fun createSubject(): Subject =
        createSubject(active = true)

    private fun createSubject(active: Boolean): Subject =
        subjectRepository.save(
            Subject(
                code = "math-${UUID.randomUUID()}",
                name = "수학",
                active = active,
            ),
        )

    private data class TeacherFixture(
        val teacherUser: User,
        val teacherProfile: TeacherProfile,
    )
}

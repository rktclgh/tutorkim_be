package com.tutorkim.backend.subject.controller

import com.tutorkim.backend.content.entity.CurriculumNode
import com.tutorkim.backend.content.repository.CurriculumNodeRepository
import com.tutorkim.backend.subject.entity.Subject
import com.tutorkim.backend.subject.repository.SubjectRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class SubjectCurriculumControllerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val subjectRepository: SubjectRepository,
    private val curriculumNodeRepository: CurriculumNodeRepository,
) {
    @Test
    fun `authenticated user can list active subjects ordered by name`() {
        clearSubjectData()
        val suffix = System.nanoTime()
        subjectRepository.save(Subject(code = "SCI-$suffix", name = "과학"))
        subjectRepository.save(Subject(code = "MATH-$suffix", name = "수학"))
        subjectRepository.save(Subject(code = "OLD-$suffix", name = "비활성과목", active = false))

        mockMvc.get("/api/v1/subjects") {
            with(user("11111111-1111-1111-1111-111111111111").roles("TEACHER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(2) }
            jsonPath("$.data[0].name") { value("과학") }
            jsonPath("$.data[1].name") { value("수학") }
            jsonPath("$.data[?(@.code == 'OLD-$suffix')]") { isEmpty() }
        }
    }

    @Test
    fun `authenticated user can read curriculum tree for active subject`() {
        clearSubjectData()
        val suffix = System.nanoTime()
        val subject = subjectRepository.save(Subject(code = "MATH-TREE-$suffix", name = "수학"))
        val depth1 = curriculumNodeRepository.save(
            CurriculumNode(
                subjectId = subject.id!!,
                depth = 1,
                name = "확률과 통계",
                system = true,
            ),
        )
        val depth2 = curriculumNodeRepository.save(
            CurriculumNode(
                subjectId = subject.id!!,
                parentId = depth1.id!!,
                depth = 2,
                name = "경우의 수",
                system = true,
            ),
        )
        curriculumNodeRepository.save(
            CurriculumNode(
                subjectId = subject.id!!,
                parentId = depth2.id!!,
                depth = 3,
                name = "순열",
                system = true,
            ),
        )

        mockMvc.get("/api/v1/subjects/${subject.id}/curriculum") {
            with(user("22222222-2222-2222-2222-222222222222").roles("TEACHER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].name") { value("확률과 통계") }
            jsonPath("$.data[0].depth") { value(1) }
            jsonPath("$.data[0].children[0].name") { value("경우의 수") }
            jsonPath("$.data[0].children[0].children[0].name") { value("순열") }
        }
    }

    @Test
    fun `inactive subject curriculum returns not found`() {
        clearSubjectData()
        val suffix = System.nanoTime()
        val subject = subjectRepository.save(Subject(code = "INACTIVE-$suffix", name = "비활성", active = false))

        mockMvc.get("/api/v1/subjects/${subject.id}/curriculum") {
            with(user("33333333-3333-3333-3333-333333333333").roles("TEACHER"))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.message") { value("과목을 찾을 수 없습니다.") }
        }
    }

    private fun clearSubjectData() {
        curriculumNodeRepository.deleteAll()
        subjectRepository.deleteAll()
    }
}

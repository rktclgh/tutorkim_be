package com.tutorkim.backend.subject.service

import com.tutorkim.backend.content.entity.CurriculumNode
import com.tutorkim.backend.content.repository.CurriculumNodeRepository
import com.tutorkim.backend.subject.dto.CurriculumNodeResponse
import com.tutorkim.backend.subject.entity.Subject
import com.tutorkim.backend.subject.repository.SubjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

class SubjectNotFoundException(message: String) : RuntimeException(message)

@Service
class SubjectCurriculumService(
    private val subjectRepository: SubjectRepository,
    private val curriculumNodeRepository: CurriculumNodeRepository,
) {
    @Transactional(readOnly = true)
    fun listSubjects(): List<Subject> =
        subjectRepository.findByActiveTrueOrderByNameAsc()

    @Transactional(readOnly = true)
    fun getCurriculumTree(subjectId: UUID): List<CurriculumNodeResponse> {
        subjectRepository.findByIdAndActiveTrue(subjectId)
            ?: throw SubjectNotFoundException("Subject not found: $subjectId")

        val nodesByParentId = curriculumNodeRepository.findBySubjectIdAndSystemTrueOrderByDepthAscNameAsc(subjectId)
            .groupBy { it.parentId }

        return buildChildren(parentId = null, nodesByParentId = nodesByParentId)
    }

    private fun buildChildren(
        parentId: UUID?,
        nodesByParentId: Map<UUID?, List<CurriculumNode>>,
    ): List<CurriculumNodeResponse> =
        nodesByParentId[parentId]
            .orEmpty()
            .sortedWith(compareBy<CurriculumNode> { it.depth }.thenBy { it.name })
            .map { node ->
                CurriculumNodeResponse.from(
                    node = node,
                    children = buildChildren(node.id, nodesByParentId),
                )
            }
}

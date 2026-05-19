package com.tutorkim.backend.content.repository

import com.tutorkim.backend.content.entity.CurriculumNode
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CurriculumNodeRepository : JpaRepository<CurriculumNode, UUID> {
	fun findBySubjectIdAndDepthOrderByNameAsc(subjectId: UUID, depth: Short): List<CurriculumNode>

	fun findByParentIdOrderByNameAsc(parentId: UUID?): List<CurriculumNode>
}

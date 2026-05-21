package com.tutorkim.backend.subject.dto

import com.tutorkim.backend.content.entity.CurriculumNode
import com.tutorkim.backend.subject.entity.Subject
import java.util.UUID

data class SubjectResponse(
    val id: UUID,
    val code: String,
    val name: String,
) {
    companion object {
        fun from(subject: Subject): SubjectResponse =
            SubjectResponse(
                id = subject.id!!,
                code = subject.code,
                name = subject.name,
            )
    }
}

data class CurriculumNodeResponse(
    val id: UUID,
    val subjectId: UUID,
    val parentId: UUID?,
    val depth: Short,
    val name: String,
    val system: Boolean,
    val children: List<CurriculumNodeResponse>,
) {
    companion object {
        fun from(
            node: CurriculumNode,
            children: List<CurriculumNodeResponse>,
        ): CurriculumNodeResponse =
            CurriculumNodeResponse(
                id = node.id!!,
                subjectId = node.subjectId,
                parentId = node.parentId,
                depth = node.depth,
                name = node.name,
                system = node.system,
                children = children,
            )
    }
}

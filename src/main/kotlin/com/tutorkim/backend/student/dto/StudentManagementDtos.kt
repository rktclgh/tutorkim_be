package com.tutorkim.backend.student.dto

import com.tutorkim.backend.student.service.StudentRelationshipView
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class UpdateStudentSubjectsRequest(
    @field:Size(min = 1)
    val subjectIds: Set<UUID>,
    val primarySubjectId: UUID,
)

data class StudentRelationshipDetailResponse(
    val id: UUID,
    val student: StudentProfileResponse,
    val subjects: List<StudentSubjectResponse>,
    val defaultSubject: SubjectSummaryResponse?,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(view: StudentRelationshipView): StudentRelationshipDetailResponse =
            StudentRelationshipDetailResponse(
                id = view.relationship.id!!,
                student = StudentProfileResponse(
                    id = view.relationship.student.id!!,
                    name = view.relationship.student.name,
                    school = view.relationship.student.school,
                    grade = view.relationship.student.grade,
                    phone = view.relationship.student.phone,
                    parentPhone = view.relationship.student.parentPhone,
                ),
                subjects = view.subjects.map { subjectLink ->
                    StudentSubjectResponse(
                        id = subjectLink.subject.id!!,
                        code = subjectLink.subject.code,
                        name = subjectLink.subject.name,
                        primary = subjectLink.primary,
                    )
                },
                defaultSubject = view.relationship.defaultSubject?.let {
                    SubjectSummaryResponse(
                        id = it.id!!,
                        code = it.code,
                        name = it.name,
                    )
                },
                active = view.relationship.active,
                createdAt = view.relationship.createdAt,
                updatedAt = view.relationship.updatedAt,
            )
    }
}

data class StudentProfileResponse(
    val id: UUID,
    val name: String,
    val school: String?,
    val grade: String?,
    val phone: String?,
    val parentPhone: String?,
)

data class StudentSubjectResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val primary: Boolean,
)

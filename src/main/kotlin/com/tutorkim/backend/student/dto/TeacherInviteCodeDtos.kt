package com.tutorkim.backend.student.dto

import com.tutorkim.backend.student.entity.InviteCodeStatus
import com.tutorkim.backend.student.entity.TeacherInviteCode
import com.tutorkim.backend.student.entity.TeacherStudent
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreateTeacherInviteCodeRequest(
    @field:Min(1)
    @field:Max(168)
    val expiresInHours: Long = 24,
)

data class AddTeacherByInviteCodeRequest(
    @field:NotBlank
    val inviteCode: String,
)

data class TeacherInviteCodeResponse(
    val id: UUID,
    val code: String,
    val status: InviteCodeStatus,
    val expiresAt: Instant,
    val createdAt: Instant,
) {
    companion object {
        fun from(inviteCode: TeacherInviteCode): TeacherInviteCodeResponse =
            TeacherInviteCodeResponse(
                id = inviteCode.id!!,
                code = inviteCode.code,
                status = inviteCode.status,
                expiresAt = inviteCode.expiresAt,
                createdAt = inviteCode.createdAt,
            )
    }
}

data class TeacherStudentRelationshipResponse(
    val id: UUID,
    val teacher: TeacherSummaryResponse,
    val student: StudentSummaryResponse,
    val defaultSubject: SubjectSummaryResponse?,
    val active: Boolean,
) {
    companion object {
        fun from(relationship: TeacherStudent): TeacherStudentRelationshipResponse =
            TeacherStudentRelationshipResponse(
                id = relationship.id!!,
                teacher = TeacherSummaryResponse(
                    id = relationship.teacher.id!!,
                    displayName = relationship.teacher.displayName,
                ),
                student = StudentSummaryResponse(
                    id = relationship.student.id!!,
                    name = relationship.student.name,
                ),
                defaultSubject = relationship.defaultSubject?.let {
                    SubjectSummaryResponse(
                        id = it.id!!,
                        code = it.code,
                        name = it.name,
                    )
                },
                active = relationship.active,
            )
    }
}

data class TeacherSummaryResponse(
    val id: UUID,
    val displayName: String,
)

data class StudentSummaryResponse(
    val id: UUID,
    val name: String,
)

data class SubjectSummaryResponse(
    val id: UUID,
    val code: String,
    val name: String,
)

package com.tutorkim.backend.student.controller

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.common.web.API_PREFIX
import com.tutorkim.backend.common.web.CurrentUser
import com.tutorkim.backend.student.dto.AddTeacherByInviteCodeRequest
import com.tutorkim.backend.student.dto.CreateTeacherInviteCodeRequest
import com.tutorkim.backend.student.dto.TeacherInviteCodeResponse
import com.tutorkim.backend.student.dto.TeacherStudentRelationshipResponse
import com.tutorkim.backend.student.service.InviteCodeNotConsumableException
import com.tutorkim.backend.student.service.TeacherDefaultSubjectNotUniqueException
import com.tutorkim.backend.student.service.TeacherInviteCodeService
import com.tutorkim.backend.student.service.TeacherStudentAlreadyExistsException
import com.tutorkim.backend.student.service.TeacherStudentRelationshipNotFoundException
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

@RestController
@RequestMapping(API_PREFIX)
class TeacherInviteCodeController(
    private val teacherInviteCodeService: TeacherInviteCodeService,
    private val currentUser: CurrentUser,
) {
    @PostMapping("teacher/invite-codes")
    fun createTeacherInviteCode(
        authentication: Authentication,
        @Valid @RequestBody request: CreateTeacherInviteCodeRequest,
    ): TeacherInviteCodeResponse {
        val inviteCode = teacherInviteCodeService.createInviteCodeForTeacherUser(
            teacherUserId = currentUser.id(authentication),
            expiresIn = Duration.ofHours(request.expiresInHours),
        )
        return TeacherInviteCodeResponse.from(inviteCode)
    }

    @GetMapping("teacher/invite-codes/active")
    fun getActiveTeacherInviteCode(authentication: Authentication): TeacherInviteCodeResponse? =
        teacherInviteCodeService.findActiveCodeForTeacherUser(currentUser.id(authentication))
            ?.let(TeacherInviteCodeResponse::from)

    @DeleteMapping("teacher/invite-codes/{inviteCodeId}")
    fun revokeTeacherInviteCode(
        authentication: Authentication,
        @PathVariable inviteCodeId: UUID,
    ): ResponseEntity<Void> {
        try {
            teacherInviteCodeService.revokeInviteCodeForTeacherUser(
                teacherUserId = currentUser.id(authentication),
                inviteCodeId = inviteCodeId,
            )
        } catch (exception: InviteCodeNotConsumableException) {
            throw ApiException(ErrorCode.NOT_FOUND, "초대코드를 찾을 수 없습니다.", cause = exception)
        }
        return ResponseEntity.noContent().build()
    }

    @PostMapping("student/teachers")
    fun addTeacherByInviteCode(
        authentication: Authentication,
        @Valid @RequestBody request: AddTeacherByInviteCodeRequest,
    ): TeacherStudentRelationshipResponse {
        val relationship = try {
            teacherInviteCodeService.consumeInviteCodeForStudentUser(
                code = request.inviteCode.trim().uppercase(),
                studentUserId = currentUser.id(authentication),
            )
        } catch (exception: InviteCodeNotConsumableException) {
            throw ApiException(
                ErrorCode.INVALID_REQUEST,
                "초대코드가 유효하지 않거나 만료되었습니다.",
                cause = exception,
            )
        } catch (exception: TeacherStudentAlreadyExistsException) {
            throw ApiException(ErrorCode.CONFLICT, "이미 연결된 선생님입니다.", cause = exception)
        } catch (exception: TeacherDefaultSubjectNotUniqueException) {
            throw ApiException(ErrorCode.CONFLICT, "선생님 기본 과목 설정을 확인해 주세요.", cause = exception)
        }

        return TeacherStudentRelationshipResponse.from(relationship)
    }

    @DeleteMapping("students/{studentId}/teacher-relationship")
    fun deactivateTeacherStudentRelationship(
        authentication: Authentication,
        @PathVariable studentId: UUID,
    ): ResponseEntity<Void> {
        try {
            teacherInviteCodeService.deactivateRelationshipForTeacherUser(
                teacherUserId = currentUser.id(authentication),
                studentId = studentId,
            )
        } catch (exception: TeacherStudentRelationshipNotFoundException) {
            throw ApiException(ErrorCode.NOT_FOUND, "활성 학생 관계를 찾을 수 없습니다.", cause = exception)
        }

        return ResponseEntity.noContent().build()
    }
}

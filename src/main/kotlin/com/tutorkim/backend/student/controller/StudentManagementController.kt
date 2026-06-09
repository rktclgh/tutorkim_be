package com.tutorkim.backend.student.controller

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.common.web.API_PREFIX
import com.tutorkim.backend.common.web.CurrentUser
import com.tutorkim.backend.student.dto.StudentRelationshipDetailResponse
import com.tutorkim.backend.student.dto.UpdateStudentSubjectsRequest
import com.tutorkim.backend.student.service.PrimarySubjectNotIncludedException
import com.tutorkim.backend.student.service.StudentManagementService
import com.tutorkim.backend.student.service.StudentSubjectsNotFoundException
import com.tutorkim.backend.student.service.TeacherStudentRelationshipNotFoundException
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(API_PREFIX)
class StudentManagementController(
    private val studentManagementService: StudentManagementService,
    private val currentUser: CurrentUser,
) {
    @GetMapping("students")
    fun listStudents(
        authentication: Authentication,
        @RequestParam subjectId: UUID?,
        @RequestParam(defaultValue = "true") active: Boolean,
    ): List<StudentRelationshipDetailResponse> =
        studentManagementService.listStudentsForTeacherUser(
            teacherUserId = currentUser.id(authentication),
            subjectId = subjectId,
            active = active,
        ).map(StudentRelationshipDetailResponse::from)

    @PutMapping("students/{studentId}/subjects")
    fun updateStudentSubjects(
        authentication: Authentication,
        @PathVariable studentId: UUID,
        @Valid @RequestBody request: UpdateStudentSubjectsRequest,
    ): StudentRelationshipDetailResponse {
        val view = try {
            studentManagementService.updateSubjectsForTeacherUser(
                teacherUserId = currentUser.id(authentication),
                studentId = studentId,
                subjectIds = request.subjectIds,
                primarySubjectId = request.primarySubjectId,
            )
        } catch (exception: PrimarySubjectNotIncludedException) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "기본 과목은 과목 목록에 포함되어야 합니다.", cause = exception)
        } catch (exception: TeacherStudentRelationshipNotFoundException) {
            throw ApiException(ErrorCode.NOT_FOUND, "활성 학생 관계를 찾을 수 없습니다.", cause = exception)
        } catch (exception: StudentSubjectsNotFoundException) {
            throw ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없습니다.", cause = exception)
        }

        return StudentRelationshipDetailResponse.from(view)
    }
}

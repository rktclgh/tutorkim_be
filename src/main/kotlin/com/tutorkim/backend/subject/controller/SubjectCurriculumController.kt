package com.tutorkim.backend.subject.controller

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.common.web.API_PREFIX
import com.tutorkim.backend.subject.dto.CurriculumNodeResponse
import com.tutorkim.backend.subject.dto.SubjectResponse
import com.tutorkim.backend.subject.service.SubjectCurriculumService
import com.tutorkim.backend.subject.service.SubjectNotFoundException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(API_PREFIX)
class SubjectCurriculumController(
    private val subjectCurriculumService: SubjectCurriculumService,
) {
    @GetMapping("subjects")
    fun listSubjects(): List<SubjectResponse> =
        subjectCurriculumService.listSubjects().map(SubjectResponse::from)

    @GetMapping("subjects/{subjectId}/curriculum")
    fun getCurriculumTree(
        @PathVariable subjectId: UUID,
    ): List<CurriculumNodeResponse> =
        try {
            subjectCurriculumService.getCurriculumTree(subjectId)
        } catch (exception: SubjectNotFoundException) {
            throw ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없습니다.", cause = exception)
        }
}

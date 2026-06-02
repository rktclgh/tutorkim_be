package com.tutorkim.backend.assignment.dto

import com.tutorkim.backend.assignment.entity.Assignment
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.entity.AssignmentType
import com.tutorkim.backend.assignment.entity.ResultVisibility
import com.tutorkim.backend.assignment.entity.SubmissionStatus
import com.tutorkim.backend.student.entity.TeacherProfile
import com.tutorkim.backend.subject.entity.Subject
import java.time.Instant
import java.util.UUID

data class StudentAssignmentTeacherResponse(
    val id: UUID,
    val name: String,
) {
    companion object {
        fun from(teacher: TeacherProfile): StudentAssignmentTeacherResponse =
            StudentAssignmentTeacherResponse(
                id = teacher.id!!,
                name = teacher.displayName,
            )
    }
}

data class StudentAssignmentSubjectResponse(
    val id: UUID,
    val name: String,
) {
    companion object {
        fun from(subject: Subject): StudentAssignmentSubjectResponse =
            StudentAssignmentSubjectResponse(
                id = subject.id!!,
                name = subject.name,
            )
    }
}

data class StudentAssignmentSummaryResponse(
    val id: UUID,
    val title: String,
    val teacher: StudentAssignmentTeacherResponse,
    val subject: StudentAssignmentSubjectResponse,
    val assignmentType: AssignmentType,
    val dueAt: Instant?,
    val expired: Boolean,
    val canSolve: Boolean,
    val problemCount: Int,
    val answeredCount: Int,
    val questionCount: Int,
    val submissionStatus: SubmissionStatus,
) {
    companion object {
        fun from(
            assignment: Assignment,
            teacher: TeacherProfile,
            subject: Subject,
            expired: Boolean,
            canSolve: Boolean,
            problemCount: Int,
            answeredCount: Int,
            questionCount: Int,
            submissionStatus: SubmissionStatus,
        ): StudentAssignmentSummaryResponse =
            StudentAssignmentSummaryResponse(
                id = assignment.id!!,
                title = assignment.title,
                teacher = StudentAssignmentTeacherResponse.from(teacher),
                subject = StudentAssignmentSubjectResponse.from(subject),
                assignmentType = assignment.assignmentType,
                dueAt = assignment.dueAt,
                expired = expired,
                canSolve = canSolve,
                problemCount = problemCount,
                answeredCount = answeredCount,
                questionCount = questionCount,
                submissionStatus = submissionStatus,
            )
    }
}

data class StudentAssignmentDetailResponse(
    val id: UUID,
    val title: String,
    val teacher: StudentAssignmentTeacherResponse,
    val subject: StudentAssignmentSubjectResponse,
    val assignmentType: AssignmentType,
    val dueAt: Instant?,
    val expired: Boolean,
    val canSolve: Boolean,
    val questionCount: Int,
    val status: AssignmentStatus,
    val submissionStatus: SubmissionStatus,
    val resultVisibility: ResultVisibility,
    val problems: List<AssignmentProblemDetailResponse>,
) {
    companion object {
        fun from(
            assignment: Assignment,
            teacher: TeacherProfile,
            subject: Subject,
            expired: Boolean,
            canSolve: Boolean,
            questionCount: Int,
            submissionStatus: SubmissionStatus,
            problems: List<AssignmentProblemDetailResponse>,
        ): StudentAssignmentDetailResponse =
            StudentAssignmentDetailResponse(
                id = assignment.id!!,
                title = assignment.title,
                teacher = StudentAssignmentTeacherResponse.from(teacher),
                subject = StudentAssignmentSubjectResponse.from(subject),
                assignmentType = assignment.assignmentType,
                dueAt = assignment.dueAt,
                expired = expired,
                canSolve = canSolve,
                questionCount = questionCount,
                status = assignment.status,
                submissionStatus = submissionStatus,
                resultVisibility = assignment.resultVisibility,
                problems = problems,
            )
    }
}

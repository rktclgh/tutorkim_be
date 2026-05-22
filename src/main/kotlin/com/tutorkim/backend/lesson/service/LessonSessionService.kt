package com.tutorkim.backend.lesson.service

import com.tutorkim.backend.content.repository.CurriculumNodeRepository
import com.tutorkim.backend.lesson.entity.AssignmentPerformance
import com.tutorkim.backend.lesson.entity.FocusLevel
import com.tutorkim.backend.lesson.entity.LessonSession
import com.tutorkim.backend.lesson.entity.LessonStatus
import com.tutorkim.backend.lesson.entity.UnderstandingLevel
import com.tutorkim.backend.lesson.repository.LessonSessionRepository
import com.tutorkim.backend.student.entity.TeacherStudent
import com.tutorkim.backend.student.repository.TeacherStudentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

class LessonSessionNotFoundException(message: String) : RuntimeException(message)

class LessonCancelledException(message: String) : RuntimeException(message)

class LessonCurriculumNodeNotFoundException(message: String) : RuntimeException(message)

data class CompleteLessonSessionCommand(
    val currentCurriculumNodeId: UUID?,
    val previousProgress: String?,
    val currentProgress: String,
    val nextProgress: String?,
    val focusLevel: FocusLevel,
    val understandingLevel: UnderstandingLevel,
    val assignmentPerformance: AssignmentPerformance,
    val lessonMemo: String?,
)

@Service
class LessonSessionService(
    private val lessonSessionRepository: LessonSessionRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val curriculumNodeRepository: CurriculumNodeRepository,
) {
    private val clock: Clock = Clock.systemUTC()

    @Transactional(readOnly = true)
    fun getLessonDetail(
        teacherUserId: UUID,
        lessonSessionId: UUID,
    ): LessonSessionView {
        val session = lessonSessionRepository.findOwnedByTeacherUserId(
            teacherUserId = teacherUserId,
            lessonSessionId = lessonSessionId,
        ) ?: throw LessonSessionNotFoundException("Lesson session not found.")

        return LessonSessionView(
            session = session,
            studentId = findRelationship(session).student.id!!,
        )
    }

    @Transactional
    fun completeLesson(
        teacherUserId: UUID,
        lessonSessionId: UUID,
        command: CompleteLessonSessionCommand,
    ): LessonSessionView {
        val session = lessonSessionRepository.findOwnedByTeacherUserIdForUpdate(
            teacherUserId = teacherUserId,
            lessonSessionId = lessonSessionId,
        ) ?: throw LessonSessionNotFoundException("Lesson session not found.")

        if (session.status == LessonStatus.CANCELLED) {
            throw LessonCancelledException("Cancelled lesson sessions cannot be completed.")
        }

        val relationship = findRelationship(session)
        validateCurriculumNode(
            session = session,
            curriculumNodeId = command.currentCurriculumNodeId,
            teacherProfileId = relationship.teacher.id!!,
        )

        val now = Instant.now(clock)
        session.currentCurriculumNodeId = command.currentCurriculumNodeId
        session.previousProgressSummary = command.previousProgress
        session.currentProgress = command.currentProgress
        session.nextProgress = command.nextProgress
        session.focusLevel = command.focusLevel
        session.understandingLevel = command.understandingLevel
        session.assignmentPerformance = command.assignmentPerformance
        session.lessonMemo = command.lessonMemo
        if (session.status != LessonStatus.COMPLETED) {
            session.status = LessonStatus.COMPLETED
            session.actualStartAt = session.actualStartAt ?: session.scheduledStartAt
            session.actualEndAt = now
        }
        session.updatedAt = now

        return LessonSessionView(
            session = session,
            studentId = relationship.student.id!!,
        )
    }

    private fun validateCurriculumNode(
        session: LessonSession,
        curriculumNodeId: UUID?,
        teacherProfileId: UUID,
    ) {
        if (curriculumNodeId == null) {
            return
        }

        val node = curriculumNodeRepository.findById(curriculumNodeId)
            .orElseThrow { LessonCurriculumNodeNotFoundException("Curriculum node not found.") }
        if (node.subjectId != session.subjectId || (!node.system && node.ownerTeacherId != teacherProfileId)) {
            throw LessonCurriculumNodeNotFoundException("Curriculum node not found.")
        }
    }

    private fun findRelationship(session: LessonSession): TeacherStudent =
        teacherStudentRepository.findById(session.teacherStudentId)
            .orElseThrow { LessonSessionNotFoundException("Lesson session relationship not found.") }
}

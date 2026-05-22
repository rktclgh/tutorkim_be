package com.tutorkim.backend.lesson.repository

import com.tutorkim.backend.lesson.entity.LessonSchedule
import com.tutorkim.backend.lesson.entity.LessonSession
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface LessonScheduleRepository : JpaRepository<LessonSchedule, UUID>

interface LessonSessionRepository : JpaRepository<LessonSession, UUID> {
    @Query(
        """
        select session
        from LessonSession session, TeacherStudent relationship
        where relationship.id = session.teacherStudentId
          and session.id = :lessonSessionId
          and relationship.teacher.user.id = :teacherUserId
          and relationship.active = true
          and relationship.student.deletedAt is null
        """,
    )
    fun findOwnedByTeacherUserId(
        @Param("teacherUserId") teacherUserId: UUID,
        @Param("lessonSessionId") lessonSessionId: UUID,
    ): LessonSession?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select session
        from LessonSession session, TeacherStudent relationship
        where relationship.id = session.teacherStudentId
          and session.id = :lessonSessionId
          and relationship.teacher.user.id = :teacherUserId
          and relationship.active = true
          and relationship.student.deletedAt is null
        """,
    )
    fun findOwnedByTeacherUserIdForUpdate(
        @Param("teacherUserId") teacherUserId: UUID,
        @Param("lessonSessionId") lessonSessionId: UUID,
    ): LessonSession?

    @Query(
        """
        select session
        from LessonSession session, TeacherStudent relationship
        where relationship.id = session.teacherStudentId
          and relationship.teacher.user.id = :teacherUserId
          and relationship.active = true
          and relationship.student.deletedAt is null
          and session.scheduledStartAt >= :fromInclusive
          and session.scheduledStartAt < :toExclusive
        order by session.scheduledStartAt asc
        """,
    )
    fun findHomeTimetableSessions(
        @Param("teacherUserId") teacherUserId: UUID,
        @Param("fromInclusive") fromInclusive: Instant,
        @Param("toExclusive") toExclusive: Instant,
    ): List<LessonSession>
}

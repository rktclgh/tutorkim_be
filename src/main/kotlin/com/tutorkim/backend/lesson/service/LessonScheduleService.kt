package com.tutorkim.backend.lesson.service

import com.tutorkim.backend.lesson.entity.LessonSchedule
import com.tutorkim.backend.lesson.entity.LessonSession
import com.tutorkim.backend.lesson.repository.LessonScheduleRepository
import com.tutorkim.backend.lesson.repository.LessonSessionRepository
import com.tutorkim.backend.student.entity.TeacherStudent
import com.tutorkim.backend.student.repository.TeacherStudentRepository
import com.tutorkim.backend.student.repository.TeacherStudentSubjectRepository
import com.tutorkim.backend.student.service.TeacherStudentRelationshipNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class LessonTimeRangeException(message: String) : RuntimeException(message)

class LessonSubjectNotFoundException(message: String) : RuntimeException(message)

class LessonTimetableRangeException(message: String) : RuntimeException(message)

class LessonTimezoneException(message: String) : RuntimeException(message)

data class LessonScheduleView(
    val schedule: LessonSchedule,
    val studentId: UUID,
)

data class LessonSessionView(
    val session: LessonSession,
    val studentId: UUID,
)

data class HomeTimetableDayView(
    val date: LocalDate,
    val lessons: List<LessonSessionView>,
)

@Service
class LessonScheduleService(
    private val lessonScheduleRepository: LessonScheduleRepository,
    private val lessonSessionRepository: LessonSessionRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val teacherStudentSubjectRepository: TeacherStudentSubjectRepository,
) {
    private val timetableZone: ZoneId = ZoneId.of("Asia/Seoul")
    private val maxTimetableDays: Long = 31

    @Transactional
    fun createRecurringSchedule(
        teacherUserId: UUID,
        studentId: UUID,
        subjectId: UUID,
        dayOfWeek: Short,
        startTime: LocalTime,
        endTime: LocalTime,
        timezone: String,
    ): LessonScheduleView {
        validateTimeRange(startTime, endTime)
        validateTimezone(timezone)
        val relationship = findActiveRelationship(teacherUserId, studentId)
        validateSubjectAssigned(relationship.id!!, subjectId)

        val schedule = lessonScheduleRepository.save(
            LessonSchedule(
                teacherStudentId = relationship.id!!,
                subjectId = subjectId,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                timezone = timezone,
                active = true,
            ),
        )

        return LessonScheduleView(schedule = schedule, studentId = relationship.student.id!!)
    }

    @Transactional
    fun createLessonSession(
        teacherUserId: UUID,
        studentId: UUID,
        subjectId: UUID,
        scheduledStartAt: Instant,
        scheduledEndAt: Instant,
    ): LessonSessionView {
        if (!scheduledEndAt.isAfter(scheduledStartAt)) {
            throw LessonTimeRangeException("Lesson session end must be after start.")
        }

        val relationship = findActiveRelationship(teacherUserId, studentId)
        validateSubjectAssigned(relationship.id!!, subjectId)

        val session = lessonSessionRepository.save(
            LessonSession(
                teacherStudentId = relationship.id!!,
                subjectId = subjectId,
                scheduledStartAt = scheduledStartAt,
                scheduledEndAt = scheduledEndAt,
            ),
        )

        return LessonSessionView(session = session, studentId = relationship.student.id!!)
    }

    @Transactional(readOnly = true)
    fun getHomeTimetable(
        teacherUserId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<HomeTimetableDayView> {
        if (to.isBefore(from)) {
            throw LessonTimetableRangeException("Timetable end date must not be before start date.")
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > maxTimetableDays) {
            throw LessonTimetableRangeException("Timetable range must be at most 31 days.")
        }

        val fromInclusive = from.atStartOfDay(timetableZone).toInstant()
        val toExclusive = to.plusDays(1).atStartOfDay(timetableZone).toInstant()
        val sessions = lessonSessionRepository.findHomeTimetableSessions(
            teacherUserId = teacherUserId,
            fromInclusive = fromInclusive,
            toExclusive = toExclusive,
        )
        if (sessions.isEmpty()) {
            return emptyList()
        }

        val relationshipsById = teacherStudentRepository.findAllById(
            sessions.map { it.teacherStudentId }.toSet(),
        ).associateBy { it.id!! }

        return sessions
            .mapNotNull { session ->
                val relationship = relationshipsById[session.teacherStudentId] ?: return@mapNotNull null
                val localDate = session.scheduledStartAt.atZone(timetableZone).toLocalDate()
                localDate to LessonSessionView(
                    session = session,
                    studentId = relationship.student.id!!,
                )
            }
            .groupBy({ it.first }, { it.second })
            .map { (date, lessons) ->
                HomeTimetableDayView(date = date, lessons = lessons)
            }
            .sortedBy { it.date }
    }

    private fun findActiveRelationship(
        teacherUserId: UUID,
        studentId: UUID,
    ): TeacherStudent =
        teacherStudentRepository.findActiveByTeacherUserIdAndStudentIdForUpdate(
            teacherUserId = teacherUserId,
            studentId = studentId,
        ) ?: throw TeacherStudentRelationshipNotFoundException("Active teacher-student relationship not found.")

    private fun validateSubjectAssigned(
        teacherStudentId: UUID,
        subjectId: UUID,
    ) {
        if (!teacherStudentSubjectRepository.existsByTeacherStudent_IdAndSubject_Id(teacherStudentId, subjectId)) {
            throw LessonSubjectNotFoundException("Subject is not assigned to this teacher-student relationship.")
        }
    }

    private fun validateTimeRange(
        startTime: LocalTime,
        endTime: LocalTime,
    ) {
        if (!endTime.isAfter(startTime)) {
            throw LessonTimeRangeException("Lesson schedule end must be after start.")
        }
    }

    private fun validateTimezone(timezone: String) {
        try {
            ZoneId.of(timezone)
        } catch (exception: DateTimeException) {
            throw LessonTimezoneException("Lesson schedule timezone is invalid.")
        }
    }
}

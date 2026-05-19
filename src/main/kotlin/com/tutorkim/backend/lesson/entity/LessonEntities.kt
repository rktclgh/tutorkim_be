package com.tutorkim.backend.lesson.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "lesson_schedules")
class LessonSchedule(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false)
	var id: UUID? = null,

	@Column(name = "teacher_student_id", nullable = false)
	var teacherStudentId: UUID,

	@Column(name = "subject_id", nullable = false)
	var subjectId: UUID,

	@Column(name = "day_of_week")
	var dayOfWeek: Short? = null,

	@Column(name = "start_time", nullable = false)
	var startTime: LocalTime,

	@Column(name = "end_time", nullable = false)
	var endTime: LocalTime,

	@Column(name = "timezone", nullable = false, length = 50)
	var timezone: String = "Asia/Seoul",

	@Column(name = "active", nullable = false)
	var active: Boolean = true,

	@Column(name = "created_at", nullable = false)
	var createdAt: Instant = Instant.now(),

	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(
	name = "lesson_sessions",
	indexes = [
		Index(
			name = "lesson_sessions_teacher_student_time_idx",
			columnList = "teacher_student_id, scheduled_start_at",
		),
	],
)
class LessonSession(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false)
	var id: UUID? = null,

	@Column(name = "teacher_student_id", nullable = false)
	var teacherStudentId: UUID,

	@Column(name = "subject_id", nullable = false)
	var subjectId: UUID,

	@Column(name = "scheduled_start_at", nullable = false)
	var scheduledStartAt: Instant,

	@Column(name = "scheduled_end_at")
	var scheduledEndAt: Instant? = null,

	@Column(name = "actual_start_at")
	var actualStartAt: Instant? = null,

	@Column(name = "actual_end_at")
	var actualEndAt: Instant? = null,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "status", nullable = false, columnDefinition = "lesson_status")
	var status: LessonStatus = LessonStatus.SCHEDULED,

	@Column(name = "previous_progress_summary", columnDefinition = "text")
	var previousProgressSummary: String? = null,

	@Column(name = "current_progress", columnDefinition = "text")
	var currentProgress: String? = null,

	@Column(name = "next_progress", columnDefinition = "text")
	var nextProgress: String? = null,

	@Column(name = "current_curriculum_node_id")
	var currentCurriculumNodeId: UUID? = null,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "focus_level", columnDefinition = "focus_level")
	var focusLevel: FocusLevel? = null,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "understanding_level", columnDefinition = "understanding_level")
	var understandingLevel: UnderstandingLevel? = null,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "assignment_performance", columnDefinition = "assignment_performance")
	var assignmentPerformance: AssignmentPerformance? = null,

	@Column(name = "lesson_memo", columnDefinition = "text")
	var lessonMemo: String? = null,

	@Column(name = "created_at", nullable = false)
	var createdAt: Instant = Instant.now(),

	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant = Instant.now(),
)

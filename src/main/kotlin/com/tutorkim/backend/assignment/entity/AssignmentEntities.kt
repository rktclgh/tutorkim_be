package com.tutorkim.backend.assignment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcType
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "assignments")
class Assignment(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false)
	var id: UUID? = null,

	@Column(name = "teacher_id", nullable = false)
	var teacherId: UUID,

	@Column(name = "teacher_student_id")
	var teacherStudentId: UUID? = null,

	@Column(name = "lesson_session_id")
	var lessonSessionId: UUID? = null,

	@Column(name = "subject_id", nullable = false)
	var subjectId: UUID,

	@Column(name = "title", nullable = false, length = 150)
	var title: String,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "assignment_type", nullable = false, columnDefinition = "assignment_type")
	var assignmentType: AssignmentType,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "status", nullable = false, columnDefinition = "assignment_status")
	var status: AssignmentStatus = AssignmentStatus.DRAFT,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "result_visibility", nullable = false, columnDefinition = "result_visibility")
	var resultVisibility: ResultVisibility = ResultVisibility.HIDDEN_UNTIL_RELEASED,

	@Column(name = "due_at")
	var dueAt: Instant? = null,

	@Column(name = "published_at")
	var publishedAt: Instant? = null,

	@Column(name = "created_at", nullable = false)
	var createdAt: Instant = Instant.now(),

	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(
	name = "assignment_problems",
	uniqueConstraints = [
		UniqueConstraint(name = "assignment_problem_unique", columnNames = ["assignment_id", "problem_id"]),
		UniqueConstraint(name = "assignment_problem_order_unique", columnNames = ["assignment_id", "sort_order"]),
	],
)
class AssignmentProblem(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false)
	var id: UUID? = null,

	@Column(name = "assignment_id", nullable = false)
	var assignmentId: UUID,

	@Column(name = "problem_id", nullable = false)
	var problemId: UUID,

	@Column(name = "sort_order", nullable = false)
	var sortOrder: Int,

	@Column(name = "points", nullable = false, precision = 6, scale = 2)
	var points: BigDecimal = BigDecimal.ONE,

	@Column(name = "created_at", nullable = false)
	var createdAt: Instant = Instant.now(),
)

@Entity
@Table(
	name = "assignment_targets",
	uniqueConstraints = [
		UniqueConstraint(name = "assignment_target_unique", columnNames = ["assignment_id", "teacher_student_id"]),
	],
)
class AssignmentTarget(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false)
	var id: UUID? = null,

	@Column(name = "assignment_id", nullable = false)
	var assignmentId: UUID,

	@Column(name = "teacher_student_id", nullable = false)
	var teacherStudentId: UUID,

	@Column(name = "created_at", nullable = false)
	var createdAt: Instant = Instant.now(),
)

@Entity
@Table(
	name = "assignment_submissions",
	uniqueConstraints = [
		UniqueConstraint(name = "assignment_submission_unique", columnNames = ["assignment_id", "teacher_student_id"]),
	],
)
class AssignmentSubmission(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false)
	var id: UUID? = null,

	@Column(name = "assignment_id", nullable = false)
	var assignmentId: UUID,

	@Column(name = "teacher_student_id", nullable = false)
	var teacherStudentId: UUID,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "status", nullable = false, columnDefinition = "submission_status")
	var status: SubmissionStatus = SubmissionStatus.NOT_SUBMITTED,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "grading_status", nullable = false, columnDefinition = "grading_status")
	var gradingStatus: GradingStatus = GradingStatus.NOT_GRADED,

	@Column(name = "score", precision = 7, scale = 2)
	var score: BigDecimal? = null,

	@Column(name = "total_points", precision = 7, scale = 2)
	var totalPoints: BigDecimal? = null,

	@Column(name = "submitted_at")
	var submittedAt: Instant? = null,

	@Column(name = "graded_at")
	var gradedAt: Instant? = null,

	@Column(name = "created_at", nullable = false)
	var createdAt: Instant = Instant.now(),

	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(
	name = "submission_answers",
	indexes = [
		Index(name = "submission_answers_problem_correct_idx", columnList = "problem_id, is_correct"),
	],
	uniqueConstraints = [
		UniqueConstraint(name = "submission_answer_unique", columnNames = ["submission_id", "problem_id"]),
	],
)
class SubmissionAnswer(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false)
	var id: UUID? = null,

	@Column(name = "submission_id", nullable = false)
	var submissionId: UUID,

	@Column(name = "problem_id", nullable = false)
	var problemId: UUID,

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "selected_choice_numbers")
	var selectedChoiceNumbers: List<Short>? = null,

	@Column(name = "numeric_answer", columnDefinition = "numeric")
	var numericAnswer: BigDecimal? = null,

	@Column(name = "is_unknown", nullable = false)
	var unknown: Boolean = false,

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType::class)
	@Column(name = "attempt_status", nullable = false, columnDefinition = "problem_attempt_status")
	var attemptStatus: ProblemAttemptStatus = ProblemAttemptStatus.PENDING,

	@Column(name = "retry_count", nullable = false)
	var retryCount: Int = 0,

	@Column(name = "auto_is_correct")
	var autoIsCorrect: Boolean? = null,

	@Column(name = "is_correct")
	var isCorrect: Boolean? = null,

	@Column(name = "auto_graded_at")
	var autoGradedAt: Instant? = null,

	@Column(name = "manual_is_correct")
	var manualIsCorrect: Boolean? = null,

	@Column(name = "manual_grading_reason", columnDefinition = "text")
	var manualGradingReason: String? = null,

	@Column(name = "manually_graded_by")
	var manuallyGradedBy: UUID? = null,

	@Column(name = "manually_graded_at")
	var manuallyGradedAt: Instant? = null,

	@Column(name = "created_at", nullable = false)
	var createdAt: Instant = Instant.now(),

	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(
	name = "submission_solution_files",
	uniqueConstraints = [
		UniqueConstraint(name = "submission_solution_file_asset_unique", columnNames = ["file_asset_id"]),
	],
)
class SubmissionSolutionFile(
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false)
	var id: UUID? = null,

	@Column(name = "submission_answer_id", nullable = false)
	var submissionAnswerId: UUID,

	@Column(name = "file_asset_id", nullable = false)
	var fileAssetId: UUID,

	@Column(name = "created_at", nullable = false)
	var createdAt: Instant = Instant.now(),
)

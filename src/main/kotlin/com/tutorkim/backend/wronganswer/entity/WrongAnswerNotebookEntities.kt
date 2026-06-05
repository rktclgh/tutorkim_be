package com.tutorkim.backend.wronganswer.entity

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
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import java.time.Instant
import java.util.UUID

enum class WrongAnswerNotebookStatus {
    DRAFT,
    PUBLISHED,
}

@Entity
@Table(
    name = "wrong_answer_notebooks",
    indexes = [
        Index(name = "wrong_answer_notebooks_student_idx", columnList = "teacher_student_id,status,due_at"),
    ],
)
class WrongAnswerNotebook(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    var id: UUID? = null,

    @Column(name = "teacher_id", nullable = false)
    var teacherId: UUID,

    @Column(name = "teacher_student_id", nullable = false)
    var teacherStudentId: UUID,

    @Column(name = "subject_id", nullable = false)
    var subjectId: UUID,

    @Column(name = "assignment_id")
    var assignmentId: UUID? = null,

    @Column(name = "title", nullable = false, length = 150)
    var title: String,

    @Column(name = "source_summary", length = 200)
    var sourceSummary: String? = null,

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "status", nullable = false, columnDefinition = "wrong_answer_notebook_status")
    var status: WrongAnswerNotebookStatus = WrongAnswerNotebookStatus.DRAFT,

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
    name = "wrong_answer_notebook_problems",
    indexes = [
        Index(name = "wrong_answer_notebook_problems_problem_idx", columnList = "problem_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "wrong_answer_notebook_problem_unique", columnNames = ["notebook_id", "problem_id"]),
        UniqueConstraint(name = "wrong_answer_notebook_unique_problem_unique", columnNames = ["notebook_id", "unique_problem_id"]),
        UniqueConstraint(name = "wrong_answer_notebook_problem_order_unique", columnNames = ["notebook_id", "sort_order"]),
    ],
)
class WrongAnswerNotebookProblem(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    var id: UUID? = null,

    @Column(name = "notebook_id", nullable = false)
    var notebookId: UUID,

    @Column(name = "problem_id", nullable = false)
    var problemId: UUID,

    @Column(name = "source_assignment_problem_id", nullable = false)
    var sourceAssignmentProblemId: UUID,

    @Column(name = "unique_problem_id", nullable = false, length = 120)
    var uniqueProblemId: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

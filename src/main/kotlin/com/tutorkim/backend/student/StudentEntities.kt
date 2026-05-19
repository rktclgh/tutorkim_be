package com.tutorkim.backend.student

import com.tutorkim.backend.identity.User
import com.tutorkim.backend.subject.Subject
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

enum class InviteCodeStatus {
    ACTIVE,
    EXPIRED,
    REVOKED,
}

@Entity
@Table(name = "teacher_profiles")
class TeacherProfile(
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    var user: User,

    @Column(name = "display_name", nullable = false, length = 100)
    var displayName: String,

    @Column(name = "onboarding_completed_at")
    var onboardingCompletedAt: Instant? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun markUpdated() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(name = "student_profiles")
class StudentProfile(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    var user: User? = null,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(length = 100)
    var school: String? = null,

    @Column(length = 50)
    var grade: String? = null,

    @Column(length = 50)
    var phone: String? = null,

    @Column(name = "parent_phone", length = 50)
    var parentPhone: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null

    @PreUpdate
    fun markUpdated() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(
    name = "teacher_students",
    indexes = [
        Index(name = "teacher_students_teacher_active_idx", columnList = "teacher_id,active"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "teacher_student_unique", columnNames = ["teacher_id", "student_id"]),
    ],
)
class TeacherStudent(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    var teacher: TeacherProfile,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    var student: StudentProfile,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_subject_id")
    var defaultSubject: Subject? = null,

    @Column(columnDefinition = "text")
    var memo: String? = null,

    @Column(nullable = false)
    var active: Boolean = true,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun markUpdated() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(
    name = "teacher_invite_codes",
    indexes = [
        Index(name = "teacher_invite_codes_teacher_status_idx", columnList = "teacher_id,status"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "teacher_invite_codes_code_unique", columnNames = ["code"]),
    ],
)
class TeacherInviteCode(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    var teacher: TeacherProfile,

    @Column(nullable = false, length = 32)
    var code: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: InviteCodeStatus = InviteCodeStatus.ACTIVE,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    fun revoke() {
        status = InviteCodeStatus.REVOKED
    }
}

@Entity
@Table(
    name = "teacher_student_subjects",
    uniqueConstraints = [
        UniqueConstraint(name = "teacher_student_subject_unique", columnNames = ["teacher_student_id", "subject_id"]),
    ],
)
class TeacherStudentSubject(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_student_id", nullable = false)
    var teacherStudent: TeacherStudent,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: Subject,

    @Column(name = "is_primary", nullable = false)
    var primary: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
}

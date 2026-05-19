package com.tutorkim.backend.student.repository

import com.tutorkim.backend.student.entity.InviteCodeStatus
import com.tutorkim.backend.student.entity.StudentProfile
import com.tutorkim.backend.student.entity.TeacherInviteCode
import com.tutorkim.backend.student.entity.TeacherProfile
import com.tutorkim.backend.student.entity.TeacherStudent
import com.tutorkim.backend.student.entity.TeacherStudentSubject
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface TeacherProfileRepository : JpaRepository<TeacherProfile, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select teacher from TeacherProfile teacher where teacher.id = :id")
    fun findLockedById(@Param("id") id: UUID): TeacherProfile?
}

interface StudentProfileRepository : JpaRepository<StudentProfile, UUID>

interface TeacherStudentRepository : JpaRepository<TeacherStudent, UUID> {
    fun existsByTeacher_IdAndStudent_Id(teacherId: UUID, studentId: UUID): Boolean
}

interface TeacherInviteCodeRepository : JpaRepository<TeacherInviteCode, UUID> {
    fun findByTeacher_IdAndId(teacherId: UUID, id: UUID): TeacherInviteCode?

    fun findFirstByTeacher_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
        teacherId: UUID,
        status: InviteCodeStatus,
        now: Instant,
    ): TeacherInviteCode?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select code
        from TeacherInviteCode code
        where code.code = :code
          and code.status = :status
          and code.expiresAt > :now
        """,
    )
    fun findConsumableForUpdate(
        @Param("code") code: String,
        @Param("status") status: InviteCodeStatus,
        @Param("now") now: Instant,
    ): TeacherInviteCode?

    @Query(
        """
        select code
        from TeacherInviteCode code
        where code.teacher.id = :teacherId
          and code.status = :status
          and code.expiresAt > :now
        """,
    )
    fun findAllActiveForTeacher(
        @Param("teacherId") teacherId: UUID,
        @Param("status") status: InviteCodeStatus,
        @Param("now") now: Instant,
    ): List<TeacherInviteCode>
}

interface TeacherStudentSubjectRepository : JpaRepository<TeacherStudentSubject, UUID> {
    fun findByTeacherStudent_Id(teacherStudentId: UUID): List<TeacherStudentSubject>
}

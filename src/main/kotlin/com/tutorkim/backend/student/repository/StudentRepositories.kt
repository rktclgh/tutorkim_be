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

    fun findByUser_Id(userId: UUID): TeacherProfile?
}

interface StudentProfileRepository : JpaRepository<StudentProfile, UUID> {
    fun findByUser_Id(userId: UUID): StudentProfile?

    fun findByUser_IdAndDeletedAtIsNull(userId: UUID): StudentProfile?
}

interface TeacherStudentRepository : JpaRepository<TeacherStudent, UUID> {
    fun existsByTeacher_IdAndStudent_Id(teacherId: UUID, studentId: UUID): Boolean

    @Query(
        """
        select distinct relationship
        from TeacherStudent relationship
        join fetch relationship.student student
        left join fetch relationship.defaultSubject defaultSubject
        where relationship.teacher.user.id = :teacherUserId
          and relationship.active = :active
          and student.deletedAt is null
          and (
            :subjectId is null
            or exists (
              select subjectLink.id
              from TeacherStudentSubject subjectLink
              where subjectLink.teacherStudent = relationship
                and subjectLink.subject.id = :subjectId
            )
          )
        order by student.name asc, relationship.createdAt asc
        """,
    )
    fun findRosterByTeacherUserId(
        @Param("teacherUserId") teacherUserId: UUID,
        @Param("subjectId") subjectId: UUID?,
        @Param("active") active: Boolean,
    ): List<TeacherStudent>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select relationship
        from TeacherStudent relationship
        where relationship.teacher.id = :teacherId
          and relationship.student.id = :studentId
        """,
    )
    fun findByTeacherIdAndStudentIdForUpdate(
        @Param("teacherId") teacherId: UUID,
        @Param("studentId") studentId: UUID,
    ): TeacherStudent?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select relationship
        from TeacherStudent relationship
        join relationship.student student
        where relationship.teacher.user.id = :teacherUserId
          and relationship.student.id = :studentId
          and relationship.active = true
          and student.deletedAt is null
        """,
    )
    fun findActiveByTeacherUserIdAndStudentIdForUpdate(
        @Param("teacherUserId") teacherUserId: UUID,
        @Param("studentId") studentId: UUID,
    ): TeacherStudent?

    @Query(
        """
        select relationship
        from TeacherStudent relationship
        join relationship.student student
        where relationship.teacher.user.id = :teacherUserId
          and relationship.student.id = :studentId
          and relationship.active = true
          and student.deletedAt is null
        """,
    )
    fun findActiveByTeacherUserIdAndStudentId(
        @Param("teacherUserId") teacherUserId: UUID,
        @Param("studentId") studentId: UUID,
    ): TeacherStudent?

    @Query(
        """
        select distinct relationship
        from TeacherStudent relationship
        join fetch relationship.student student
        where relationship.id in :ids
          and relationship.teacher.id = :teacherId
        """,
    )
    fun findByIdInAndTeacherIdWithStudent(
        @Param("ids") ids: Collection<UUID>,
        @Param("teacherId") teacherId: UUID,
    ): List<TeacherStudent>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select relationship
        from TeacherStudent relationship
        join fetch relationship.student student
        where relationship.id = :id
          and relationship.teacher.id = :teacherId
          and relationship.active = true
          and student.deletedAt is null
        """,
    )
    fun findActiveByIdAndTeacherIdForUpdate(
        @Param("id") id: UUID,
        @Param("teacherId") teacherId: UUID,
    ): TeacherStudent?

    @Query(
        """
        select distinct relationship
        from TeacherStudent relationship
        join fetch relationship.teacher teacher
        join fetch relationship.student student
        where relationship.id in :ids
          and student.id = :studentId
          and relationship.active = true
          and student.deletedAt is null
        """,
    )
    fun findByIdInAndStudentIdWithTeacher(
        @Param("ids") ids: Collection<UUID>,
        @Param("studentId") studentId: UUID,
    ): List<TeacherStudent>

    @Query(
        """
        select distinct relationship
        from TeacherStudent relationship
        join fetch relationship.teacher teacher
        join fetch relationship.student student
        where student.id = :studentId
          and relationship.active = true
          and student.deletedAt is null
        """,
    )
    fun findActiveByStudentIdWithTeacher(
        @Param("studentId") studentId: UUID,
    ): List<TeacherStudent>

    @Query(
        """
        select relationship
        from TeacherStudent relationship
        join fetch relationship.teacher teacher
        join fetch relationship.student student
        where relationship.id = :id
          and student.id = :studentId
          and relationship.active = true
          and student.deletedAt is null
        """,
    )
    fun findByIdAndStudentIdWithTeacher(
        @Param("id") id: UUID,
        @Param("studentId") studentId: UUID,
    ): TeacherStudent?
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

    fun existsByTeacherStudent_IdAndSubject_Id(teacherStudentId: UUID, subjectId: UUID): Boolean
}

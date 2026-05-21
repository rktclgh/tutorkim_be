package com.tutorkim.backend.student.service

import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import com.tutorkim.backend.student.entity.InviteCodeStatus
import com.tutorkim.backend.student.entity.TeacherInviteCode
import com.tutorkim.backend.student.entity.TeacherStudent
import com.tutorkim.backend.student.entity.TeacherStudentSubject
import com.tutorkim.backend.student.repository.StudentProfileRepository
import com.tutorkim.backend.student.repository.TeacherInviteCodeRepository
import com.tutorkim.backend.student.repository.TeacherProfileRepository
import com.tutorkim.backend.student.repository.TeacherStudentRepository
import com.tutorkim.backend.student.repository.TeacherStudentSubjectRepository
import com.tutorkim.backend.subject.repository.TeacherSubjectRepository
import org.hibernate.Hibernate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InviteCodeNotConsumableException(message: String) : RuntimeException(message)

class TeacherStudentAlreadyExistsException(message: String) : RuntimeException(message)

class TeacherDefaultSubjectNotUniqueException(message: String) : RuntimeException(message)

@Service
class TeacherInviteCodeService(
    private val teacherProfileRepository: TeacherProfileRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val teacherSubjectRepository: TeacherSubjectRepository,
    private val teacherStudentRepository: TeacherStudentRepository,
    private val teacherStudentSubjectRepository: TeacherStudentSubjectRepository,
    private val teacherInviteCodeRepository: TeacherInviteCodeRepository,
) {
    private val random = SecureRandom()

    @Transactional
    fun createInviteCode(
        teacherId: UUID,
        expiresIn: Duration = Duration.ofHours(24),
        clock: Clock = Clock.systemUTC(),
    ): TeacherInviteCode {
        val now = clock.instant()
        val expiresAt = now.plus(expiresIn)
        return createInviteCode(teacherId, generateCode(), expiresAt, clock)
    }

    @Transactional
    fun createInviteCode(
        teacherId: UUID,
        code: String,
        expiresAt: Instant,
        clock: Clock = Clock.systemUTC(),
    ): TeacherInviteCode {
        val now = clock.instant()
        require(expiresAt.isAfter(now)) { "Invite code expiry must be in the future." }

        val teacher = teacherProfileRepository.findLockedById(teacherId)
            ?: throw IllegalArgumentException("Teacher profile not found: $teacherId")

        teacherInviteCodeRepository.findAllActiveForTeacher(teacherId, InviteCodeStatus.ACTIVE, now)
            .forEach { it.revoke() }

        return teacherInviteCodeRepository.save(
            TeacherInviteCode(
                teacher = teacher,
                code = code,
                status = InviteCodeStatus.ACTIVE,
                expiresAt = expiresAt,
                createdAt = now,
            ),
        )
    }

    @Transactional
    fun createInviteCodeForTeacherUser(
        teacherUserId: UUID,
        expiresIn: Duration = Duration.ofHours(24),
        clock: Clock = Clock.systemUTC(),
    ): TeacherInviteCode {
        val teacher = findTeacherProfileByUserId(teacherUserId)
        return createInviteCode(teacher.id!!, expiresIn, clock)
    }

    @Transactional(readOnly = true)
    fun findActiveCode(teacherId: UUID, clock: Clock = Clock.systemUTC()): TeacherInviteCode? =
        teacherInviteCodeRepository.findFirstByTeacher_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            teacherId,
            InviteCodeStatus.ACTIVE,
            clock.instant(),
        )

    @Transactional(readOnly = true)
    fun findActiveCodeForTeacherUser(
        teacherUserId: UUID,
        clock: Clock = Clock.systemUTC(),
    ): TeacherInviteCode? {
        val teacher = findTeacherProfileByUserId(teacherUserId)
        return findActiveCode(teacher.id!!, clock)
    }

    @Transactional
    fun revokeInviteCode(teacherId: UUID, inviteCodeId: UUID): TeacherInviteCode {
        val inviteCode = teacherInviteCodeRepository.findByTeacher_IdAndId(teacherId, inviteCodeId)
            ?: throw InviteCodeNotConsumableException("Invite code not found for teacher.")

        inviteCode.revoke()
        return inviteCode
    }

    @Transactional
    fun revokeInviteCodeForTeacherUser(
        teacherUserId: UUID,
        inviteCodeId: UUID,
    ): TeacherInviteCode {
        val teacher = findTeacherProfileByUserId(teacherUserId)
        return revokeInviteCode(teacher.id!!, inviteCodeId)
    }

    @Transactional
    fun consumeInviteCode(
        code: String,
        studentId: UUID,
        clock: Clock = Clock.systemUTC(),
    ): TeacherStudent {
        val now = clock.instant()
        val inviteCode = teacherInviteCodeRepository.findConsumableForUpdate(
            code = code,
            status = InviteCodeStatus.ACTIVE,
            now = now,
        ) ?: throw InviteCodeNotConsumableException("Invite code is not active or has expired.")

        val teacher = inviteCode.teacher
        val student = studentProfileRepository.findById(studentId)
            .orElseThrow { IllegalArgumentException("Student profile not found: $studentId") }

        if (teacherStudentRepository.existsByTeacher_IdAndStudent_Id(teacher.id!!, student.id!!)) {
            throw TeacherStudentAlreadyExistsException("Teacher-student relationship already exists.")
        }

        val defaultSubjects = teacherSubjectRepository.findByTeacher_IdAndDefaultTrue(teacher.id!!)
        if (defaultSubjects.size > 1) {
            throw TeacherDefaultSubjectNotUniqueException("Teacher has multiple default subjects.")
        }
        val defaultSubject = defaultSubjects.singleOrNull()?.subject

        val relationship = teacherStudentRepository.save(
            TeacherStudent(
                teacher = teacher,
                student = student,
                defaultSubject = defaultSubject,
                active = true,
            ),
        )

        if (defaultSubject != null) {
            teacherStudentSubjectRepository.save(
                TeacherStudentSubject(
                    teacherStudent = relationship,
                    subject = defaultSubject,
                    primary = true,
                ),
            )
        }

        Hibernate.initialize(relationship.teacher)
        Hibernate.initialize(relationship.student)
        Hibernate.initialize(relationship.defaultSubject)
        inviteCode.revoke()
        return relationship
    }

    @Transactional
    fun consumeInviteCodeForStudentUser(
        code: String,
        studentUserId: UUID,
        clock: Clock = Clock.systemUTC(),
    ): TeacherStudent {
        val student = studentProfileRepository.findByUser_IdAndDeletedAtIsNull(studentUserId)
            ?: throw ApiException(ErrorCode.FORBIDDEN, "학생 프로필이 필요합니다.")

        return consumeInviteCode(code, student.id!!, clock)
    }

    private fun findTeacherProfileByUserId(teacherUserId: UUID) =
        teacherProfileRepository.findByUser_Id(teacherUserId)
            ?: throw ApiException(ErrorCode.FORBIDDEN, "선생님 프로필이 필요합니다.")

    private fun generateCode(): String =
        buildString {
            repeat(INVITE_CODE_LENGTH) {
                append(INVITE_CODE_ALPHABET[random.nextInt(INVITE_CODE_ALPHABET.length)])
            }
        }

    private companion object {
        const val INVITE_CODE_LENGTH = 8
        const val INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}

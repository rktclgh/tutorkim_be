package com.tutorkim.backend.student

import com.tutorkim.backend.subject.TeacherSubjectRepository
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

    @Transactional(readOnly = true)
    fun findActiveCode(teacherId: UUID, clock: Clock = Clock.systemUTC()): TeacherInviteCode? =
        teacherInviteCodeRepository.findFirstByTeacher_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            teacherId,
            InviteCodeStatus.ACTIVE,
            clock.instant(),
        )

    @Transactional
    fun revokeInviteCode(teacherId: UUID, inviteCodeId: UUID): TeacherInviteCode {
        val inviteCode = teacherInviteCodeRepository.findByTeacher_IdAndId(teacherId, inviteCodeId)
            ?: throw InviteCodeNotConsumableException("Invite code not found for teacher.")

        inviteCode.revoke()
        return inviteCode
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

        inviteCode.revoke()
        return relationship
    }

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

package com.tutorkim.backend.student.service

import com.tutorkim.backend.student.entity.TeacherStudent
import com.tutorkim.backend.student.entity.TeacherStudentSubject
import com.tutorkim.backend.student.repository.TeacherStudentRepository
import com.tutorkim.backend.student.repository.TeacherStudentSubjectRepository
import com.tutorkim.backend.subject.repository.SubjectRepository
import org.hibernate.Hibernate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

class PrimarySubjectNotIncludedException(message: String) : RuntimeException(message)

class StudentSubjectsNotFoundException(message: String) : RuntimeException(message)

data class StudentRelationshipView(
    val relationship: TeacherStudent,
    val subjects: List<TeacherStudentSubject>,
)

@Service
class StudentManagementService(
    private val teacherStudentRepository: TeacherStudentRepository,
    private val teacherStudentSubjectRepository: TeacherStudentSubjectRepository,
    private val subjectRepository: SubjectRepository,
) {
    @Transactional(readOnly = true)
    fun listStudentsForTeacherUser(
        teacherUserId: UUID,
        subjectId: UUID?,
        active: Boolean,
    ): List<StudentRelationshipView> {
        val relationships = teacherStudentRepository.findRosterByTeacherUserId(
            teacherUserId = teacherUserId,
            subjectId = subjectId,
            active = active,
        )
        if (relationships.isEmpty()) {
            return emptyList()
        }
        val subjectsByRelationshipId = teacherStudentSubjectRepository.findByTeacherStudent_IdInWithSubject(
            relationships.map { it.id!! },
        ).groupBy { it.teacherStudent.id!! }

        return relationships.map { relationship ->
            toView(
                relationship = relationship,
                subjects = subjectsByRelationshipId[relationship.id!!].orEmpty(),
            )
        }
    }

    @Transactional
    fun updateSubjectsForTeacherUser(
        teacherUserId: UUID,
        studentId: UUID,
        subjectIds: Set<UUID>,
        primarySubjectId: UUID,
    ): StudentRelationshipView {
        if (primarySubjectId !in subjectIds) {
            throw PrimarySubjectNotIncludedException("Primary subject must be included in subjects.")
        }

        val relationship = teacherStudentRepository.findActiveByTeacherUserIdAndStudentIdForUpdate(
            teacherUserId = teacherUserId,
            studentId = studentId,
        ) ?: throw TeacherStudentRelationshipNotFoundException("Active teacher-student relationship not found.")

        val subjects = subjectRepository.findByIdInAndActiveTrue(subjectIds)
            .associateBy { it.id!! }
        if (subjects.size != subjectIds.size) {
            throw StudentSubjectsNotFoundException("One or more subjects were not found.")
        }

        val currentSubjectLinks = teacherStudentSubjectRepository.findByTeacherStudent_Id(relationship.id!!)
        val currentLinksBySubjectId = currentSubjectLinks.associateBy { it.subject.id!! }
        val removedLinks = currentSubjectLinks.filter { it.subject.id!! !in subjectIds }
        val remainingLinksBySubjectId = currentLinksBySubjectId.toMutableMap()

        currentSubjectLinks.forEach { it.primary = false }
        teacherStudentSubjectRepository.flush()

        if (removedLinks.isNotEmpty()) {
            teacherStudentSubjectRepository.deleteAll(removedLinks)
            teacherStudentSubjectRepository.flush()
            removedLinks.forEach { remainingLinksBySubjectId.remove(it.subject.id!!) }
        }

        subjectIds.forEach { subjectId ->
            val currentLink = remainingLinksBySubjectId[subjectId]
            if (currentLink == null) {
                val newLink = teacherStudentSubjectRepository.save(
                    TeacherStudentSubject(
                        teacherStudent = relationship,
                        subject = subjects.getValue(subjectId),
                        primary = false,
                    ),
                )
                remainingLinksBySubjectId[subjectId] = newLink
            } else {
                currentLink.primary = false
            }
        }

        remainingLinksBySubjectId.getValue(primarySubjectId).primary = true
        relationship.defaultSubject = subjects.getValue(primarySubjectId)

        return toView(relationship)
    }

    private fun toView(relationship: TeacherStudent): StudentRelationshipView =
        toView(
            relationship = relationship,
            subjects = teacherStudentSubjectRepository.findByTeacherStudent_Id(relationship.id!!),
        )

    private fun toView(
        relationship: TeacherStudent,
        subjects: List<TeacherStudentSubject>,
    ): StudentRelationshipView {
        Hibernate.initialize(relationship.student)
        Hibernate.initialize(relationship.defaultSubject)
        val sortedSubjects = subjects
            .sortedWith(
                compareByDescending<TeacherStudentSubject> { it.primary }
                    .thenBy { it.subject.name },
            )
        sortedSubjects.forEach { Hibernate.initialize(it.subject) }
        return StudentRelationshipView(relationship = relationship, subjects = sortedSubjects)
    }
}

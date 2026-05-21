package com.tutorkim.backend.subject.repository

import com.tutorkim.backend.subject.entity.Subject
import com.tutorkim.backend.subject.entity.TeacherSubject
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SubjectRepository : JpaRepository<Subject, UUID> {
    fun findByIdInAndActiveTrue(ids: Collection<UUID>): List<Subject>
}

interface TeacherSubjectRepository : JpaRepository<TeacherSubject, UUID> {
    fun findByTeacher_IdAndDefaultTrue(teacherId: UUID): List<TeacherSubject>
}

package com.tutorkim.backend.subject

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SubjectRepository : JpaRepository<Subject, UUID>

interface TeacherSubjectRepository : JpaRepository<TeacherSubject, UUID> {
    fun findByTeacher_IdAndDefaultTrue(teacherId: UUID): List<TeacherSubject>
}

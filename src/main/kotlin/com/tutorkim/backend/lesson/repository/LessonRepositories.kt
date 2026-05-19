package com.tutorkim.backend.lesson.repository

import com.tutorkim.backend.lesson.entity.LessonSchedule
import com.tutorkim.backend.lesson.entity.LessonSession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LessonScheduleRepository : JpaRepository<LessonSchedule, UUID>

interface LessonSessionRepository : JpaRepository<LessonSession, UUID>

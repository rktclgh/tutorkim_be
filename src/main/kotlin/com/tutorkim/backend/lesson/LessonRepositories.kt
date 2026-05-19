package com.tutorkim.backend.lesson

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LessonScheduleRepository : JpaRepository<LessonSchedule, UUID>

interface LessonSessionRepository : JpaRepository<LessonSession, UUID>

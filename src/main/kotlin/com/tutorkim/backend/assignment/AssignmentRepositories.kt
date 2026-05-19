package com.tutorkim.backend.assignment

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AssignmentRepository : JpaRepository<Assignment, UUID>

interface AssignmentProblemRepository : JpaRepository<AssignmentProblem, UUID>

interface AssignmentTargetRepository : JpaRepository<AssignmentTarget, UUID>

interface AssignmentSubmissionRepository : JpaRepository<AssignmentSubmission, UUID>

interface SubmissionAnswerRepository : JpaRepository<SubmissionAnswer, UUID>

interface SubmissionSolutionFileRepository : JpaRepository<SubmissionSolutionFile, UUID>

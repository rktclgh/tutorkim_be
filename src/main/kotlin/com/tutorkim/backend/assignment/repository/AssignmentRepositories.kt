package com.tutorkim.backend.assignment.repository

import com.tutorkim.backend.assignment.entity.Assignment
import com.tutorkim.backend.assignment.entity.AssignmentProblem
import com.tutorkim.backend.assignment.entity.AssignmentSubmission
import com.tutorkim.backend.assignment.entity.AssignmentTarget
import com.tutorkim.backend.assignment.entity.SubmissionAnswer
import com.tutorkim.backend.assignment.entity.SubmissionSolutionFile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AssignmentRepository : JpaRepository<Assignment, UUID>

interface AssignmentProblemRepository : JpaRepository<AssignmentProblem, UUID>

interface AssignmentTargetRepository : JpaRepository<AssignmentTarget, UUID>

interface AssignmentSubmissionRepository : JpaRepository<AssignmentSubmission, UUID>

interface SubmissionAnswerRepository : JpaRepository<SubmissionAnswer, UUID>

interface SubmissionSolutionFileRepository : JpaRepository<SubmissionSolutionFile, UUID>

package com.tutorkim.backend.assignment.repository

import com.tutorkim.backend.assignment.entity.Assignment
import com.tutorkim.backend.assignment.entity.AssignmentProblem
import com.tutorkim.backend.assignment.entity.AssignmentSubmission
import com.tutorkim.backend.assignment.entity.AssignmentStatus
import com.tutorkim.backend.assignment.entity.AssignmentTarget
import com.tutorkim.backend.assignment.entity.AssignmentType
import com.tutorkim.backend.assignment.entity.SubmissionAnswer
import com.tutorkim.backend.assignment.entity.SubmissionSolutionFile
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AssignmentRepository : JpaRepository<Assignment, UUID> {
	fun findByIdAndTeacherId(
		id: UUID,
		teacherId: UUID,
	): Assignment?

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(
		"""
		select assignment
		from Assignment assignment
		where assignment.id = :assignmentId
		  and assignment.teacherId = :teacherId
		""",
	)
	fun findOwnedByTeacherIdForUpdate(
		@Param("assignmentId") assignmentId: UUID,
		@Param("teacherId") teacherId: UUID,
	): Assignment?

	@Query(
		"""
		select assignment
		from Assignment assignment, TeacherStudent relationship
		where assignment.teacherId = :teacherId
		  and relationship.id = assignment.teacherStudentId
		  and relationship.teacher.id = :teacherId
		  and (:studentId is null or relationship.student.id = :studentId)
		  and (:type is null or assignment.assignmentType = :type)
		  and (:status is null or assignment.status = :status)
		order by assignment.createdAt desc
		""",
	)
	fun searchForTeacher(
		@Param("teacherId") teacherId: UUID,
		@Param("studentId") studentId: UUID?,
		@Param("type") type: AssignmentType?,
		@Param("status") status: AssignmentStatus?,
		pageable: Pageable,
	): List<Assignment>
}

interface AssignmentProblemRepository : JpaRepository<AssignmentProblem, UUID> {
	fun findByAssignmentIdOrderBySortOrderAsc(assignmentId: UUID): List<AssignmentProblem>

	fun countByAssignmentId(assignmentId: UUID): Int

	@Query(
		"""
		select problem.assignmentId as assignmentId, count(problem.id) as problemCount
		from AssignmentProblem problem
		where problem.assignmentId in :assignmentIds
		group by problem.assignmentId
		""",
	)
	fun countByAssignmentIdIn(
		@Param("assignmentIds") assignmentIds: Collection<UUID>,
	): List<AssignmentProblemCountView>
}

interface AssignmentProblemCountView {
	val assignmentId: UUID
	val problemCount: Long
}

interface AssignmentTargetRepository : JpaRepository<AssignmentTarget, UUID> {
	fun findByAssignmentId(assignmentId: UUID): List<AssignmentTarget>
}

interface AssignmentSubmissionRepository : JpaRepository<AssignmentSubmission, UUID> {
	fun findByAssignmentId(assignmentId: UUID): List<AssignmentSubmission>

	fun findByAssignmentIdAndTeacherStudentId(
		assignmentId: UUID,
		teacherStudentId: UUID,
	): AssignmentSubmission?

	fun findByAssignmentIdIn(assignmentIds: Collection<UUID>): List<AssignmentSubmission>

	fun existsByAssignmentIdAndTeacherStudentId(
		assignmentId: UUID,
		teacherStudentId: UUID,
	): Boolean
}

interface SubmissionAnswerRepository : JpaRepository<SubmissionAnswer, UUID> {
	fun findBySubmissionIdAndProblemIdIn(
		submissionId: UUID,
		problemIds: Collection<UUID>,
	): List<SubmissionAnswer>
}

interface SubmissionSolutionFileRepository : JpaRepository<SubmissionSolutionFile, UUID> {
	fun findBySubmissionAnswerIdIn(submissionAnswerIds: Collection<UUID>): List<SubmissionSolutionFile>
}

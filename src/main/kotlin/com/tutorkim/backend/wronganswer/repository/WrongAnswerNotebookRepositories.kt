package com.tutorkim.backend.wronganswer.repository

import com.tutorkim.backend.wronganswer.entity.WrongAnswerNotebook
import com.tutorkim.backend.wronganswer.entity.WrongAnswerNotebookProblem
import com.tutorkim.backend.wronganswer.entity.WrongAnswerNotebookStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WrongAnswerNotebookRepository : JpaRepository<WrongAnswerNotebook, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select notebook
        from WrongAnswerNotebook notebook
        where notebook.id = :id
          and notebook.teacherId = :teacherId
        """,
    )
    fun findByIdAndTeacherIdForUpdate(
        @Param("id") id: UUID,
        @Param("teacherId") teacherId: UUID,
    ): WrongAnswerNotebook?

    fun findByTeacherStudentIdInOrderByCreatedAtDesc(teacherStudentIds: Collection<UUID>): List<WrongAnswerNotebook>

    fun findByTeacherStudentIdInAndStatusOrderByCreatedAtDesc(
        teacherStudentIds: Collection<UUID>,
        status: WrongAnswerNotebookStatus,
    ): List<WrongAnswerNotebook>
}

interface WrongAnswerNotebookProblemRepository : JpaRepository<WrongAnswerNotebookProblem, UUID> {
    fun findByNotebookIdOrderBySortOrderAsc(notebookId: UUID): List<WrongAnswerNotebookProblem>

    @Query(
        """
        select problem.notebookId as notebookId, count(problem.id) as problemCount
        from WrongAnswerNotebookProblem problem
        where problem.notebookId in :notebookIds
        group by problem.notebookId
        """,
    )
    fun countByNotebookIdIn(
        @Param("notebookIds") notebookIds: Collection<UUID>,
    ): List<WrongAnswerNotebookProblemCountView>
}

interface WrongAnswerNotebookProblemCountView {
    val notebookId: UUID
    val problemCount: Long
}

package com.tutorkim.backend.identity.repository

import com.tutorkim.backend.identity.entity.AuthSession
import com.tutorkim.backend.identity.entity.AuthSessionStatus
import com.tutorkim.backend.identity.entity.User
import com.tutorkim.backend.identity.entity.UserAuthAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID>

interface UserAuthAccountRepository : JpaRepository<UserAuthAccount, UUID>

interface AuthSessionRepository : JpaRepository<AuthSession, UUID> {
    @Query(
        """
        select session
        from AuthSession session
        join fetch session.user user
        where session.sessionTokenHash = :sessionTokenHash
          and session.status = :status
        """,
    )
    fun findActiveBySessionTokenHash(
        @Param("sessionTokenHash") sessionTokenHash: String,
        @Param("status") status: AuthSessionStatus,
    ): AuthSession?
}

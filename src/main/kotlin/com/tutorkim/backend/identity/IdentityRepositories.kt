package com.tutorkim.backend.identity

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID>

interface UserAuthAccountRepository : JpaRepository<UserAuthAccount, UUID>

interface AuthSessionRepository : JpaRepository<AuthSession, UUID>

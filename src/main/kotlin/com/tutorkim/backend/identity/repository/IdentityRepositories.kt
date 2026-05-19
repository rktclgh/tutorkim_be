package com.tutorkim.backend.identity.repository

import com.tutorkim.backend.identity.entity.AuthSession
import com.tutorkim.backend.identity.entity.User
import com.tutorkim.backend.identity.entity.UserAuthAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID>

interface UserAuthAccountRepository : JpaRepository<UserAuthAccount, UUID>

interface AuthSessionRepository : JpaRepository<AuthSession, UUID>

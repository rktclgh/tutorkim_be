package com.tutorkim.backend.identity.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcType
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import org.hibernate.type.SqlTypes
import java.net.InetAddress
import java.time.Instant
import java.util.UUID

enum class UserRole {
    STUDENT,
    TEACHER,
    ADMIN,
}

enum class AuthProvider {
    KAKAO,
    EMAIL,
}

enum class AuthSessionClientType {
    WEB,
    MOBILE,
}

enum class AuthSessionStatus {
    ACTIVE,
    REVOKED,
    EXPIRED,
}

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(name = "users_email_unique", columnNames = ["email"]),
    ],
)
class User(
    @Column(length = 255)
    var email: String? = null,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(length = 50)
    var phone: String? = null,

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(nullable = false, columnDefinition = "user_role")
    var role: UserRole,

    @Column(name = "profile_image_url", columnDefinition = "text")
    var profileImageUrl: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null

    @PreUpdate
    fun markUpdated() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(
    name = "user_auth_accounts",
    uniqueConstraints = [
        UniqueConstraint(name = "user_auth_provider_unique", columnNames = ["provider", "provider_user_id"]),
        UniqueConstraint(name = "user_auth_email_provider_unique", columnNames = ["provider", "email"]),
    ],
)
class UserAuthAccount(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(nullable = false, columnDefinition = "auth_provider")
    var provider: AuthProvider,

    @Column(name = "provider_user_id", length = 255)
    var providerUserId: String? = null,

    @Column(length = 255)
    var email: String? = null,

    @Column(name = "email_verified_at")
    var emailVerifiedAt: Instant? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun markUpdated() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(
    name = "auth_sessions",
    indexes = [
        Index(name = "auth_sessions_user_status_idx", columnList = "user_id,status"),
        Index(name = "auth_sessions_expires_idx", columnList = "expires_at"),
    ],
)
class AuthSession(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "client_type", nullable = false, columnDefinition = "auth_session_client_type")
    var clientType: AuthSessionClientType = AuthSessionClientType.WEB,

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(nullable = false, columnDefinition = "auth_session_status")
    var status: AuthSessionStatus = AuthSessionStatus.ACTIVE,

    @Column(name = "session_token_hash", length = 128)
    var sessionTokenHash: String? = null,

    @Column(name = "refresh_token_hash", length = 128)
    var refreshTokenHash: String? = null,

    @Column(name = "csrf_token_hash", length = 128)
    var csrfTokenHash: String? = null,

    @Column(name = "user_agent", columnDefinition = "text")
    var userAgent: String? = null,

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", columnDefinition = "inet")
    var ipAddress: InetAddress? = null,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @PrePersist
    @PreUpdate
    fun validateAndMarkUpdated() {
        require(!sessionTokenHash.isNullOrBlank() || !refreshTokenHash.isNullOrBlank()) {
            "Auth session requires a session or refresh token hash."
        }
        updatedAt = Instant.now()
    }
}

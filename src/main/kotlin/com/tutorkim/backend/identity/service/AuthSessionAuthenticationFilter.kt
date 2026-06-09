package com.tutorkim.backend.identity.service

import com.tutorkim.backend.identity.entity.AuthSessionStatus
import com.tutorkim.backend.identity.repository.AuthSessionRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock
import java.time.Instant

const val SESSION_COOKIE_NAME = "tutorkim_session"

@Component
class AuthSessionAuthenticationFilter(
    private val authSessionRepository: AuthSessionRepository,
    private val authTokenHasher: AuthTokenHasher,
) : OncePerRequestFilter() {
    private val clock: Clock = Clock.systemUTC()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val currentAuthentication = SecurityContextHolder.getContext().authentication
        if (currentAuthentication?.isAuthenticated == true) {
            filterChain.doFilter(request, response)
            return
        }

        val rawToken = request.cookies
            ?.firstOrNull { it.name == SESSION_COOKIE_NAME }
            ?.value
            ?.takeIf(String::isNotBlank)
        if (rawToken == null) {
            filterChain.doFilter(request, response)
            return
        }

        val session = authSessionRepository.findActiveBySessionTokenHash(
            sessionTokenHash = authTokenHasher.sha256Hex(rawToken),
            status = AuthSessionStatus.ACTIVE,
        )
        val now = Instant.now(clock)
        val user = session?.user
        if (session != null &&
            user != null &&
            session.revokedAt == null &&
            session.expiresAt.isAfter(now) &&
            user.deletedAt == null
        ) {
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                user.id!!.toString(),
                null,
                listOf(SimpleGrantedAuthority("ROLE_${user.role.name}")),
            )
        }

        filterChain.doFilter(request, response)
    }
}

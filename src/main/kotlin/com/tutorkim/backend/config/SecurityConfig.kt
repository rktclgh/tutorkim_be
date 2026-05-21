package com.tutorkim.backend.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.tutorkim.backend.common.dto.ApiEnvelope
import com.tutorkim.backend.common.dto.ApiError
import com.tutorkim.backend.common.exception.ErrorCode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig(
	private val objectMapper: ObjectMapper,
) {
	@Bean
	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
		http.authorizeHttpRequests { authorize ->
			authorize
				.requestMatchers(
					"/api/v1/health",
					"/api/v1/auth/kakao/authorize",
					"/api/v1/auth/kakao/callback",
					"/api/v1/auth/email/verification-requests",
					"/api/v1/auth/email/verify",
					"/api/v1/auth/csrf",
					"/actuator/health",
					)
					.permitAll()
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/teacher/invite-codes",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/teacher/invite-codes/active",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.DELETE,
						"/api/v1/teacher/invite-codes/*",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/student/teachers",
					)
					.hasRole("STUDENT")
					.requestMatchers(
						HttpMethod.DELETE,
						"/api/v1/students/*/teacher-relationship",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/students",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.PUT,
						"/api/v1/students/*/subjects",
					)
					.hasRole("TEACHER")
					.anyRequest()
					.authenticated()
			}
		http.exceptionHandling { exceptions ->
			exceptions
				.authenticationEntryPoint { _, response, _ ->
					if (!response.isCommitted) {
						writeError(response, ErrorCode.UNAUTHORIZED)
					}
				}
				.accessDeniedHandler { _, response, _ ->
					if (!response.isCommitted) {
						writeError(response, ErrorCode.FORBIDDEN)
					}
				}
		}

		return http.build()
	}

	private fun writeError(
		response: jakarta.servlet.http.HttpServletResponse,
		errorCode: ErrorCode,
	) {
		response.status = errorCode.status.value()
		response.contentType = MediaType.APPLICATION_JSON_VALUE
		response.characterEncoding = Charsets.UTF_8.name()
		objectMapper.writeValue(
			response.writer,
			ApiEnvelope.error(
				ApiError(
					code = errorCode,
					message = errorCode.defaultMessage,
				),
			),
		)
	}
}

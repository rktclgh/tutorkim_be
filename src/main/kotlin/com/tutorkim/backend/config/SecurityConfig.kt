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
						HttpMethod.GET,
						"/api/v1/student/assignments",
					)
					.hasRole("STUDENT")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/student/assignments/*",
					)
					.hasRole("STUDENT")
					.requestMatchers(
						HttpMethod.PATCH,
						"/api/v1/student/assignments/*/answers/*",
					)
					.hasRole("STUDENT")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/student/assignments/*/answers/*/solution-files",
					)
					.hasRole("STUDENT")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/student/assignments/*/submit",
					)
					.hasRole("STUDENT")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/student/assignments/*/submissions",
					)
					.hasRole("STUDENT")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/student/assignments/*/problems/*/questions",
					)
					.hasRole("STUDENT")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/student/wrong-answer-notebooks",
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
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/students/*/wrong-answer-notebooks",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/students/*/wrong-answer-notebook-sources",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/students/*/wrong-answers",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/students/*/wrong-answer-notebooks",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/wrong-answer-notebooks/*/publish",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/lesson-schedules",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/lesson-sessions",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/lesson-sessions/*",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.PATCH,
						"/api/v1/lesson-sessions/*/complete",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/home/timetable",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/files/upload-url",
					)
					.hasAnyRole("TEACHER", "STUDENT")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/problem-upload-batches",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/problem-upload-batches/*/files",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/problem-upload-batches/*/parse",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/problem-upload-batches/*/retry",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/problem-upload-batches/*/confirm",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/problem-upload-batches/*",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/problems",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/problems/*",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.PATCH,
						"/api/v1/problems/*",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/problems/*/teacher-solution-files",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.DELETE,
						"/api/v1/problems/*/teacher-solution-files/*",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.PATCH,
						"/api/v1/problems/*/archive",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/assignments",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/assignments",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/assignments/*",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/assignments/*/publish",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/assignments/*/release-results",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.GET,
						"/api/v1/assignments/*/questions",
					)
					.hasRole("TEACHER")
					.requestMatchers(
						HttpMethod.POST,
						"/api/v1/assignments/*/questions/*/teacher-solution-files",
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

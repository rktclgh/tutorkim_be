package com.tutorkim.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {
	@Bean
	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
		http.authorizeHttpRequests { authorize ->
			authorize
				.requestMatchers("/api/v1/health", "/actuator/health")
				.permitAll()
				.anyRequest()
				.authenticated()
		}

		return http.build()
	}
}

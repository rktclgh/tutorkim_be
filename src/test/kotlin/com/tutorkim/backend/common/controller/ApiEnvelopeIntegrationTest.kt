package com.tutorkim.backend.common.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tutorkim.backend.common.dto.ApiEnvelope
import com.tutorkim.backend.common.dto.ErrorDetail
import com.tutorkim.backend.common.exception.ApiException
import com.tutorkim.backend.common.exception.ErrorCode
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals

@SpringBootTest
@AutoConfigureMockMvc
class ApiEnvelopeIntegrationTest @Autowired constructor(
	private val mockMvc: MockMvc,
) {
	private val objectMapper = jacksonObjectMapper()

	@Test
	fun `api success responses are wrapped in the common envelope`() {
		mockMvc.get("/api/v1/health")
			.andExpect {
				status { isOk() }
				jsonPath("$.data.status") { value("ok") }
				jsonPath("$.meta") { exists() }
				jsonPath("$.error") { isEmpty() }
			}
	}

	@Test
	@WithMockUser
	fun `api exceptions are mapped to error envelopes`() {
		mockMvc.get("/api/v1/test/api-exception")
			.andExpect {
				status { isBadRequest() }
				jsonPath("$.data") { isEmpty() }
				jsonPath("$.meta") { exists() }
				jsonPath("$.error.code") { value("INVALID_REQUEST") }
				jsonPath("$.error.message") { value("요청 값이 올바르지 않습니다.") }
				jsonPath("$.error.details[0].field") { value("id") }
				jsonPath("$.error.details[0].message") { value("유효하지 않은 식별자입니다.") }
			}
	}

	@Test
	@WithMockUser
	fun `validation failures are mapped to field error envelopes`() {
		val body = objectMapper.writeValueAsString(TestRequest(name = ""))

		mockMvc.post("/api/v1/test/validation") {
			with(csrf())
			contentType = MediaType.APPLICATION_JSON
			content = body
		}.andExpect {
			status { isBadRequest() }
			jsonPath("$.data") { isEmpty() }
			jsonPath("$.meta") { exists() }
			jsonPath("$.error.code") { value("VALIDATION_ERROR") }
			jsonPath("$.error.message") { value("입력값을 확인해 주세요.") }
			jsonPath("$.error.details[0].field") { value("name") }
			jsonPath("$.error.details[0].message") { value("이름을 입력해 주세요.") }
		}
	}

	@Test
	fun `actuator health remains public`() {
		val response = mockMvc.get("/actuator/health")
			.andReturn()
			.response

		assertNotEquals(401, response.status)
		assertNotEquals(403, response.status)
	}

	@Test
	fun `security failures are mapped to error envelopes`() {
		mockMvc.get("/api/v1/test/with-meta")
			.andExpect {
				status { isUnauthorized() }
				jsonPath("$.data") { isEmpty() }
				jsonPath("$.meta") { exists() }
				jsonPath("$.error.code") { value("UNAUTHORIZED") }
				jsonPath("$.error.message") { value("인증이 필요합니다.") }
			}
	}

	@Test
	@WithMockUser
	fun `success helper can include metadata`() {
		mockMvc.get("/api/v1/test/with-meta")
			.andExpect {
				status { isOk() }
				jsonPath("$.data.value") { value("ok") }
				jsonPath("$.meta.requestId") { value("test-request") }
				jsonPath("$.error") { isEmpty() }
			}
	}

	@Test
	@WithMockUser
	fun `string api responses are serialized as json envelopes`() {
		mockMvc.get("/api/v1/test/string")
			.andExpect {
				status { isOk() }
				content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
				jsonPath("$.data") { value("plain") }
				jsonPath("$.meta") { exists() }
				jsonPath("$.error") { isEmpty() }
			}
	}

	@Test
	@WithMockUser
	fun `empty successful responses are not force wrapped`() {
		mockMvc.get("/api/v1/test/no-content")
			.andExpect {
				status { isNoContent() }
				content { string("") }
			}
	}

	@Test
	@WithMockUser
	fun `non successful response entities are wrapped as error envelopes`() {
		mockMvc.get("/api/v1/test/non-success")
			.andExpect {
				status { isBadRequest() }
				jsonPath("$.data") { isEmpty() }
				jsonPath("$.meta") { exists() }
				jsonPath("$.error.code") { value("INVALID_REQUEST") }
				jsonPath("$.error.message") { value("요청 값이 올바르지 않습니다.") }
			}
	}

	@Test
	@WithMockUser
	fun `malformed json is mapped to an error envelope`() {
		mockMvc.post("/api/v1/test/validation") {
			with(csrf())
			contentType = MediaType.APPLICATION_JSON
			content = "{"
		}.andExpect {
			status { isBadRequest() }
			jsonPath("$.data") { isEmpty() }
			jsonPath("$.meta") { exists() }
			jsonPath("$.error.code") { value("INVALID_REQUEST") }
			jsonPath("$.error.message") { value("요청 값이 올바르지 않습니다.") }
		}
	}

	@Test
	@WithMockUser
	fun `unsupported api methods are mapped to error envelopes`() {
		mockMvc.put("/api/v1/test/validation") {
			with(csrf())
			contentType = MediaType.APPLICATION_JSON
			content = "{}"
		}.andExpect {
			status { isBadRequest() }
			jsonPath("$.data") { isEmpty() }
			jsonPath("$.meta") { exists() }
			jsonPath("$.error.code") { value("INVALID_REQUEST") }
		}
	}

	@Test
	@WithMockUser
	fun `unexpected api errors are mapped to internal error envelopes`() {
		mockMvc.get("/api/v1/test/unhandled")
			.andExpect {
				status { isInternalServerError() }
				jsonPath("$.data") { isEmpty() }
				jsonPath("$.meta") { exists() }
				jsonPath("$.error.code") { value("INTERNAL_SERVER_ERROR") }
				jsonPath("$.error.message") { value("서버 오류가 발생했습니다.") }
			}
	}

	@Test
	@WithMockUser
	fun `missing api routes are mapped to not found envelopes`() {
		mockMvc.get("/api/v1/test/missing-route")
			.andExpect {
				status { isNotFound() }
				jsonPath("$.data") { isEmpty() }
				jsonPath("$.meta") { exists() }
				jsonPath("$.error.code") { value("NOT_FOUND") }
				jsonPath("$.error.message") { value("요청한 리소스를 찾을 수 없습니다.") }
			}
	}

	@Test
	fun `auth bootstrap routes are public`() {
		mockMvc.get("/api/v1/auth/csrf")
			.andExpect {
				status { isNotFound() }
			}

		mockMvc.post("/api/v1/auth/kakao/callback") {
			with(csrf())
			contentType = MediaType.APPLICATION_JSON
			content = "{}"
		}.andExpect {
			status { isNotFound() }
		}

		mockMvc.post("/api/v1/auth/email/verification-requests") {
			with(csrf())
			contentType = MediaType.APPLICATION_JSON
			content = "{}"
		}.andExpect {
			status { isNotFound() }
		}
	}

	@TestConfiguration
	class TestControllerConfiguration {
		@Bean
		fun apiEnvelopeTestController(): ApiEnvelopeTestController = ApiEnvelopeTestController()
	}

	@RestController
	@RequestMapping("/api/v1/test")
	class ApiEnvelopeTestController {
		@GetMapping("/api-exception")
		fun apiException(): Nothing {
			throw ApiException(
				errorCode = ErrorCode.INVALID_REQUEST,
				details = listOf(ErrorDetail(field = "id", message = "유효하지 않은 식별자입니다.")),
			)
		}

		@PostMapping("/validation")
		fun validation(
			@Valid @RequestBody request: TestRequest,
		): Map<String, String> {
			assertNotNull(request.name)
			return mapOf("name" to request.name)
		}

		@GetMapping("/with-meta")
		fun withMeta(): ApiEnvelope<Map<String, String>> =
			ApiEnvelope.success(
				data = mapOf("value" to "ok"),
				meta = mapOf("requestId" to "test-request"),
			)

		@GetMapping("/string")
		fun string(): String = "plain"

		@GetMapping("/no-content")
		fun noContent(): ResponseEntity<Void> = ResponseEntity.noContent().build()

		@GetMapping("/non-success")
		fun nonSuccess(): ResponseEntity<Map<String, String>> =
			ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("reason" to "bad"))

		@GetMapping("/unhandled")
		fun unhandled(): Nothing {
			error("boom")
		}
	}

	data class TestRequest(
		@field:NotBlank(message = "이름을 입력해 주세요.")
		val name: String?,
	)
}

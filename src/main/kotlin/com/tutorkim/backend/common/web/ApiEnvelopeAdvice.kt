package com.tutorkim.backend.common.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.tutorkim.backend.common.dto.ApiEnvelope
import com.tutorkim.backend.common.dto.ApiError
import com.tutorkim.backend.common.exception.ErrorCode
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

@RestControllerAdvice
class ApiEnvelopeAdvice(
	private val objectMapper: ObjectMapper,
) : ResponseBodyAdvice<Any> {
	override fun supports(
		returnType: MethodParameter,
		converterType: Class<out HttpMessageConverter<*>>,
	): Boolean = true

	override fun beforeBodyWrite(
		body: Any?,
		returnType: MethodParameter,
		selectedContentType: MediaType,
		selectedConverterType: Class<out HttpMessageConverter<*>>,
		request: ServerHttpRequest,
		response: ServerHttpResponse,
	): Any? {
		if (!isApiRequest(request) || body is ApiEnvelope<*>) {
			return body
		}

		val status = responseStatus(response)
		if (status == 204) {
			return body
		}

		val envelope = if (status in 200..299) {
			ApiEnvelope.success(data = body)
		} else {
			val errorCode = ErrorCode.fromStatus(status)
			ApiEnvelope.error(
				ApiError(
					code = errorCode,
					message = (body as? String) ?: errorCode.defaultMessage,
				),
			)
		}
		if (StringHttpMessageConverter::class.java.isAssignableFrom(selectedConverterType)) {
			response.headers.contentType = MediaType.APPLICATION_JSON
			return objectMapper.writeValueAsString(envelope)
		}

		return envelope
	}

	private fun isApiRequest(request: ServerHttpRequest): Boolean {
		val servletRequest = request as? ServletServerHttpRequest ?: return false
		return servletRequest.servletRequest.requestURI.startsWith(API_PREFIX)
	}

	private fun responseStatus(response: ServerHttpResponse): Int {
		val servletResponse = response as? ServletServerHttpResponse ?: return 200
		return servletResponse.servletResponse.status
	}

}

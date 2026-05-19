package com.tutorkim.backend.common

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ApiErrorController : ErrorController {
	@RequestMapping("/error")
	fun error(request: HttpServletRequest): ResponseEntity<ApiEnvelope<Nothing>> {
		val status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as? Int
			?: ErrorCode.INTERNAL_SERVER_ERROR.status.value()
		val errorCode = errorCodeForStatus(status)

		return ResponseEntity
			.status(errorCode.status)
			.body(
				ApiEnvelope.error(
					ApiError(
						code = errorCode,
						message = errorCode.defaultMessage,
					),
				),
			)
	}

	private fun errorCodeForStatus(status: Int): ErrorCode =
		when (status) {
			400 -> ErrorCode.INVALID_REQUEST
			401 -> ErrorCode.UNAUTHORIZED
			403 -> ErrorCode.FORBIDDEN
			404 -> ErrorCode.NOT_FOUND
			409 -> ErrorCode.CONFLICT
			in 400..499 -> ErrorCode.INVALID_REQUEST
			else -> ErrorCode.INTERNAL_SERVER_ERROR
		}
}

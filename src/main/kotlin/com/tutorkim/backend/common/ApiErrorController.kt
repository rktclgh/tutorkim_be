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
		val errorCode = ErrorCode.fromStatus(status)

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

}

package com.tutorkim.backend.common.exception

import com.tutorkim.backend.common.dto.ApiEnvelope
import com.tutorkim.backend.common.dto.ApiError
import com.tutorkim.backend.common.dto.ErrorDetail
import com.tutorkim.backend.common.web.API_PREFIX
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
	@ExceptionHandler(ApiException::class)
	fun handleApiException(exception: ApiException): ResponseEntity<ApiEnvelope<Nothing>> {
		val errorCode = exception.errorCode
		return ResponseEntity
			.status(errorCode.status)
			.body(
				ApiEnvelope.error(
					ApiError(
						code = errorCode,
						message = exception.message,
						details = exception.details,
					),
				),
			)
	}

	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun handleValidationException(exception: MethodArgumentNotValidException): ResponseEntity<ApiEnvelope<Nothing>> {
		val errorCode = ErrorCode.VALIDATION_ERROR
		val details = exception.bindingResult.fieldErrors.map { fieldError ->
			ErrorDetail(
				field = fieldError.field,
				message = fieldError.defaultMessage ?: errorCode.defaultMessage,
			)
		}

		return ResponseEntity
			.status(errorCode.status)
			.body(
				ApiEnvelope.error(
					ApiError(
						code = errorCode,
						message = errorCode.defaultMessage,
						details = details,
					),
				),
			)
	}

	@ExceptionHandler(
		HttpMessageNotReadableException::class,
		HttpMediaTypeNotSupportedException::class,
		HttpRequestMethodNotSupportedException::class,
	)
	fun handleBadRequestFrameworkException(): ResponseEntity<ApiEnvelope<Nothing>> =
		errorResponse(ErrorCode.INVALID_REQUEST)

	@ExceptionHandler(ResponseStatusException::class)
	fun handleResponseStatusException(exception: ResponseStatusException): ResponseEntity<ApiEnvelope<Nothing>> {
		val errorCode = ErrorCode.fromStatus(exception.statusCode.value())
		return errorResponse(errorCode, exception.reason ?: errorCode.defaultMessage)
	}

	@ExceptionHandler(NoHandlerFoundException::class, NoResourceFoundException::class)
	fun handleNotFoundException(): ResponseEntity<ApiEnvelope<Nothing>> =
		errorResponse(ErrorCode.NOT_FOUND)

	@ExceptionHandler(Exception::class)
	fun handleUnhandledException(
		exception: Exception,
		request: WebRequest,
	): ResponseEntity<ApiEnvelope<Nothing>> {
		if (request !is ServletWebRequest || !request.request.requestURI.startsWith(API_PREFIX)) {
			throw exception
		}

		val errorCode = ErrorCode.INTERNAL_SERVER_ERROR
		return errorResponse(errorCode)
	}

	private fun errorResponse(
		errorCode: ErrorCode,
		message: String = errorCode.defaultMessage,
	): ResponseEntity<ApiEnvelope<Nothing>> {
		return ResponseEntity
			.status(errorCode.status)
			.body(
				ApiEnvelope.error(
					ApiError(
						code = errorCode,
						message = message,
					),
				),
			)
	}

}

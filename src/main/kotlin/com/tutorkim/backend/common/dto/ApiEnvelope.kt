package com.tutorkim.backend.common.dto

import com.tutorkim.backend.common.exception.ErrorCode

data class ApiEnvelope<T>(
	val data: T? = null,
	val meta: Map<String, Any?> = emptyMap(),
	val error: ApiError? = null,
) {
	companion object {
		fun <T> success(
			data: T,
			meta: Map<String, Any?> = emptyMap(),
		): ApiEnvelope<T> = ApiEnvelope(data = data, meta = meta)

		fun error(
			error: ApiError,
			meta: Map<String, Any?> = emptyMap(),
		): ApiEnvelope<Nothing> = ApiEnvelope(meta = meta, error = error)
	}
}

data class ApiError(
	val code: ErrorCode,
	val message: String,
	val details: List<ErrorDetail> = emptyList(),
)

data class ErrorDetail(
	val field: String? = null,
	val message: String,
)

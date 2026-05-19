package com.tutorkim.backend.common.exception

import com.tutorkim.backend.common.dto.ErrorDetail

class ApiException(
	val errorCode: ErrorCode,
	override val message: String = errorCode.defaultMessage,
	val details: List<ErrorDetail> = emptyList(),
	cause: Throwable? = null,
) : RuntimeException(message, cause)

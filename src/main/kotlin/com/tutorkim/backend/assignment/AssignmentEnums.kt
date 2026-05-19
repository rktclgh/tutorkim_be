package com.tutorkim.backend.assignment

enum class AssignmentType {
	HOMEWORK,
	TEST,
	REVIEW_SET,
}

enum class AssignmentStatus {
	DRAFT,
	PUBLISHED,
	CLOSED,
	ARCHIVED,
}

enum class SubmissionStatus {
	NOT_SUBMITTED,
	PARTIAL,
	SUBMITTED,
	LATE_SUBMITTED,
}

enum class GradingStatus {
	NOT_GRADED,
	AUTO_GRADED,
	MANUALLY_ADJUSTED,
}

enum class ResultVisibility {
	IMMEDIATE,
	HIDDEN_UNTIL_RELEASED,
	RELEASED,
}

enum class AnswerType {
	SINGLE_CHOICE,
	MULTIPLE_CHOICE,
	NUMERIC,
}

enum class GradingDecision {
	AUTO_GRADED,
	UNKNOWN,
	MANUAL_REVIEW_REQUIRED,
}

enum class ProblemAttemptStatus {
	CORRECT_FIRST,
	WRONG_FIRST,
	CORRECT_RETRY,
	UNKNOWN,
	PENDING,
}

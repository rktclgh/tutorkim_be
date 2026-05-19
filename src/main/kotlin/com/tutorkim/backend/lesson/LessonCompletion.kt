package com.tutorkim.backend.lesson

import java.util.UUID

data class LessonProgress(
	val previousProgress: String?,
	val currentProgress: String,
	val nextProgress: String?,
	val currentCurriculumNodeId: UUID?,
)

data class LessonEvaluation(
	val focusLevel: FocusLevel,
	val understandingLevel: UnderstandingLevel,
	val assignmentPerformance: AssignmentPerformance,
)

data class LessonCompletion(
	val progress: LessonProgress,
	val evaluation: LessonEvaluation,
	val lessonMemo: String?,
)

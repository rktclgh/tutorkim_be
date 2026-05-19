do $$
begin
	if not exists (select 1 from pg_type where typname = 'user_role') then
		create type user_role as enum ('STUDENT', 'TEACHER', 'ADMIN');
	end if;
	if not exists (select 1 from pg_type where typname = 'auth_provider') then
		create type auth_provider as enum ('KAKAO', 'EMAIL');
	end if;
	if not exists (select 1 from pg_type where typname = 'auth_session_client_type') then
		create type auth_session_client_type as enum ('WEB', 'MOBILE');
	end if;
	if not exists (select 1 from pg_type where typname = 'auth_session_status') then
		create type auth_session_status as enum ('ACTIVE', 'REVOKED', 'EXPIRED');
	end if;
	if not exists (select 1 from pg_type where typname = 'invite_code_status') then
		create type invite_code_status as enum ('ACTIVE', 'EXPIRED', 'REVOKED');
	end if;
	if not exists (select 1 from pg_type where typname = 'upload_source_type') then
		create type upload_source_type as enum ('PAGE_IMAGE', 'PROBLEM_IMAGE', 'TEXT', 'ANSWER_IMAGE', 'EXPLANATION_IMAGE', 'PDF');
	end if;
	if not exists (select 1 from pg_type where typname = 'parse_status') then
		create type parse_status as enum ('PENDING', 'PROCESSING', 'NEEDS_REVIEW', 'REVIEWED', 'FAILED');
	end if;
	if not exists (select 1 from pg_type where typname = 'answer_type') then
		create type answer_type as enum ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'NUMERIC');
	end if;
	if not exists (select 1 from pg_type where typname = 'block_type') then
		create type block_type as enum ('TEXT', 'MATH', 'CHOICE_LIST', 'DIAGRAM_IMAGE', 'TABLE_IMAGE', 'EXPLANATION');
	end if;
	if not exists (select 1 from pg_type where typname = 'explanation_source_type') then
		create type explanation_source_type as enum ('TEACHER_SOLUTION_IMAGE', 'TEACHER_TEXT', 'AI_GENERATED');
	end if;
	if not exists (select 1 from pg_type where typname = 'assignment_type') then
		create type assignment_type as enum ('HOMEWORK', 'TEST', 'REVIEW_SET');
	end if;
	if not exists (select 1 from pg_type where typname = 'assignment_status') then
		create type assignment_status as enum ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED');
	end if;
	if not exists (select 1 from pg_type where typname = 'submission_status') then
		create type submission_status as enum ('NOT_SUBMITTED', 'PARTIAL', 'SUBMITTED', 'LATE_SUBMITTED');
	end if;
	if not exists (select 1 from pg_type where typname = 'grading_status') then
		create type grading_status as enum ('NOT_GRADED', 'AUTO_GRADED', 'MANUALLY_ADJUSTED');
	end if;
	if not exists (select 1 from pg_type where typname = 'result_visibility') then
		create type result_visibility as enum ('IMMEDIATE', 'HIDDEN_UNTIL_RELEASED', 'RELEASED');
	end if;
	if not exists (select 1 from pg_type where typname = 'lesson_status') then
		create type lesson_status as enum ('SCHEDULED', 'COMPLETED', 'CANCELLED');
	end if;
	if not exists (select 1 from pg_type where typname = 'focus_level') then
		create type focus_level as enum ('HIGH', 'MEDIUM', 'LOW');
	end if;
	if not exists (select 1 from pg_type where typname = 'understanding_level') then
		create type understanding_level as enum ('HIGH', 'MEDIUM', 'LOW');
	end if;
	if not exists (select 1 from pg_type where typname = 'assignment_performance') then
		create type assignment_performance as enum ('GOOD', 'AVERAGE', 'POOR');
	end if;
	if not exists (select 1 from pg_type where typname = 'problem_attempt_status') then
		create type problem_attempt_status as enum ('CORRECT_FIRST', 'WRONG_FIRST', 'CORRECT_RETRY', 'UNKNOWN', 'PENDING');
	end if;
end $$;;

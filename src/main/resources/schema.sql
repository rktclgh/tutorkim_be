do $$
begin
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

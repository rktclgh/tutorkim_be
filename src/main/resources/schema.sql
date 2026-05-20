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
	if not exists (select 1 from pg_type where typname = 'ingestion_stage_type') then
		create type ingestion_stage_type as enum ('PDF_TEXT_EXTRACTION', 'PDF_BOX_EXTRACTION', 'OCR', 'LAYOUT_SEGMENTATION', 'SEMANTIC_GROUPING', 'PROBLEM_BOUNDARY_DETECTION', 'ANSWER_MAPPING', 'HERMES_VISUAL_SEMANTIC_REVIEW', 'HERMES_TARGETED_REPAIR', 'REVIEW_TASK_GENERATION');
	end if;
	if not exists (select 1 from pg_type where typname = 'ingestion_stage_status') then
		create type ingestion_stage_status as enum ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED');
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

do $$
begin
	if to_regclass('public.problem_upload_batches') is not null then
		execute 'alter table problem_upload_batches
			add column if not exists pipeline_version varchar(80),
			add column if not exists deterministic_coverage_rate numeric,
			add column if not exists hermes_review_rate numeric,
			add column if not exists hermes_targeted_repair_rate numeric,
			add column if not exists average_confidence numeric';

		execute 'create table if not exists document_ingestion_stage_runs (
			id uuid primary key default gen_random_uuid(),
			batch_id uuid not null references problem_upload_batches(id) on delete cascade,
			stage_type ingestion_stage_type not null,
			status ingestion_stage_status not null default ''PENDING'',
			engine_name varchar(120),
			confidence numeric,
			input_artifact_ids uuid[] not null default ''{}''::uuid[],
			output_json jsonb not null default ''{}''::jsonb,
			error_message text,
			started_at timestamptz,
			completed_at timestamptz,
			created_at timestamptz not null default now()
		)';

		execute 'create index if not exists document_ingestion_stage_runs_batch_idx
			on document_ingestion_stage_runs (batch_id, stage_type)';
	end if;

	if to_regclass('public.problem_upload_batches') is not null
		and to_regclass('public.problem_upload_files') is not null
		and to_regclass('public.document_ingestion_stage_runs') is not null
		and to_regclass('public.file_assets') is not null then
		execute 'create table if not exists document_ingestion_artifacts (
			id uuid primary key default gen_random_uuid(),
			batch_id uuid not null references problem_upload_batches(id) on delete cascade,
			upload_file_id uuid references problem_upload_files(id),
			stage_run_id uuid references document_ingestion_stage_runs(id),
			artifact_type varchar(80) not null,
			page_number integer,
			bounding_box jsonb,
			text_content text,
			latex_content text,
			file_asset_id uuid references file_assets(id),
			confidence numeric,
			metadata jsonb not null default ''{}''::jsonb,
			created_at timestamptz not null default now()
		)';

		execute 'create index if not exists document_ingestion_artifacts_batch_idx
			on document_ingestion_artifacts (batch_id, artifact_type)';
	end if;
end $$;;

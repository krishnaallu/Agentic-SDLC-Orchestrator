ALTER TABLE orchestration_run ADD COLUMN failure_reason TEXT;
ALTER TABLE orchestration_run ADD COLUMN original_requirement TEXT NOT NULL DEFAULT '';
UPDATE orchestration_run SET original_requirement = requirement;
ALTER TABLE orchestration_run ALTER COLUMN original_requirement DROP DEFAULT;
ALTER TABLE orchestration_run ADD COLUMN codebase_context TEXT NOT NULL DEFAULT '';
ALTER TABLE orchestration_run ALTER COLUMN codebase_context DROP DEFAULT;
ALTER TABLE orchestration_run ADD COLUMN clarification_notes TEXT NOT NULL DEFAULT '';
ALTER TABLE orchestration_run ALTER COLUMN clarification_notes DROP DEFAULT;
ALTER TABLE orchestration_run ADD COLUMN plan_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE orchestration_run ADD COLUMN started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orchestration_run ADD COLUMN finished_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orchestration_run ADD COLUMN total_recovery_millis BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orchestration_run ADD COLUMN recovery_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE orchestration_task ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE orchestration_task ADD COLUMN output_summary TEXT;
ALTER TABLE orchestration_task ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE orchestration_artifact (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES orchestration_run(id) ON DELETE CASCADE,
    task_key VARCHAR(80) NOT NULL,
    artifact_path VARCHAR(500) NOT NULL,
    media_type VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (run_id, artifact_path)
);

CREATE INDEX orchestration_artifact_run_idx ON orchestration_artifact(run_id, artifact_path);
CREATE TABLE orchestration_run (
    id UUID PRIMARY KEY,
    requirement TEXT NOT NULL,
    scenario VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    decision_actor VARCHAR(200),
    decision_note TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE orchestration_task (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES orchestration_run(id) ON DELETE CASCADE,
    node_key VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    dependencies JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    sequence_number INTEGER NOT NULL,
    UNIQUE (run_id, node_key)
);

CREATE INDEX orchestration_task_run_idx ON orchestration_task(run_id, sequence_number);

CREATE TABLE orchestration_audit_event (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES orchestration_run(id) ON DELETE CASCADE,
    action VARCHAR(80) NOT NULL,
    actor VARCHAR(200) NOT NULL,
    details TEXT NOT NULL,
    happened_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX orchestration_audit_run_idx ON orchestration_audit_event(run_id, happened_at);
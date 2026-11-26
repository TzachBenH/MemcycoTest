-- One row per execution attempt. Inserted as RUNNING when a run starts, updated when it ends.

CREATE TABLE task_executions
(
    id                     UUID PRIMARY KEY,
    scheduling_id          UUID         NOT NULL REFERENCES schedulings (id) ON DELETE CASCADE,
    task_key               VARCHAR(50)  NOT NULL,
    -- Snapshot of the parameters this run actually used (the scheduling may be edited later).
    parameters             JSONB        NOT NULL,
    -- SHA-256 (hex) of task_key + canonical parameters; the runtime-lock key.
    run_key                VARCHAR(64)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL
        CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED', 'STUCK')),
    trigger_type           VARCHAR(20)  NOT NULL CHECK (trigger_type IN ('SCHEDULED', 'MANUAL', 'RETRY')),
    attempt                INT          NOT NULL CHECK (attempt >= 1),
    previous_attempt_id    UUID         REFERENCES task_executions (id) ON DELETE SET NULL,
    next_retry_at          TIMESTAMPTZ,
    retry_cancelled_reason VARCHAR(20)
        CHECK (retry_cancelled_reason IN ('SUPERSEDED', 'PAUSED', 'EDITED', 'DELETED')),
    -- Which backend instance ran it (cluster demo).
    executed_by            VARCHAR(100) NOT NULL,
    started_at             TIMESTAMPTZ  NOT NULL,
    last_heartbeat_at      TIMESTAMPTZ  NOT NULL,
    finished_at            TIMESTAMPTZ,
    error_message          TEXT
);

-- Runtime lock: the same scheduling cannot run the same task + parameters twice at the same time.
CREATE UNIQUE INDEX ux_task_executions_one_running
    ON task_executions (scheduling_id, run_key) WHERE status = 'RUNNING';

-- History page / "last run" lookups.
CREATE INDEX ix_task_executions_scheduling_started ON task_executions (scheduling_id, started_at DESC);

-- Stuck-execution monitor only looks at RUNNING rows.
CREATE INDEX ix_task_executions_running_heartbeat
    ON task_executions (last_heartbeat_at) WHERE status = 'RUNNING';

-- Retention cleanup.
CREATE INDEX ix_task_executions_started ON task_executions (started_at);

-- Retry chains: finding an attempt's successor, and keeping deletes fast (ON DELETE SET NULL looks rows up by it).
CREATE INDEX ix_task_executions_previous_attempt
    ON task_executions (previous_attempt_id) WHERE previous_attempt_id IS NOT NULL;

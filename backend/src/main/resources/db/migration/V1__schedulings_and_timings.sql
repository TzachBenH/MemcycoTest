-- Timing: a JOINED inheritance hierarchy. schedule_timings is the base row (type + timezone);
-- exactly one child table holds the type-specific columns, sharing the same id. No nullable timing columns.

CREATE TABLE schedule_timings
(
    id       UUID PRIMARY KEY,
    type     VARCHAR(20) NOT NULL CHECK (type IN ('ONE_TIME', 'INTERVAL', 'WEEKLY', 'CRON')),
    timezone VARCHAR(50) NOT NULL
);

CREATE TABLE one_time_timings
(
    id     UUID PRIMARY KEY REFERENCES schedule_timings (id) ON DELETE CASCADE,
    run_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE interval_timings
(
    id             UUID PRIMARY KEY REFERENCES schedule_timings (id) ON DELETE CASCADE,
    interval_value INT         NOT NULL CHECK (interval_value > 0),
    interval_unit  VARCHAR(10) NOT NULL CHECK (interval_unit IN ('MINUTES', 'HOURS')),
    start_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE weekly_timings
(
    id          UUID PRIMARY KEY REFERENCES schedule_timings (id) ON DELETE CASCADE,
    time_of_day TIME NOT NULL
);

CREATE TABLE weekly_timing_days
(
    timing_id   UUID       NOT NULL REFERENCES weekly_timings (id) ON DELETE CASCADE,
    day_of_week VARCHAR(3) NOT NULL CHECK (day_of_week IN ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN')),
    PRIMARY KEY (timing_id, day_of_week)
);

CREATE TABLE cron_timings
(
    id              UUID PRIMARY KEY REFERENCES schedule_timings (id) ON DELETE CASCADE,
    cron_expression VARCHAR(120) NOT NULL
);

-- A scheduling has exactly one timing (composition). Replacing the timing keeps the scheduling's id and history.
CREATE TABLE schedulings
(
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    task_key    VARCHAR(50)  NOT NULL,
    parameters  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    enabled     BOOLEAN      NOT NULL,
    timing_id   UUID         NOT NULL UNIQUE REFERENCES schedule_timings (id),
    -- SHA-256 (hex) of task_key + canonical parameters + canonical timing; see FingerprintService.
    fingerprint VARCHAR(64)  NOT NULL,
    version     BIGINT       NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(100) NOT NULL,
    updated_by  VARCHAR(100) NOT NULL
);

-- Only ACTIVE schedulings must be unique; a paused duplicate is allowed (resuming it is then blocked).
CREATE UNIQUE INDEX ux_schedulings_active_fingerprint ON schedulings (fingerprint) WHERE enabled = TRUE;

CREATE INDEX ix_schedulings_task_key ON schedulings (task_key);
CREATE INDEX ix_schedulings_name_lower ON schedulings (LOWER(name));

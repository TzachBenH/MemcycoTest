-- Exclusive edit lock (lease): at most one editor per scheduling. The editor renews it every 30s;
-- an expired lock can be taken over by someone else.

CREATE TABLE scheduling_edit_locks
(
    scheduling_id UUID PRIMARY KEY REFERENCES schedulings (id) ON DELETE CASCADE,
    lock_token    UUID         NOT NULL,
    locked_by     VARCHAR(100) NOT NULL,
    acquired_at   TIMESTAMPTZ  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL
);

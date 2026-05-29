# --- !Ups

ALTER TABLE todo_event_outbox
ADD replay_count INT NOT NULL DEFAULT 0;

ALTER TABLE todo_event_outbox
ADD last_replayed_at DATETIME2 NULL;

ALTER TABLE todo_event_outbox
ADD last_replayed_by_user_id UNIQUEIDENTIFIER NULL;

CREATE TABLE todo_event_outbox_replay_log (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    outbox_id UNIQUEIDENTIFIER NOT NULL,
    tenant_id UNIQUEIDENTIFIER NOT NULL,
    requested_by_user_id UNIQUEIDENTIFIER NOT NULL,
    event_type NVARCHAR(100) NOT NULL,
    replay_mode NVARCHAR(20) NOT NULL,
    filter_summary NVARCHAR(1000) NULL,
    replayed_at DATETIME2 NOT NULL,
    created_at DATETIME2 NOT NULL
);

ALTER TABLE todo_event_outbox_replay_log
ADD CONSTRAINT chk_todo_event_outbox_replay_log_mode
CHECK (replay_mode IN ('SINGLE', 'BULK'));

CREATE INDEX idx_todo_event_outbox_replay_log_tenant_replayed_at
ON todo_event_outbox_replay_log(tenant_id, replayed_at DESC);

CREATE INDEX idx_todo_event_outbox_replay_log_outbox_id
ON todo_event_outbox_replay_log(outbox_id);

# --- !Downs

DROP INDEX idx_todo_event_outbox_replay_log_outbox_id ON todo_event_outbox_replay_log;
DROP INDEX idx_todo_event_outbox_replay_log_tenant_replayed_at ON todo_event_outbox_replay_log;
ALTER TABLE todo_event_outbox_replay_log DROP CONSTRAINT chk_todo_event_outbox_replay_log_mode;
DROP TABLE IF EXISTS todo_event_outbox_replay_log;

ALTER TABLE todo_event_outbox DROP COLUMN last_replayed_by_user_id;
ALTER TABLE todo_event_outbox DROP COLUMN last_replayed_at;
ALTER TABLE todo_event_outbox DROP COLUMN replay_count;

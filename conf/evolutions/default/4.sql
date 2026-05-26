# --- !Ups

CREATE TABLE todo_event_outbox (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    aggregate_type NVARCHAR(100) NOT NULL,
    aggregate_id UNIQUEIDENTIFIER NOT NULL,
    event_type NVARCHAR(100) NOT NULL,
    event_version INT NOT NULL,
    tenant_id UNIQUEIDENTIFIER NOT NULL,
    user_id UNIQUEIDENTIFIER NOT NULL,
    payload_json NVARCHAR(MAX) NOT NULL,
    headers_json NVARCHAR(MAX) NOT NULL,
    status NVARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME2 NOT NULL,
    published_at DATETIME2 NULL,
    last_error NVARCHAR(1000) NULL,
    created_at DATETIME2 NOT NULL
);

ALTER TABLE todo_event_outbox
ADD CONSTRAINT chk_todo_event_outbox_status
CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'));

CREATE INDEX idx_todo_event_outbox_status_available
ON todo_event_outbox(status, available_at);

CREATE INDEX idx_todo_event_outbox_aggregate_id
ON todo_event_outbox(aggregate_id);

CREATE INDEX idx_todo_event_outbox_created_at
ON todo_event_outbox(created_at);

# --- !Downs

DROP INDEX idx_todo_event_outbox_created_at ON todo_event_outbox;
DROP INDEX idx_todo_event_outbox_aggregate_id ON todo_event_outbox;
DROP INDEX idx_todo_event_outbox_status_available ON todo_event_outbox;
ALTER TABLE todo_event_outbox DROP CONSTRAINT chk_todo_event_outbox_status;
DROP TABLE IF EXISTS todo_event_outbox;

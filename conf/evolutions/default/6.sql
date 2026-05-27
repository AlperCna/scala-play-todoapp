# --- !Ups

CREATE TABLE tenant_todo_analytics_projection (
    todo_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    tenant_id UNIQUEIDENTIFIER NOT NULL,
    user_id UNIQUEIDENTIFIER NOT NULL,
    title NVARCHAR(200) NOT NULL,
    description NVARCHAR(MAX) NULL,
    due_date DATETIME2 NULL,
    is_completed BIT NOT NULL DEFAULT 0,
    is_deleted BIT NOT NULL DEFAULT 0,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NULL,
    completed_at DATETIME2 NULL,
    deleted_at DATETIME2 NULL,
    last_event_type NVARCHAR(100) NOT NULL,
    last_event_at DATETIME2 NOT NULL
);

CREATE TABLE tenant_todo_analytics_summary (
    tenant_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    total_tracked_todos INT NOT NULL DEFAULT 0,
    active_todos INT NOT NULL DEFAULT 0,
    completed_todos INT NOT NULL DEFAULT 0,
    open_todos INT NOT NULL DEFAULT 0,
    deleted_todos INT NOT NULL DEFAULT 0,
    created_events INT NOT NULL DEFAULT 0,
    updated_events INT NOT NULL DEFAULT 0,
    completed_events INT NOT NULL DEFAULT 0,
    deleted_events INT NOT NULL DEFAULT 0,
    completion_rate DECIMAL(10,4) NOT NULL DEFAULT 0,
    last_event_at DATETIME2 NULL,
    updated_at DATETIME2 NOT NULL
);

CREATE TABLE tenant_todo_daily_metrics (
    tenant_id UNIQUEIDENTIFIER NOT NULL,
    metric_date DATE NOT NULL,
    created_count INT NOT NULL DEFAULT 0,
    updated_count INT NOT NULL DEFAULT 0,
    completed_count INT NOT NULL DEFAULT 0,
    deleted_count INT NOT NULL DEFAULT 0,
    updated_at DATETIME2 NOT NULL,
    CONSTRAINT pk_tenant_todo_daily_metrics PRIMARY KEY (tenant_id, metric_date)
);

CREATE INDEX idx_tenant_todo_analytics_projection_tenant
ON tenant_todo_analytics_projection(tenant_id);

CREATE INDEX idx_tenant_todo_analytics_projection_tenant_state
ON tenant_todo_analytics_projection(tenant_id, is_deleted, is_completed);

CREATE INDEX idx_tenant_todo_daily_metrics_tenant_date
ON tenant_todo_daily_metrics(tenant_id, metric_date);

# --- !Downs

DROP INDEX idx_tenant_todo_daily_metrics_tenant_date ON tenant_todo_daily_metrics;
DROP INDEX idx_tenant_todo_analytics_projection_tenant_state ON tenant_todo_analytics_projection;
DROP INDEX idx_tenant_todo_analytics_projection_tenant ON tenant_todo_analytics_projection;
DROP TABLE IF EXISTS tenant_todo_daily_metrics;
DROP TABLE IF EXISTS tenant_todo_analytics_summary;
DROP TABLE IF EXISTS tenant_todo_analytics_projection;

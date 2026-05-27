# --- !Ups

CREATE TABLE consumer_processed_events (
    consumer_name NVARCHAR(100) NOT NULL,
    event_id UNIQUEIDENTIFIER NOT NULL,
    tenant_id UNIQUEIDENTIFIER NOT NULL,
    processed_at DATETIME2 NOT NULL,
    CONSTRAINT pk_consumer_processed_events PRIMARY KEY (consumer_name, event_id)
);

CREATE INDEX idx_consumer_processed_events_tenant
ON consumer_processed_events(tenant_id);

# --- !Downs

DROP INDEX idx_consumer_processed_events_tenant ON consumer_processed_events;
DROP TABLE IF EXISTS consumer_processed_events;

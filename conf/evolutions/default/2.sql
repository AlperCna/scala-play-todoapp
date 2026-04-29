# --- !Ups

CREATE TABLE tenants (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    domain NVARCHAR(255) NOT NULL UNIQUE,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NULL
);

INSERT INTO tenants (id, name, domain, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default Tenant', 'default.local', GETDATE());

ALTER TABLE users ADD tenant_id UNIQUEIDENTIFIER NULL;
ALTER TABLE todos ADD tenant_id UNIQUEIDENTIFIER NULL;
ALTER TABLE audit_logs ADD tenant_id UNIQUEIDENTIFIER NULL;

UPDATE users SET tenant_id = '00000000-0000-0000-0000-000000000001';
UPDATE todos SET tenant_id = '00000000-0000-0000-0000-000000000001';
UPDATE audit_logs SET tenant_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE users ALTER COLUMN tenant_id UNIQUEIDENTIFIER NOT NULL;
ALTER TABLE todos ALTER COLUMN tenant_id UNIQUEIDENTIFIER NOT NULL;

ALTER TABLE users ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
ALTER TABLE todos ADD CONSTRAINT fk_todos_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_logs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_todos_tenant_id ON todos(tenant_id);
CREATE INDEX idx_audit_logs_tenant_id ON audit_logs(tenant_id);

# --- !Downs

ALTER TABLE audit_logs DROP CONSTRAINT fk_audit_logs_tenant;
ALTER TABLE todos DROP CONSTRAINT fk_todos_tenant;
ALTER TABLE users DROP CONSTRAINT fk_users_tenant;

DROP INDEX idx_audit_logs_tenant_id ON audit_logs;
DROP INDEX idx_todos_tenant_id ON todos;
DROP INDEX idx_users_tenant_id ON users;

ALTER TABLE audit_logs DROP COLUMN tenant_id;
ALTER TABLE todos DROP COLUMN tenant_id;
ALTER TABLE users DROP COLUMN tenant_id;

DROP TABLE IF EXISTS tenants;

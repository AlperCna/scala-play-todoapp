# --- !Ups

CREATE TABLE users (
                       id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
                       username NVARCHAR(100) NOT NULL,
                       email NVARCHAR(255) NOT NULL UNIQUE,
                       password_hash NVARCHAR(255) NOT NULL,
                       role NVARCHAR(20) NOT NULL,
                       created_at DATETIME2 NOT NULL,
                       updated_at DATETIME2 NULL,
                       is_active BIT NOT NULL DEFAULT 1
);

CREATE TABLE todos (
                       id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
                       user_id UNIQUEIDENTIFIER NOT NULL,
                       title NVARCHAR(200) NOT NULL,
                       description NVARCHAR(MAX) NULL,
                       is_completed BIT NOT NULL DEFAULT 0,
                       created_at DATETIME2 NOT NULL,
                       updated_at DATETIME2 NULL,
                       deleted_at DATETIME2 NULL,
                       is_deleted BIT NOT NULL DEFAULT 0,
                       CONSTRAINT fk_todos_user
                           FOREIGN KEY (user_id)
                               REFERENCES users(id)
                               ON DELETE CASCADE
);


CREATE TABLE audit_logs (
                            id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
                            user_id UNIQUEIDENTIFIER NULL,
                            action NVARCHAR(100) NOT NULL,
                            ip_address NVARCHAR(100) NULL,
                            user_agent NVARCHAR(500) NULL,
                            created_at DATETIME2 NOT NULL,
                            CONSTRAINT fk_audit_logs_user
                                FOREIGN KEY (user_id)
                                    REFERENCES users(id)
                                    ON DELETE SET NULL
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_todos_user_id ON todos(user_id);
CREATE INDEX idx_todos_user_deleted ON todos(user_id, is_deleted);
CREATE INDEX idx_todos_user_completed ON todos(user_id, is_completed);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

# --- !Downs

DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS todos;
DROP TABLE IF EXISTS users;
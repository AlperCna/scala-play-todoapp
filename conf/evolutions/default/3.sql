# --- !Ups

ALTER TABLE todos ADD due_date DATETIME2 NULL;

# --- !Downs

ALTER TABLE todos DROP COLUMN due_date;

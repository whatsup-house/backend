ALTER TABLE users
    ADD COLUMN IF NOT EXISTS "delete" CHAR(1) NOT NULL DEFAULT 'N';

ALTER TABLE users
    ADD CONSTRAINT chk_users_delete_yn CHECK ("delete" IN ('Y', 'N'));

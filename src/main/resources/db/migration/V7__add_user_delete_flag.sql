ALTER TABLE users
    ADD COLUMN IF NOT EXISTS "delete" CHAR(1) NOT NULL DEFAULT 'N';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_users_delete_yn'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_delete_yn CHECK ("delete" IN ('Y', 'N'));
    END IF;
END $$;

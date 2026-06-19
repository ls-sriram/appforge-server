-- Rename legacy share_slots table to entity_share_references for clearer ownership semantics.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'share_slots'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'entity_share_references'
    ) THEN
        ALTER TABLE share_slots RENAME TO entity_share_references;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_class
        WHERE relname = 'idx_share_slots_token'
    ) THEN
        ALTER INDEX idx_share_slots_token RENAME TO idx_entity_share_references_token;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'entity_share_references' AND policyname = 'share_slots_user_isolation'
    ) THEN
        ALTER POLICY share_slots_user_isolation ON entity_share_references RENAME TO entity_share_references_user_isolation;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'entity_share_references' AND policyname = 'share_slots_user_insert'
    ) THEN
        ALTER POLICY share_slots_user_insert ON entity_share_references RENAME TO entity_share_references_user_insert;
    END IF;
END $$;


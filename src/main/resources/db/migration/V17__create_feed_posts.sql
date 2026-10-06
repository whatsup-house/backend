-- V17: 피드 게시물 (KAN-380)
-- 인스타그램 게시물·릴스를 옮겨 담는다. media 는 순서 있는 배열 [{type: IMAGE|VIDEO, url, posterUrl?, width?, height?}].
CREATE TABLE IF NOT EXISTS feed_posts (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    media         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    caption       TEXT,
    instagram_url VARCHAR(500),
    posted_at     TIMESTAMP    NOT NULL,
    gathering_id  UUID         REFERENCES gatherings(id),
    is_visible    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    deleted_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_feed_posts_visible_posted_at ON feed_posts (is_visible, posted_at DESC);

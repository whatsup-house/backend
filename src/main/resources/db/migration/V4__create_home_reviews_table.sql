-- 홈 화면 후기 미리보기 테이블
CREATE TABLE home_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    author_name VARCHAR(50) NOT NULL,
    avatar_url TEXT DEFAULT NULL,
    gathering_title VARCHAR(100) NOT NULL,
    rating INTEGER NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
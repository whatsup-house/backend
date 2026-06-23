CREATE TABLE IF NOT EXISTS users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    name            VARCHAR(50)  NOT NULL,
    gender          VARCHAR(10)  NOT NULL,
    age             INTEGER      NOT NULL,
    birth_date      DATE,
    nickname        VARCHAR(50)  NOT NULL UNIQUE,
    phone           VARCHAR(11),
    instagram_id    VARCHAR(100),
    mbti            VARCHAR(4),
    job             VARCHAR(30),
    intro           TEXT,
    is_admin        BOOLEAN      NOT NULL DEFAULT FALSE,
    mileage_balance INTEGER      NOT NULL DEFAULT 0,
    account_status  VARCHAR(20),
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    deleted_at      TIMESTAMP
);

-- ================================================
-- 참가자 (회원/비회원 공통) — 우연한 식탁 심사·이용권의 소유 주체
-- ================================================
CREATE TABLE IF NOT EXISTS participants (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID         UNIQUE REFERENCES users(id),
    participant_type         VARCHAR(20)  NOT NULL,
    name                     VARCHAR(50)  NOT NULL,
    email                    VARCHAR(255) NOT NULL,
    phone                    VARCHAR(11)  NOT NULL,
    email_verified_at        TIMESTAMP,
    account_status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    random_table_eligibility VARCHAR(20)  NOT NULL DEFAULT 'UNREVIEWED',
    created_at               TIMESTAMP    NOT NULL,
    updated_at               TIMESTAMP    NOT NULL,
    deleted_at               TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_participants_user_id ON participants(user_id);

CREATE TABLE IF NOT EXISTS locations (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(100) NOT NULL,
    address       VARCHAR(255) NOT NULL,
    naver_map_url VARCHAR(500),
    kakao_map_url VARCHAR(500),
    status        VARCHAR(20)  NOT NULL,
    max_capacity  INTEGER      NOT NULL,
    memo          TEXT,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    deleted_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS gatherings (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    how_to_run    JSONB,
    location_id   UUID         REFERENCES locations(id),
    event_date    DATE         NOT NULL,
    start_time    TIME,
    end_time      TIME,
    price         INTEGER,
    max_attendees INTEGER      NOT NULL,
    gathering_type VARCHAR(20),
    status        VARCHAR(20)  NOT NULL,
    thumbnail_url VARCHAR(500),
    is_curated    BOOLEAN      NOT NULL DEFAULT FALSE,
    curated_rank  INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    deleted_at    TIMESTAMP
);

CREATE TABLE IF NOT EXISTS applications (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_number VARCHAR(20) NOT NULL UNIQUE,
    gathering_id   UUID        NOT NULL REFERENCES gatherings(id),
    participant_id UUID        REFERENCES participants(id),
    name           VARCHAR(50) NOT NULL,
    phone          VARCHAR(11) NOT NULL,
    email          VARCHAR(255),
    form_snapshot  JSONB,
    gender         VARCHAR(10),
    age            INTEGER,
    instagram_id   VARCHAR(100),
    job            VARCHAR(50),
    mbti           VARCHAR(4),
    intro          TEXT,
    referrer_name  VARCHAR(50),
    status         VARCHAR(20) NOT NULL,
    payment_confirmed_at TIMESTAMP,
    reviewed_at    TIMESTAMP,
    rejection_reason VARCHAR(500),
    created_at     TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP   NOT NULL,
    deleted_at     TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mileage_history (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    mileage_user_id UUID         NOT NULL REFERENCES users(id),
    mileage_type    VARCHAR(20)  NOT NULL,
    amount          INTEGER      NOT NULL,
    balance_after   INTEGER      NOT NULL,
    related_id      UUID,
    adjust_reason   VARCHAR(255),
    earned_date     TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS reviews (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES users(id),
    application_id     UUID        NOT NULL REFERENCES applications(id),
    gathering_id       UUID        NOT NULL REFERENCES gatherings(id),
    review_type        VARCHAR(10) NOT NULL,
    review_content     TEXT        NOT NULL,
    like_count         INTEGER     NOT NULL DEFAULT 0,
    notified_like_milestone INTEGER NOT NULL DEFAULT 0,
    is_home_featured   BOOLEAN     NOT NULL DEFAULT FALSE,
    home_display_order INTEGER     NOT NULL DEFAULT 0,
    created_at         TIMESTAMP   NOT NULL,
    updated_at         TIMESTAMP   NOT NULL,
    deleted_at         TIMESTAMP,
    CONSTRAINT uk_reviews_application_id UNIQUE (application_id)
);

CREATE INDEX IF NOT EXISTS idx_reviews_gathering_id ON reviews(gathering_id);
CREATE INDEX IF NOT EXISTS idx_reviews_user_id ON reviews(user_id);
CREATE INDEX IF NOT EXISTS idx_reviews_home_featured ON reviews(is_home_featured, home_display_order);

CREATE TABLE IF NOT EXISTS review_images (
    id            UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id     UUID      NOT NULL REFERENCES reviews(id),
    image_url     TEXT      NOT NULL,
    display_order INTEGER   NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    deleted_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_review_images_review_id ON review_images(review_id);

CREATE TABLE IF NOT EXISTS review_likes (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id  UUID      NOT NULL REFERENCES reviews(id),
    user_id    UUID      NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_review_likes_review_user UNIQUE (review_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_review_likes_review_id ON review_likes(review_id);
CREATE INDEX IF NOT EXISTS idx_review_likes_user_id ON review_likes(user_id);

CREATE TABLE IF NOT EXISTS carousel_slides (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type         VARCHAR(20)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    content      VARCHAR(500),
    image_url    VARCHAR(500) NOT NULL,
    gathering_id UUID         REFERENCES gatherings(id),
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    is_active    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    deleted_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS home_reviews (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    content         TEXT        NOT NULL,
    author_name     VARCHAR(50) NOT NULL,
    avatar_url      TEXT,
    gathering_title VARCHAR(100) NOT NULL,
    rating          INTEGER     NOT NULL,
    display_order   INTEGER     NOT NULL DEFAULT 0,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- ================================================
-- EAV 동적 신청폼 (우연한 식탁)
-- ================================================
CREATE TABLE IF NOT EXISTS forms (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    gathering_id   UUID        REFERENCES gatherings(id),
    is_template    BOOLEAN     NOT NULL DEFAULT FALSE,
    gathering_type VARCHAR(20),
    guide_text     TEXT,
    created_at     TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP   NOT NULL,
    deleted_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_forms_gathering_id ON forms(gathering_id);

CREATE TABLE IF NOT EXISTS form_questions (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    form_id            UUID         NOT NULL REFERENCES forms(id),
    question_key       VARCHAR(100) NOT NULL,
    type               VARCHAR(30)  NOT NULL,
    label              TEXT         NOT NULL,
    placeholder        TEXT,
    required           BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order      INTEGER      NOT NULL DEFAULT 0,
    options            JSONB,
    validation         JSONB,
    is_matching_field  BOOLEAN      NOT NULL DEFAULT FALSE,
    is_system_reserved BOOLEAN      NOT NULL DEFAULT FALSE,
    matching_strategy  VARCHAR(100),
    matching_weight    NUMERIC(3,2),
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    deleted_at         TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_form_questions_form_id ON form_questions(form_id);

CREATE TABLE IF NOT EXISTS application_answers (
    id             UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID      NOT NULL REFERENCES applications(id),
    question_id    UUID      NOT NULL REFERENCES form_questions(id),
    value          JSONB     NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL,
    deleted_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_application_answers_application_id ON application_answers(application_id);
CREATE INDEX IF NOT EXISTS idx_application_answers_question_id ON application_answers(question_id);

-- ================================================
-- 자동매칭 (rule-v1)
-- ================================================
CREATE TABLE IF NOT EXISTS matching_groups (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    gathering_id       UUID         NOT NULL REFERENCES gatherings(id),
    event_date         DATE         NOT NULL,
    region             TEXT,
    group_size         INTEGER      NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    restaurant_name    TEXT,
    restaurant_address TEXT,
    matched_at         TIMESTAMP,
    algorithm_version  VARCHAR(20)  NOT NULL DEFAULT 'rule-v1',
    group_score        NUMERIC(5,4),
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    deleted_at         TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_matching_groups_gathering_id ON matching_groups(gathering_id);

CREATE TABLE IF NOT EXISTS matching_members (
    id               UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id   UUID      NOT NULL UNIQUE REFERENCES applications(id),
    group_id         UUID      NOT NULL REFERENCES matching_groups(id),
    seat_order       INTEGER,
    is_manual_assign BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_matching_members_group_id ON matching_members(group_id);

-- ================================================
-- 알림 메일 템플릿 (관리자 운영 수정용 오버라이드)
-- 행이 없으면 코드의 MailTemplateType 기본값을 사용하므로 시드는 필요 없다.
-- ================================================
CREATE TABLE IF NOT EXISTS mail_templates (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key VARCHAR(50)  NOT NULL UNIQUE,
    description  VARCHAR(100),
    subject      VARCHAR(255) NOT NULL,
    body         TEXT         NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);

-- ================================================
-- 인앱 알림
-- ================================================
CREATE TABLE IF NOT EXISTS notifications (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id),
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    content    TEXT,
    link       VARCHAR(40),
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications(user_id, is_read);

-- ================================================
-- 우연한 식탁 이용권
-- ================================================
CREATE TABLE IF NOT EXISTS ticket_passes (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id       UUID        NOT NULL REFERENCES participants(id),
    application_id        UUID        REFERENCES applications(id),
    product              VARCHAR(40) NOT NULL,
    total_count          INTEGER     NOT NULL,
    remaining_count      INTEGER     NOT NULL DEFAULT 0,
    purchase_amount      INTEGER     NOT NULL,
    status               VARCHAR(20) NOT NULL,
    payment_deadline     TIMESTAMP,
    payment_confirmed_at TIMESTAMP,
    activated_at         TIMESTAMP,
    created_at           TIMESTAMP   NOT NULL,
    updated_at           TIMESTAMP   NOT NULL,
    deleted_at           TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ticket_passes_participant_id ON ticket_passes(participant_id);
CREATE INDEX IF NOT EXISTS idx_ticket_passes_application_id ON ticket_passes(application_id);
CREATE INDEX IF NOT EXISTS idx_ticket_passes_status ON ticket_passes(status);

-- ================================================
-- 우연한 식탁 이용권 거래내역 (잔여 횟수 변경 이력)
-- ================================================
CREATE TABLE IF NOT EXISTS ticket_transactions (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_pass_id   UUID        NOT NULL REFERENCES ticket_passes(id),
    application_id   UUID        REFERENCES applications(id),
    transaction_type VARCHAR(20) NOT NULL,
    quantity         INTEGER     NOT NULL,
    balance_after    INTEGER     NOT NULL,
    reason           VARCHAR(255),
    created_at       TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ticket_transactions_ticket_pass_id ON ticket_transactions(ticket_pass_id);
CREATE INDEX IF NOT EXISTS idx_ticket_transactions_application_id ON ticket_transactions(application_id);

-- ================================================
-- 다국어 콘텐츠 번역
-- ================================================
CREATE TABLE IF NOT EXISTS content_translations (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type  VARCHAR(40)  NOT NULL,
    entity_id    UUID         NOT NULL,
    field        VARCHAR(40)  NOT NULL,
    locale       VARCHAR(5)   NOT NULL,
    value        TEXT,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DONE',
    source_hash  VARCHAR(64),
    is_override  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    deleted_at   TIMESTAMP,
    CONSTRAINT uq_content_translation UNIQUE (entity_type, entity_id, field, locale)
);

CREATE INDEX IF NOT EXISTS idx_content_translation_lookup
    ON content_translations (entity_type, entity_id, locale);

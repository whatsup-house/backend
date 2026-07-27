CREATE TABLE IF NOT EXISTS users (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    gender      VARCHAR(10)  NOT NULL,
    age         INTEGER      NOT NULL,
    nickname    VARCHAR(50)  NOT NULL UNIQUE,
    phone       VARCHAR(11),
    instagram_id VARCHAR(100),
    mbti        VARCHAR(4),
    job         VARCHAR(30),
    intro       TEXT,
    is_admin    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    deleted_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS locations (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL,
    address      VARCHAR(255) NOT NULL,
    map_url      VARCHAR(500),
    status       VARCHAR(20)  NOT NULL,
    max_capacity INTEGER      NOT NULL,
    memo         TEXT,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    deleted_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS gatherings (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    location_id  UUID         REFERENCES locations(id),
    event_date   DATE         NOT NULL,
    start_time   TIME,
    end_time     TIME,
    price        INTEGER,
    max_attendees INTEGER     NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    thumbnail_url VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    deleted_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS applications (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_number VARCHAR(20) NOT NULL UNIQUE,
    gathering_id   UUID        NOT NULL REFERENCES gatherings(id),
    user_id        UUID        REFERENCES users(id),
    name           VARCHAR(50) NOT NULL,
    phone          VARCHAR(11) NOT NULL,
    gender         VARCHAR(10),
    age            INTEGER,
    instagram_id   VARCHAR(100),
    job            VARCHAR(50),
    mbti           VARCHAR(4),
    intro          TEXT,
    referrer_name  VARCHAR(50),
    status         VARCHAR(20) NOT NULL,
    created_at     TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP   NOT NULL,
    deleted_at     TIMESTAMP
);

-- T24 (Architecture 7.3): first version of the persistent command log.
-- Replay is driven only by command_sequence (never insert order or a timestamp).

CREATE TABLE matches (
    id               UUID PRIMARY KEY,
    seed             BIGINT NOT NULL,
    ruleset_version  VARCHAR(100) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ
);

CREATE TABLE match_players (
    match_id  UUID NOT NULL REFERENCES matches (id),
    seat      INT NOT NULL,
    nickname  VARCHAR(100),
    city      VARCHAR(50) NOT NULL,
    is_bot    BOOLEAN NOT NULL,
    PRIMARY KEY (match_id, seat)
);

CREATE TABLE match_commands (
    match_id                 UUID NOT NULL REFERENCES matches (id),
    command_sequence         BIGINT NOT NULL,
    command_id               VARCHAR(100),
    origin                   VARCHAR(10) NOT NULL,
    actor_seat               INT,
    command_type             VARCHAR(60) NOT NULL,
    command_schema_version   INT NOT NULL,
    payload                  JSONB NOT NULL,
    outcome                  VARCHAR(10) NOT NULL,
    rejection_code           VARCHAR(60),
    resulting_state_version  BIGINT NOT NULL,
    PRIMARY KEY (match_id, command_sequence)
);

CREATE TABLE match_results (
    match_id      UUID PRIMARY KEY REFERENCES matches (id),
    scores        JSONB NOT NULL,
    winner_seats  JSONB NOT NULL
);

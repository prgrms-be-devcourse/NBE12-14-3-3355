CREATE TABLE related_game_precompute_runs (
    id VARCHAR(36) NOT NULL,
    version BIGINT NULL,
    requested_by BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    requested_game_ids TEXT NULL,
    source_run_id VARCHAR(36) NULL,
    owner_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    upper_game_id BIGINT NULL,
    estimated_total BIGINT NULL,
    processed BIGINT NOT NULL,
    succeeded BIGINT NOT NULL,
    failed BIGINT NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    progress_updated_at DATETIME(6) NULL,
    heartbeat_at DATETIME(6) NOT NULL,
    error VARCHAR(500) NULL,
    PRIMARY KEY (id),
    INDEX idx_precompute_run_requested (requested_at, id),
    INDEX idx_precompute_run_status (status, heartbeat_at)
);

CREATE TABLE related_game_precompute_run_failures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(36) NOT NULL,
    game_id BIGINT NOT NULL,
    error VARCHAR(500) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_precompute_failure_run_game UNIQUE (run_id, game_id),
    CONSTRAINT fk_precompute_failure_run FOREIGN KEY (run_id)
        REFERENCES related_game_precompute_runs (id)
);

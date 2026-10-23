-- 원천은 게임 삭제 후에도 장르·변경 정보를 보존하므로 games 외래키를 두지 않는다.
CREATE TABLE related_game_input_changes (
    game_id BIGINT NOT NULL,
    change_version BIGINT NOT NULL,
    expanded_version BIGINT NOT NULL,
    affected_genre_ids JSON NOT NULL,
    reason_mask BIGINT NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (game_id),
    CONSTRAINT ck_related_input_change_values CHECK (
        game_id > 0 AND change_version >= 0 AND expanded_version >= 0
        AND expanded_version <= change_version AND reason_mask >= 0
    )
);

-- 기준 게임이 삭제되면 계산할 대상도 사라지므로 대기 상태는 함께 정리한다.
CREATE TABLE related_game_refresh_targets (
    game_id BIGINT NOT NULL,
    required_version BIGINT NOT NULL,
    applied_version BIGINT NOT NULL,
    reason_mask BIGINT NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (game_id),
    CONSTRAINT fk_related_refresh_target_game FOREIGN KEY (game_id)
        REFERENCES games (id) ON DELETE CASCADE,
    CONSTRAINT ck_related_refresh_target_values CHECK (
        game_id > 0 AND required_version > 0 AND applied_version >= 0
        AND applied_version <= required_version AND reason_mask >= 0
    )
);

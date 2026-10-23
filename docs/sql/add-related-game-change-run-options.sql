-- 기존 관리자 실행 이력 테이블이 있는 DB에 적용한다.
-- 새 설치에서 add-related-game-precompute-runs.sql을 사용했다면 이 ALTER는 실행하지 않는다.
ALTER TABLE related_game_precompute_runs
    ADD COLUMN initialize_baseline BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN baseline_registered_at DATETIME(6) NULL;

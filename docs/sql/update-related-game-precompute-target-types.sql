-- Hibernate ddl-auto=update는 예전 enum CHECK를 갱신하지 않을 수 있다.
-- target_type의 enum CHECK만 찾아 현재 실행 종류로 교체한다. 상태 CHECK와 실행 이력은 유지한다.
-- 컬럼 추가와 독립적이며 이미 initialize_baseline 컬럼이 있는 DB에서도 재실행할 수 있다.
SET @related_precompute_target_drops = (
    SELECT GROUP_CONCAT(
        CONCAT('DROP CHECK `', REPLACE(tc.CONSTRAINT_NAME, '`', '``'), '`')
        ORDER BY tc.CONSTRAINT_NAME SEPARATOR ', '
    )
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
        ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
        AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA = DATABASE()
        AND tc.TABLE_NAME = 'related_game_precompute_runs'
        AND tc.CONSTRAINT_TYPE = 'CHECK'
        AND LOWER(cc.CHECK_CLAUSE) LIKE '%`target_type` in (%'
);

SET @related_precompute_target_ddl = CONCAT(
    'ALTER TABLE related_game_precompute_runs ',
    IF(@related_precompute_target_drops IS NULL, '', CONCAT(@related_precompute_target_drops, ', ')),
    'ADD CONSTRAINT ck_precompute_run_target_type ',
    'CHECK (target_type IN (''ALL'', ''IDS'', ''FAILED'', ''UNREADY'', ''CHANGED''))'
);
PREPARE related_precompute_target_stmt FROM @related_precompute_target_ddl;
EXECUTE related_precompute_target_stmt;
DEALLOCATE PREPARE related_precompute_target_stmt;
SET @related_precompute_target_drops = NULL;
SET @related_precompute_target_ddl = NULL;

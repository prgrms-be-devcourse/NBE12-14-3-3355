-- MySQL 8.4 / manual migration. Confirm the database and SHOW INDEX FROM games first.
-- Local gamelog_retry already has this index; do not create it twice.
-- Matches the existing default search ORDER BY exactly, including NULL -> -1.0.
-- Trailing columns cover candidate filtering/projection; full game rows are read only after LIMIT.
CREATE INDEX idx_games_search_score ON games
    ((COALESCE(igdb_rating, -1.0)) DESC, id ASC, release_date, title, igdb_rating)
    INVISIBLE;

-- Before activation, compare plans/results in a dedicated session:
-- SET SESSION optimizer_switch='use_invisible_indexes=on';
-- Exact COUNT still scans the matching release-date range.
ALTER TABLE games ALTER INDEX idx_games_search_score VISIBLE;

-- Roll back plan selection without deleting the index:
-- ALTER TABLE games ALTER INDEX idx_games_search_score INVISIBLE;
-- Invisible indexes still consume storage and incur write maintenance.

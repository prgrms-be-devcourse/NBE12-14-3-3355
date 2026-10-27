-- Read-only optimizer estimates; this does not execute EXPLAIN ANALYZE.
-- Local IDE configuration: readyOnly=true, 2026-10-26 Asia/Seoul, page=0, size=20.
-- Representative default-list query and its COUNT, matching GameRepository.
EXPLAIN
SELECT g.* FROM (
    SELECT id, igdb_rating FROM games
    WHERE release_date >= '1970-01-01'
      AND release_date < '2026-10-27'
      AND EXISTS (
          SELECT 1 FROM related_game_calculations calculation
          WHERE calculation.game_id = games.id AND calculation.last_success_at IS NOT NULL
      )
      AND LOWER(title) LIKE LOWER('%Baldur%') ESCAPE '!'
    ORDER BY COALESCE(igdb_rating, -1.0) DESC, id ASC
    LIMIT 20 OFFSET 0
) matched JOIN games g ON g.id = matched.id
ORDER BY COALESCE(g.igdb_rating, -1.0) DESC, g.id ASC;

EXPLAIN
SELECT COUNT(*) FROM games
WHERE release_date >= '1970-01-01'
  AND release_date < '2026-10-27'
  AND EXISTS (
      SELECT 1 FROM related_game_calculations calculation
      WHERE calculation.game_id = games.id AND calculation.last_success_at IS NOT NULL
  )
  AND LOWER(title) LIKE LOWER('%Baldur%') ESCAPE '!';

-- Filter COUNT plans: actual filter IDs from the baseline manifest (RPG=4, PC=1).
EXPLAIN
SELECT /*+ JOIN_INDEX(g idx_games_catalog_lookup) */ COUNT(*) FROM games g
WHERE g.release_date >= '1970-01-01' AND g.release_date < '2026-10-27'
  AND EXISTS (SELECT 1 FROM related_game_calculations calculation
              WHERE calculation.game_id = g.id AND calculation.last_success_at IS NOT NULL)
  AND EXISTS (SELECT 1 FROM game_genres gg WHERE gg.game_id = g.id AND gg.genre_id IN (4));

EXPLAIN
SELECT /*+ JOIN_INDEX(g idx_games_catalog_lookup) */ COUNT(*) FROM games g
WHERE g.release_date >= '1970-01-01' AND g.release_date < '2026-10-27'
  AND EXISTS (SELECT 1 FROM related_game_calculations calculation
              WHERE calculation.game_id = g.id AND calculation.last_success_at IS NOT NULL)
  AND EXISTS (SELECT 1 FROM game_platforms gp WHERE gp.game_id = g.id AND gp.platform_id IN (1));

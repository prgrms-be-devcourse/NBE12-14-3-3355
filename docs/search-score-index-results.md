# Default search score index experiment

2026-10-27, local MySQL 8.4 `gamelog_retry`, 284,294 games. Existing application SQL is unchanged.
The candidate index orders by `COALESCE(igdb_rating, -1.0) DESC, id ASC`, followed by release date,
title and raw rating for covering reads. The previous release-date-first index remains available.

The index was created invisible, then compared with session `use_invisible_indexes=off/on`.
Each query had one warmup and three sequential EXPLAIN ANALYZE measurements. Values below are
medians in milliseconds; they include instrumentation, exclude HTTP/JVM overhead and do not
predict concurrent latency. Modes were run sequentially, not randomized. No concurrent k6 run was performed.

| Case | List before | List after | COUNT before | COUNT after |
|---|---:|---:|---:|---:|
| a, page 0 | 139 | 0.178 | 93.5 | 94.7 |
| Baldur, page 0 | 112 | 57.6 | 103 | 98.1 |
| zzzz_no_match_20261027, page 0 | 101 | 109 | 93 | 95.4 |
| a, page 100 | 147 | 1.08 | 92.2 | 93.2 |

All cases used size 20, release date >= 1970-01-01 and < 2026-10-28. Full selected rows,
their order and exact COUNT were equal between modes in all four cases. This is not exhaustive
coverage of filters, other sort options or autocomplete.

For `a` page 0, candidate index entries read fell from 284,294 to 26. The large candidate sort
disappeared; an outer sort of only the final 20 rows remains. `Baldur` still read 151,157 entries.
No-match search still scans broadly and was about 8ms slower in this sample. Exact COUNT still
reads 284,294 index entries and is now the dominant cost for broad first-page searches.

Raw plans and CSV: `k6/results/score-index-20261027-122838/` (git-ignored).
Reproduction: `k6/compare-score-index.ps1`, with the candidate index INVISIBLE and a stable DB.
The script compares result equality, warms each query and saves timing plans. Its cutoff is the
next day in the shell's local timezone (the measured machine used Asia/Seoul).

Local activation: `ALTER TABLE games ALTER INDEX idx_games_search_score VISIBLE`.
Other environments need the manual migration in `monitoring/search-score-index.sql`.
Rollback: set the index INVISIBLE. Existing indexes and exact pagination are retained.
The new index adds storage and write maintenance; these costs were not benchmarked.

Reference: https://dev.mysql.com/doc/refman/8.4/en/invisible-indexes.html

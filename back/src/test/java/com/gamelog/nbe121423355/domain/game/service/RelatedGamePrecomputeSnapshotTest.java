package com.gamelog.nbe121423355.domain.game.service;

import com.gamelog.nbe121423355.domain.game.repository.projection.RelatedGameCandidateProjection;
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode;
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelatedGamePrecomputeSnapshotTest {

    @Test
    void reusesOneGenreRankingAndRetainsTwentyAfterSelfExclusion() {
        // given: 같은 장르 조합을 순서와 중복이 다르게 표현한 게임과 평점을 준비한다.
        Map<Long, List<Long>> genres = new HashMap<>();
        Map<Long, BigDecimal> ratings = new HashMap<>();
        LongStream.rangeClosed(1, 25).forEach(id -> {
            genres.put(id, id % 2 == 0 ? List.of(2L, 1L, 1L) : List.of(1L, 2L));
            ratings.put(id, new BigDecimal("80.00"));
        });
        var snapshot = snapshot(genres, ratings, Set.of());

        // when: 같은 장르 조합의 서로 다른 기준 게임으로 후보를 계산한다.
        var first = ids(snapshot, 1L);
        var second = ids(snapshot, 2L);
        var last = ids(snapshot, 25L);

        // then: 자기 자신을 제외한 20개를 유지하며 장르 조합 계산을 한 번만 수행한다.
        assertThat(first).containsExactlyElementsOf(LongStream.rangeClosed(2, 21).boxed().toList());
        assertThat(second).containsExactlyElementsOf(LongStream.rangeClosed(1, 21)
                .filter(id -> id != 2).boxed().toList());
        assertThat(last).containsExactlyElementsOf(LongStream.rangeClosed(1, 20).boxed().toList());
        assertThat(snapshot.calculatedGroupCount()).isEqualTo(1);
    }

    @Test
    void separatesPartiallyMatchingGenresAndPreservesFractionalScore() {
        // given: 일부 장르만 공유하는 기준 게임과 소수 평점의 후보를 준비한다.
        var snapshot = snapshot(Map.of(
                1L, List.of(1L, 2L), 2L, List.of(1L),
                3L, List.of(1L, 2L), 4L, List.of(3L)),
                Map.of(3L, new BigDecimal("80.25"), 4L, new BigDecimal("99.00")), Set.of());

        // when: 서로 다른 장르 조합의 기본 추천을 계산한다.
        var first = snapshot.baselineFor(1L);
        var second = snapshot.baselineFor(2L);

        // then: 공유 장르 수와 소수 점수를 보존하고 조합별로 계산한다.
        assertThat(first.getFirst().relatedScore())
                .isEqualByComparingTo("70.125");
        assertThat(second.getFirst().relatedScore())
                .isEqualByComparingTo("55.125");
        assertThat(snapshot.calculatedGroupCount()).isEqualTo(2);
        assertThat(ids(snapshot, 1L)).containsExactly(3L);
    }

    @Test
    void supportsMoreThanSixtyFourDistinctGenresWithoutBitCollisions() {
        // given: 비트마스크 한 단어를 넘는 70개 장르와 경계 양쪽의 후보를 준비한다.
        Map<Long, List<Long>> genres = new HashMap<>();
        LongStream.rangeClosed(1, 70).forEach(id -> genres.put(id, List.of(id)));
        genres.put(100L, List.of(1L, 65L, 70L));
        var snapshot = snapshot(genres, Map.of(1L, new BigDecimal("80"),
                2L, new BigDecimal("99"), 65L, new BigDecimal("80"), 70L, new BigDecimal("80")), Set.of());

        // when: 1번과 65번 및 70번 장르를 공유하는 후보를 계산한다.
        var result = ids(snapshot, 100L);

        // then: 서로 다른 비트 단어의 장르를 혼동하지 않는다.
        assertThat(result).containsExactly(1L, 65L, 70L);
    }

    @Test
    void distinguishesEmptyResultFromSourcesThatNeedExistingQuery() {
        // given: 사용자 행동이 있는 게임과 장르가 없는 게임을 준비한다.
        var snapshot = snapshot(Map.of(1L, List.of(1L), 2L, List.of()), Map.of(), Set.of(1L));

        // when: 행동 게임과 미포함 게임 및 장르 없는 게임의 기본 추천을 요청한다.
        var interaction = snapshot.baselineFor(1L);
        var missing = snapshot.baselineFor(999L);
        var empty = snapshot.baselineFor(2L);

        // then: SQL 계산이 필요한 null과 정상 빈 결과를 구분한다.
        assertThat(interaction).isNull();
        assertThat(missing).isNull();
        assertThat(empty).isNotNull().isEmpty();
    }

    @Test
    void inputMutationDoesNotChangeSnapshotOrCachedRanking() {
        // given: 외부에서 수정 가능한 장르 목록과 후보 평점 및 행동 집합으로 스냅샷을 만든다.
        var sourceGenres = new ArrayList<>(List.of(1L));
        Map<Long, List<Long>> genres = new HashMap<>(Map.of(1L, sourceGenres, 2L, List.of(1L)));
        Map<Long, BigDecimal> ratings = new HashMap<>(Map.of(2L, new BigDecimal("80.25")));
        Set<Long> interactions = new HashSet<>();
        var snapshot = snapshot(genres, ratings, interactions);

        // when: 첫 계산 전 원본 입력을 변경하고 캐시 생성 후 장르 목록을 다시 변경한다.
        sourceGenres.clear();
        genres.clear();
        ratings.clear();
        interactions.add(1L);
        var first = snapshot.baselineFor(1L);
        sourceGenres.add(999L);
        var repeated = snapshot.baselineFor(1L);

        // then: 최초 스냅샷의 점수와 후보 및 캐시 키를 유지한다.
        assertThat(first).extracting(RelatedGameCandidateProjection::gameId).containsExactly(2L);
        assertThat(first.getFirst().relatedScore()).isEqualByComparingTo("55.125");
        assertThat(repeated).containsExactlyElementsOf(first);
        assertThat(snapshot.sourceCount()).isEqualTo(2);
        assertThat(snapshot.candidateCount()).isEqualTo(1);
        assertThat(snapshot.interactionSourceCount()).isZero();
        assertThat(snapshot.calculatedGroupCount()).isEqualTo(1);
    }

    @Test
    void returnedBaselineCannotBeModified() {
        // given: 공유 장르를 가진 기준 게임과 후보의 스냅샷을 준비한다.
        var snapshot = snapshot(Map.of(1L, List.of(1L), 2L, List.of(1L)),
                Map.of(2L, new BigDecimal("80")), Set.of());

        // when: 기준 게임의 기본 추천 목록을 조회한다.
        var result = snapshot.baselineFor(1L);

        // then: 반환 목록을 수정할 수 없고 이후 조회에서도 같은 후보를 반환한다.
        assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(ids(snapshot, 1L)).containsExactly(2L);
    }

    private RelatedGamePrecomputeSnapshot snapshot(Map<Long, List<Long>> genres,
                                                  Map<Long, BigDecimal> ratings, Set<Long> interactions) {
        return new RelatedGamePrecomputeSnapshot(RelatedGamePrecomputeMode.INITIAL_LOAD, 20,
                genres, ratings, interactions);
    }

    private List<Long> ids(RelatedGamePrecomputeSnapshot snapshot, Long sourceId) {
        return snapshot.baselineFor(sourceId).stream()
                .map(RelatedGameCandidateProjection::gameId).toList();
    }
}

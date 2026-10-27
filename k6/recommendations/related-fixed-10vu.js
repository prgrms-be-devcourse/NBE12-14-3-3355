// 고정 10 VU: bash k6/recommendations/run.sh related-fixed
import http from 'k6/http';
import { check, sleep } from 'k6';
import { duration, pause, phase, runTags } from './fixed-vu-config.js';
export { handleSummary } from './fixed-vu-config.js';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
if (!/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(baseUrl)) {
    throw new Error('이 스크립트는 로컬 전용입니다. BASE_URL은 localhost를 사용하세요.');
}
export const options = {
    scenarios: {
        related: { executor: 'constant-vus', vus: 10, duration, exec: 'related', gracefulStop: '30s' },
    },
    setupTimeout: '1m', tags: runTags,
    systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
    thresholds: {
        'http_req_failed{scenario:related,phase:measurement}': ['rate<0.01'],
        'checks{scenario:related,phase:measurement}': ['rate>0.99'],
        'http_req_duration{scenario:related,phase:measurement}': ['p(99)<60000'],
        'http_reqs{scenario:related,phase:measurement}': ['count>0'],
    },
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function hasRecommendations(response) {
    try { return Array.isArray(response.json().data) && response.json().data.length > 0; }
    catch { return false; }
}

export function setup() {
    let gameId;
    if (__ENV.RELATED_GAME_ID) {
        gameId = Number(__ENV.RELATED_GAME_ID);
    } else {
        const response = http.get(`${baseUrl}/api/v1/games/page?page=0&size=1`, {
            tags: { name: 'fixture-setup', phase: 'setup' }, timeout: '30s',
        });
        if (response.status !== 200) throw new Error(`게임 선택 실패: HTTP ${response.status}`);
        try { gameId = response.json().data.content[0].id; }
        catch { throw new Error('DB에 게임이 없습니다. 먼저 데이터를 수집하세요.'); }
    }
    if (!Number.isSafeInteger(gameId) || gameId < 1) {
        throw new Error('RELATED_GAME_ID는 로컬 DB games.id의 양의 정수여야 합니다.');
    }
    const response = http.get(`${baseUrl}/api/v1/games/${gameId}/related`, {
        tags: { name: 'fixture-setup', phase: 'setup' }, timeout: '60s',
    });
    if (response.status !== 200 || !hasRecommendations(response)) {
        throw new Error(`연관 추천 사전 확인 실패: HTTP ${response.status}. 추천 결과가 있는 RELATED_GAME_ID를 지정하세요.`);
    }
    console.log(`게임 상세 연관 추천 준비 완료: RELATED_GAME_ID=${gameId}`);
    return { gameId };
}

export function related(data) {
    const requestPhase = phase();
    const metricScenario = requestPhase === 'warmup' ? 'warmup_related' : 'related';
    const response = http.get(`${baseUrl}/api/v1/games/${data.gameId}/related`, {
        tags: { name: '게임 상세 연관 추천', scenario: metricScenario, phase: requestPhase }, timeout: '60s',
    });
    check(response, {
        'HTTP 200': (r) => r.status === 200,
        '추천 결과 존재': hasRecommendations,
    }, { scenario: metricScenario, phase: requestPhase });
    sleep(pause);
}

// 고정 10 VU: bash k6/recommendations/run.sh personalized-fixed
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { duration, pause, phase, runTags } from './fixed-vu-config.js';
export { handleSummary } from './fixed-vu-config.js';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
if (!/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(baseUrl)) {
    throw new Error('이 스크립트는 준비된 테스트 계정을 사용하는 로컬 전용입니다. BASE_URL은 localhost를 사용하세요.');
}
// 로그인 정보는 Git에서 제외한 로컬 파일로만 관리한다.
let accounts;
try {
    accounts = JSON.parse(open(__ENV.ACCOUNTS_FILE || './.env.recommendation-accounts.json'));
} catch {
    throw new Error('k6/recommendations/.env.recommendation-accounts.json 파일을 준비하거나 ACCOUNTS_FILE을 지정하세요.');
}
for (const kind of ['onboarding', 'record']) {
    if (!Array.isArray(accounts[kind]) || accounts[kind].length !== 5) {
        throw new Error(`${kind} 계정은 정확히 5명이어야 합니다.`);
    }
    for (const user of accounts[kind]) {
        if (!user.email || !(user.password || accounts.password)
            || !Number.isSafeInteger(user.userId) || user.userId < 1) {
            throw new Error(`${kind} 계정의 email, password, userId를 확인하세요.`);
        }
        const expected = kind === 'record' ? 10 : 0;
        if (!Array.isArray(user.gameIds) || new Set(user.gameIds).size !== expected
            || user.gameIds.length !== expected) {
            throw new Error(`${kind} 계정의 gameIds는 서로 다른 게임 ${expected}개여야 합니다.`);
        }
    }
}
const allAccounts = [...accounts.onboarding, ...accounts.record];
if (new Set(allAccounts.map((user) => user.email)).size !== 10
    || new Set(allAccounts.map((user) => user.userId)).size !== 10) {
    throw new Error('온보딩/기록 계정은 중복 없이 총 10명이어야 합니다.');
}

// 기존 계정 10개에 각각 1 VU를 고정 배정한다. 워밍업부터 같은 VU를 유지한다.
const scenarios = {};
const thresholds = {};
for (const kind of ['onboarding', 'record']) {
    for (let slot = 0; slot < 5; slot++) {
        const name = `${kind}_${slot + 1}`;
        scenarios[name] = {
            executor: 'constant-vus', vus: 1, duration, exec: kind, gracefulStop: '30s',
        };
    }
    // 리퀘스트/check에 cohort를 scenario 태그로 명시해 기존 Grafana 쿼리를 유지한다.
    thresholds[`http_req_failed{scenario:${kind},phase:measurement}`] = ['rate<0.01'];
    thresholds[`checks{scenario:${kind},phase:measurement}`] = ['rate>0.99'];
    thresholds[`http_req_duration{scenario:${kind},phase:measurement}`] = ['p(99)<60000'];
    thresholds[`http_reqs{scenario:${kind},phase:measurement}`] = ['count>0'];
}
export const options = {
    scenarios, thresholds, setupTimeout: '3m', tags: runTags,
    systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function request(method, path, body, token, name, cohort, requestPhase = 'setup') {
    const response = http.request(method, `${baseUrl}${path}`,
        body === null ? null : JSON.stringify(body), {
            headers: {
                'Content-Type': 'application/json',
                ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
            tags: { phase: requestPhase, name: name || 'fixture-setup', ...(cohort ? { scenario: cohort } : {}) }, timeout: '60s',
        });
    return response;
}

function requireData(response, label) {
    let body;
    try { body = response.json(); } catch { /* 아래에서 민감한 응답을 출력하지 않고 실패 처리 */ }
    if (response.status < 200 || response.status >= 300 || body?.data === undefined) {
        throw new Error(`${label} 실패: HTTP ${response.status} (응답 본문/토큰은 출력하지 않습니다)`);
    }
    return body.data;
}

function requireRecommendations(path, token, label) {
    const data = requireData(request('GET', path, null, token), label);
    if (!Array.isArray(data) || data.length === 0) {
        throw new Error(`${label}: 추천 결과가 비었습니다. 다른 GAME_IDS를 지정하세요.`);
    }
}

export function setup() {
    const fixtures = { onboardingUsers: [], recordUsers: [] };
    for (const kind of ['onboarding', 'record']) {
        for (const user of accounts[kind]) {
            const login = requireData(request('POST', '/api/v1/users/login', {
                email: user.email, password: user.password || accounts.password,
            }), `${kind} 로그인`);
            if (!login.accessToken || login.user?.id !== user.userId) {
                throw new Error(`${kind} 로그인 결과가 준비된 계정 정보와 다릅니다.`);
            }
            const library = requireData(request('GET',
                '/api/v1/library/games?status=ALL&page=0&size=100', null, login.accessToken), '기록 확인');
            const expectedCount = kind === 'record' ? 10 : 0;
            const actualIds = new Set((library.userGames || []).map((game) => game.gameId));
            if (library.totalElements !== expectedCount || actualIds.size !== expectedCount
                || !user.gameIds.every((id) => actualIds.has(id))) {
                throw new Error(`${kind} userId=${user.userId}: 게임 기록이 ${expectedCount}개가 아니거나 게임 구성이 다릅니다.`);
            }
            const preferred = requireData(request('GET', '/api/v1/users/me/preferred-games',
                null, login.accessToken), '선호 게임 확인');
            if (!Array.isArray(preferred) || preferred.length === 0) {
                throw new Error(`${kind} userId=${user.userId}: 온보딩 선호 게임이 없습니다.`);
            }
            requireRecommendations('/api/v1/games/recommendations/personalized',
                login.accessToken, `${kind} userId=${user.userId} 사전 확인`);
            fixtures[`${kind}Users`].push({ userId: user.userId, token: login.accessToken });
        }
    }
    console.log('기존 계정 준비 완료: 온보딩 5명(기록 0개), 기록 5명(각 10개). 신규 가입/기록 추가 없음.');
    return fixtures;
}

function measure(path, token, name, cohort) {
    const requestPhase = phase();
    // 기존 패널의 scenario=onboarding/record에는 본측정만 표시한다.
    const metricScenario = requestPhase === 'warmup' ? `warmup_${cohort}` : cohort;
    const response = request('GET', path, null, token, name, metricScenario, requestPhase);
    check(response, {
        'HTTP 200': (r) => r.status === 200,
        '추천 결과 존재': (r) => {
            try { return Array.isArray(r.json().data) && r.json().data.length > 0; }
            catch { return false; }
        },
    }, { scenario: metricScenario, phase: requestPhase });
    sleep(pause);
}

// 시나리오 이름의 슬롯 번호로 고정 계정을 선택한다. 슬롯당 최대 1 VU.
function userForVu(data, kind) {
    const slot = Number(exec.scenario.name.split('_').pop()) - 1;
    if (!Number.isInteger(slot) || slot < 0 || slot >= 5) throw new Error('잘못된 계정 슬롯입니다.');
    const user = data[`${kind}Users`][slot];
    if (__ITER === 0) console.log(`${kind} slot=${slot + 1} userId=${user.userId}`);
    return user;
}

export function onboarding(data) {
    measure('/api/v1/games/recommendations/personalized', userForVu(data, 'onboarding').token, '사용자 맞춤 추천', 'onboarding');
}
export function record(data) {
    measure('/api/v1/games/recommendations/personalized', userForVu(data, 'record').token, '사용자 맞춤 추천', 'record');
}

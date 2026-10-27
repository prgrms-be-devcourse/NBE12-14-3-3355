import http from 'k6/http';
import { check, sleep } from 'k6';

const vus = Number(__ENV.SEARCH_VUS || 20);
const thinkTime = Number(__ENV.THINK_TIME || 0);
const page = Number(__ENV.PAGE || 0);
const size = Number(__ENV.SIZE || 20);
const kind = __ENV.SEARCH_KIND || 'list';
const keyword = __ENV.KEYWORD === undefined ? 'a' : __ENV.KEYWORD;
const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
if (!Number.isInteger(vus) || vus < 1 || !Number.isFinite(thinkTime) || thinkTime < 0
    || !Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100
    || !['list', 'suggest'].includes(kind)) {
    throw new Error('SEARCH_VUS >= 1, THINK_TIME >= 0, PAGE >= 0, SIZE = 1..100, SEARCH_KIND = list|suggest');
}

export const options = {
    scenarios: {
        search: {
            executor: 'constant-vus',
            vus,
            duration: __ENV.SEARCH_DURATION || '2m',
            gracefulStop: '15s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
        checks: ['rate==1'],
    },
};

const tags = { name: 'game-search', kind, keyword, page: String(page), size: String(size) };
const path = kind === 'suggest'
    ? `/api/v1/games/suggestions?keyword=${encodeURIComponent(keyword)}`
    : `/api/v1/games/page?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`;

export default function () {
    const response = http.get(`${baseUrl}${path}`, { tags, timeout: '10s', redirects: 0 });
    check(response, {
        '검색 응답이 200': (r) => r.status === 200,
        '검색 결과 구조가 정상': (r) => {
            try {
                const data = r.json().data;
                return kind === 'suggest'
                    ? Array.isArray(data) && data.length <= 6
                    : Array.isArray(data.content) && typeof data.totalElements === 'number';
            } catch {
                return false;
            }
        },
    }, tags);
    if (thinkTime > 0) sleep(thinkTime);
}

import http from 'k6/http';
import { check } from 'k6';
import { Gauge } from 'k6/metrics';

const rate = Number(__ENV.RATE || 1);
const duration = Number(__ENV.DURATION_SECONDS || 120);
if (!Number.isInteger(rate) || rate < 1 || !Number.isInteger(duration) || duration < 1) {
    throw new Error('RATE and DURATION_SECONDS must be positive integers');
}
const target = new Gauge('search_target_rps');
export const options = {
    scenarios: {
        search: {
            executor: 'constant-arrival-rate', rate, timeUnit: '1s',
            duration: `${duration}s`, preAllocatedVUs: 100, maxVUs: 100,
            gracefulStop: '15s',
        },
    },
    systemTags: ['name', 'scenario'],
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
        checks: ['rate==1'],
        dropped_iterations: ['count==0'],
    },
};

export default function () {
    target.add(rate);
    const response = http.get(`${__ENV.BASE_URL || 'http://localhost:8080'}${__ENV.CASE_PATH}`, {
        timeout: '10s', redirects: 0,
        tags: { name: __ENV.CASE_NAME || 'search' },
    });
    check(response, {
        'HTTP 200': r => r.status === 200,
        'valid search response': r => {
            try {
                const body = r.json();
                return body.resultCode === '200-1' && (__ENV.CASE_KIND === 'suggestions'
                    ? Array.isArray(body.data) && body.data.length <= 6
                    : Array.isArray(body.data.content) && Number.isInteger(body.data.totalElements));
            } catch (_) { return false; }
        },
    });
}

export function handleSummary(data) {
    return { [__ENV.SUMMARY_PATH]: JSON.stringify(data, null, 2) };
}

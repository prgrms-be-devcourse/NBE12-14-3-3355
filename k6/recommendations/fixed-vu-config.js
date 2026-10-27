// 고정 VU 테스트에서만 사용하는 실행 조건과 측정 구간 구분.
import exec from 'k6/execution';

function seconds(name, fallback, minimum) {
    const value = Number(__ENV[name] ?? fallback);
    if (!Number.isSafeInteger(value) || value < minimum) {
        throw new Error(`${name}는 ${minimum} 이상의 정수(초)여야 합니다.`);
    }
    return value;
}

export const warmupSeconds = seconds('WARMUP_SECONDS', 30, 0);
export const measurementSeconds = seconds('MEASUREMENT_SECONDS', 120, 1);
export const duration = `${warmupSeconds + measurementSeconds}s`;
export const pause = Number(__ENV.SLEEP_SECONDS ?? 1);
if (!Number.isFinite(pause) || pause < 0) throw new Error('SLEEP_SECONDS는 0 이상이어야 합니다.');
// 각 VU에서 같은 값을 사용하도록 실행 도우미에서 식별자를 지정한다.
export const runId = __ENV.RUN_ID;
if (!runId || !/^[A-Za-z0-9_-]+$/.test(runId)) {
    throw new Error('RUN_ID에 영문/숫자/밑줄/하이픈 식별자를 지정하거나 run.sh로 실행하세요.');
}

export const runTags = { run_id: runId, profile: 'fixed-10vu', sleep_seconds: String(pause) };

export function phase() {
    return Date.now() - exec.scenario.startTime < warmupSeconds * 1000 ? 'warmup' : 'measurement';
}

// setup 반환값에는 인증 토큰이 있으므로 지표만 명시적으로 저장한다.
// SUMMARY_FILE의 부모 디렉터리는 실행 전에 준비해야 한다.
export function handleSummary(data) {
    const safe = JSON.stringify({
        run_id: runId, profile: 'fixed-10vu', vus: 10,
        warmup_seconds: warmupSeconds, measurement_seconds: measurementSeconds,
        sleep_seconds: pause, metrics: data.metrics,
    }, null, 2);
    return {
        stdout: `${safe}\n`,
        ...(__ENV.SUMMARY_FILE ? { [__ENV.SUMMARY_FILE]: safe } : {}),
    };
}

"""Sequential, guarded k6 arrival-rate measurements (Python standard library only)."""
import argparse
import collections
import datetime as dt
import json
import math
import os
import re
from pathlib import Path
import shutil
import subprocess
import time
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def get_json(url, timeout=10):
    with urllib.request.urlopen(url, timeout=timeout) as response:
        return json.load(response)


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')


def percentile(values, p=0.95):
    values = sorted(values)
    if not values:
        return None
    index = (len(values) - 1) * p
    lo = int(index)
    return values[lo] + (values[math.ceil(index)] - values[lo]) * (index - lo)


class Window:
    def __init__(self):
        self.samples = collections.defaultdict(collections.deque)
        self.dropped = 0

    def add(self, point):
        if point.get('type') != 'Point':
            return
        name, data = point['metric'], point['data']
        if name == 'dropped_iterations':
            self.dropped += data['value']
        if name in ('http_req_duration', 'http_req_failed', 'checks'):
            # k6 emits nanoseconds; Python 3.10 accepts only microsecond precision.
            timestamp = re.sub(r'\.(\d+)', lambda m: '.' + m[1][:6].ljust(6, '0'), data['time']).replace('Z', '+00:00')
            stamp = dt.datetime.fromisoformat(timestamp).timestamp()
            self.samples[name].append((stamp, data['value']))

    def read(self, now):
        for queue in self.samples.values():
            while queue and queue[0][0] < now - 30:
                queue.popleft()
        durations = [v for _, v in self.samples['http_req_duration']]
        failures = [v for _, v in self.samples['http_req_failed']]
        checks = [v for _, v in self.samples['checks']]
        return {
            'time': now, 'requests': len(durations),
            'p95_ms': percentile(durations),
            'http_failure_rate': sum(failures) / len(failures) if failures else None,
            'check_failure_rate': 1 - sum(checks) / len(checks) if checks else None,
            'dropped_iterations': self.dropped,
        }


class Guard:
    """Require a full window, then a sustained breach; ordinary SLO misses do not abort."""
    def __init__(self, started):
        self.started = started
        self.breach_since = {'latency': None, 'errors': None}

    def reason(self, now, current, app_up):
        if current['dropped_iterations']:
            return 'dropped_iterations: target arrival rate not sustained'
        if app_up != [1.0]:
            return 'application scrape unavailable: monitoring or backend failure'
        if now - self.started < 30:
            return None
        if not current['requests']:
            return 'no completed requests in the last 30s'
        bad = {'latency': (current['p95_ms'] or 0) >= 3000,
               'errors': (current['http_failure_rate'] or 0) >= .10}
        for key, exceeds in bad.items():
            if not exceeds:
                self.breach_since[key] = None
            elif self.breach_since[key] is None:
                self.breach_since[key] = now
            if self.breach_since[key] is not None and now - self.breach_since[key] >= 30:
                return key + ': 30s rolling threshold sustained for 30s'
        return None


def build_cases(base):
    filters = get_json(base + '/api/v1/games/filters')['data']
    genre = next((g for g in filters['genres'] if 'Role-playing' in g['name']), filters['genres'][0])
    platform = next((p for p in filters['platforms'] if 'PC (Microsoft Windows)' == p['name']), filters['platforms'][0])
    specs = [
        ('list-baldur', {'keyword': 'Baldur'}),
        ('list-all', {}),
        ('list-broad-a', {'keyword': 'a'}),
        ('list-no-match', {'keyword': 'zzzz_k6_no_match_20261026'}),
        ('list-deep-a', {'keyword': 'a', 'page': 100}),
        ('list-genre', {'genreIds': genre['id']}),
        ('list-platform', {'platformIds': platform['id']}),
        ('list-combined', {'keyword': 'a', 'genreIds': genre['id'], 'platformIds': platform['id']}),
    ] + [('sort-' + sort.lower(), {'sort': sort}) for sort in ('LATEST', 'TITLE', 'RATING', 'LIBRARY', 'PLAY_TIME')]
    cases = [{'name': name, 'kind': 'page', 'path': '/api/v1/games/page?' + urllib.parse.urlencode({'page': 0, 'size': 20, **query})} for name, query in specs]
    cases += [{'name': 'suggest-' + term, 'kind': 'suggestions', 'path': '/api/v1/games/suggestions?keyword=' + term} for term in ('a', 'al')]
    return cases, {'genre': genre, 'platform': platform}


def telemetry(prom):
    queries = {
        'app_up': 'up{job="gamelog"}',
        'process_cpu': 'process_cpu_usage{job="gamelog"}',
        'system_cpu': 'system_cpu_usage{job="gamelog"}',
        'heap_bytes': 'sum(jvm_memory_used_bytes{job="gamelog",area="heap"})',
        'gc_pause_seconds_per_second': 'sum(rate(jvm_gc_pause_seconds_sum{job="gamelog"}[30s]))',
        'pool_active': 'hikaricp_connections_active{job="gamelog"}',
        'pool_pending': 'hikaricp_connections_pending{job="gamelog"}',
        'pool_max': 'hikaricp_connections_max{job="gamelog"}',
        'pool_acquire_max_seconds': 'hikaricp_connections_acquire_seconds_max{job="gamelog"}',
    }
    result = {'time': time.time()}
    for name, query in queries.items():
        try:
            data = get_json(prom + '/api/v1/query?' + urllib.parse.urlencode({'query': query}), 3)
            result[name] = [float(row['value'][1]) for row in data['data']['result']]
        except Exception as exc:
            result[name] = {'error': str(exc)}
    return result


def run_stage(args, output, case, rate, duration, phase, run_number):
    run_id = f"{case['name']}-{phase}-{rate}rps-{run_number}"
    directory = output / run_id
    directory.mkdir()
    raw_path = directory / 'points.jsonl'
    env = dict(os.environ, BASE_URL=args.base_url, CASE_PATH=case['path'], CASE_NAME=case['name'],
               CASE_KIND=case['kind'], RATE=str(rate), DURATION_SECONDS=str(duration),
               SUMMARY_PATH=str(directory / 'summary.json'),
               K6_PROMETHEUS_RW_SERVER_URL=args.prometheus + '/api/v1/write',
               K6_PROMETHEUS_RW_TREND_STATS='avg,p(95),max',
               K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM='false')
    command = [shutil.which('k6'), 'run', '--quiet', '--address', '',
               '-o', 'experimental-prometheus-rw', '-o', 'json=' + str(raw_path),
               '--tag', 'suite=' + output.name, '--tag', 'testid=' + run_id,
               '--tag', 'phase=' + phase, '--tag', 'case=' + case['name'],
               '--tag', 'target_rps=' + str(rate), str(ROOT / 'k6/search-arrival.js')]
    start = time.time()
    window = Window()
    reason, reader, pending = None, None, ''
    guard = Guard(start)
    windows, observations = [], []
    print(f"START {run_id} ({duration}s)", flush=True)
    with (directory / 'k6.log').open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, env=env, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        try:
            while process.poll() is None:
                time.sleep(5)
                if reader is None and raw_path.exists():
                    reader = raw_path.open(encoding='utf-8')
                if reader:
                    lines = (pending + reader.read()).split('\n')
                    pending = lines.pop()
                    for line in lines:
                        if line.strip():
                            window.add(json.loads(line))
                now = time.time()
                current = window.read(now)
                windows.append(current)
                observations.append(telemetry(args.prometheus))
                reason = guard.reason(now, current, observations[-1]['app_up'])
                if now - start > duration + 45:
                    reason = 'stage exceeded duration and shutdown allowance'
                if reason:
                    process.terminate()
                    break
            process.wait(timeout=20)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
            if reader:
                reader.close()
    end = time.time()
    result = {'run_id': run_id, 'case': case, 'rate': rate, 'duration': duration, 'phase': phase,
              'start': start, 'end': end, 'exit_code': process.returncode, 'stop_reason': reason,
              'rolling_windows': windows, 'telemetry': observations}
    if (directory / 'summary.json').exists():
        metrics = get_json((directory / 'summary.json').as_uri())['metrics']
        result['metrics'] = {name: metric['values'] for name, metric in metrics.items()}
    # Raw points remain the authority if a safety abort prevented handleSummary.
    durations, failures, checks, count, dropped = [], [], [], 0, 0
    if raw_path.exists():
        with raw_path.open(encoding='utf-8') as points:
            for line in points:
                try:
                    point = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if point.get('type') != 'Point':
                    continue
                name, value = point['metric'], point['data']['value']
                if name == 'http_req_duration': durations.append(value)
                elif name == 'http_req_failed': failures.append(value)
                elif name == 'checks': checks.append(value)
                elif name == 'http_reqs': count += value
                elif name == 'dropped_iterations': dropped += value
    result['observed'] = {
        'p95_ms': percentile(durations), 'mean_ms': sum(durations) / len(durations) if durations else None,
        'http_failure_rate': sum(failures) / len(failures) if failures else None,
        'check_failure_rate': 1 - sum(checks) / len(checks) if checks else None,
        'requests': count, 'dropped_iterations': dropped,
        'completed_rps_wall_clock': count / (end - start),
    }
    result['planned_requests'] = rate * duration
    result['valid'] = not reason and process.returncode in (0, 99) and count >= rate * duration - 1 and dropped == 0
    result['passed'] = result['valid'] and result['observed']['p95_ms'] < 500 and result['observed']['http_failure_rate'] < .01 and result['observed']['check_failure_rate'] == 0
    save(directory / 'result.json', result)
    print(f"END {run_id}: p95={result['observed']['p95_ms']} failed={result['observed']['http_failure_rate']} requests={count} stop={reason}", flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:8080')
    parser.add_argument('--prometheus', default='http://127.0.0.1:9090')
    parser.add_argument('--cases', help='Comma-separated case names; omitted means all 15 conditions')
    parser.add_argument('--rates', default='1,5,10,20')
    parser.add_argument('--duration', type=int, default=120)
    parser.add_argument('--warmup', type=int, default=30)
    parser.add_argument('--recovery', type=int, default=30)
    parser.add_argument('--no-repeat', action='store_true', help='Skip boundary repeat (only for harness verification)')
    parser.add_argument('--preflight-only', action='store_true')
    parser.add_argument('--resume', help='Existing results directory; reuse completed stages with matching settings')
    args = parser.parse_args()
    if any(x <= 0 for x in (args.duration, args.warmup, args.recovery)):
        parser.error('durations must be positive')
    rates = [int(value) for value in args.rates.split(',')]
    if not rates or rates != sorted(set(rates)) or min(rates) < 1:
        parser.error('rates must be unique ascending positive integers')
    if not shutil.which('k6'):
        parser.error('k6 is not on PATH')
    output = Path(args.resume).resolve() if args.resume else ROOT / 'k6/results' / ('arrival-' + dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
    if args.resume and output.parent != (ROOT / 'k6/results').resolve():
        parser.error('resume directory must be a direct child of k6/results')
    output.mkdir(parents=True, exist_ok=bool(args.resume))
    if get_json(args.base_url + '/actuator/health').get('status') != 'UP':
        raise RuntimeError('Backend health is not UP')
    observations = telemetry(args.prometheus)
    if observations['app_up'] != [1.0]:
        raise RuntimeError('Prometheus application target is not UP')
    cases, selected_filters = build_cases(args.base_url)
    if args.cases:
        selected = set(args.cases.split(','))
        unknown = selected - {case['name'] for case in cases}
        if unknown: parser.error('Unknown cases: ' + ','.join(unknown))
        cases = [case for case in cases if case['name'] in selected]
    manifest = {'arguments': vars(args), 'cases': cases, 'filters': selected_filters,
                'rates': rates, 'preflight_telemetry': observations, 'smoke': [], 'results': [], 'completed_cases': []}
    if args.resume:
        manifest = json.loads((output / 'manifest.json').read_text(encoding='utf-8'))
        for name in ('base_url', 'prometheus', 'duration', 'warmup', 'recovery', 'no_repeat'):
            if manifest['arguments'][name] != getattr(args, name):
                parser.error('resume settings differ: ' + name)
        if manifest['cases'] != cases or manifest['rates'] != rates:
            parser.error('resume cases or rates differ')
        manifest.setdefault('completed_cases', [])
        manifest.setdefault('resumed_at', []).append(dt.datetime.now(dt.timezone.utc).isoformat())
        known = {r['run_id'] for r in manifest['results']}
        manifest['incomplete_directories'] = [p.name for p in output.iterdir() if p.is_dir() and p.name not in known]
        manifest['status'] = 'running'
        manifest.pop('finished', None)
    save(output / 'manifest.json', manifest)
    print('OUTPUT ' + str(output), flush=True)
    # All smoke requests run before load, to distinguish functional failures from overload.
    for case in cases:
        started = time.perf_counter()
        data = get_json(args.base_url + case['path'], 15)
        payload = data['data']
        if case['kind'] == 'page':
            assert isinstance(payload['content'], list) and isinstance(payload['totalElements'], int)
            count = payload['totalElements']
        else:
            assert isinstance(payload, list)
            count = len(payload)
        assert data['resultCode'] == '200-1'
        manifest['smoke'].append({'case': case['name'], 'count': count, 'milliseconds': (time.perf_counter() - started) * 1000})
        save(output / 'manifest.json', manifest)
    if args.preflight_only:
        print('PREFLIGHT OK', flush=True)
        return
    number = max([int(p.name.rsplit('-', 1)[-1]) for p in output.iterdir() if p.is_dir()] or [0])
    def stage(case, rate, duration, phase):
        nonlocal number
        if args.resume and phase != 'warmup':
            previous = [r for r in manifest['results'] if r['case']['name'] == case['name'] and r['rate'] == rate and r['phase'] == phase]
            if previous:
                print('REUSE ' + previous[-1]['run_id'], flush=True)
                return previous[-1]
        number += 1
        result = run_stage(args, output, case, rate, duration, phase, number)
        manifest['results'].append(result)
        save(output / 'manifest.json', manifest)
        time.sleep(args.recovery)
        return result
    for case in cases:
        if case['name'] in manifest['completed_cases']:
            continue
        warmup = stage(case, 1, args.warmup, 'warmup')
        if not warmup['valid']:
            print('HALT: invalid warmup; investigate before continuing', flush=True)
            break
        previous, boundary = None, None
        for rate in rates:
            result = stage(case, rate, args.duration, 'measure')
            if boundary is None and not result['passed']:
                boundary = (previous, rate)
            if not result['valid']:
                break
            previous = rate
        if boundary and not args.no_repeat:
            for rate in boundary:
                if rate is not None:
                    retry = stage(case, rate, args.duration, 'repeat')
                    if not retry['valid']:
                        break
        if get_json(args.base_url + '/actuator/health', 5).get('status') != 'UP':
            print('HALT: backend did not recover', flush=True)
            break
        manifest['completed_cases'].append(case['name'])
        save(output / 'manifest.json', manifest)
    manifest['finished'] = dt.datetime.now(dt.timezone.utc).isoformat()
    manifest['status'] = 'completed' if len(manifest['completed_cases']) == len(cases) else 'stopped'
    save(output / 'manifest.json', manifest)
    print('DONE ' + str(output), flush=True)


if __name__ == '__main__':
    main()

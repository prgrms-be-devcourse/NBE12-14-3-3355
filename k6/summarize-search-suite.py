"""Create a Markdown readout from saved measurements; never contacts the backend."""
import argparse
import json
from pathlib import Path


def render(manifest, source):
    results = [r for r in manifest['results'] if r['phase'] != 'warmup']
    completed = [r for r in results if r['valid']]
    passed = [r for r in completed if r['passed']]
    settings = manifest['arguments']
    environment_path = source / 'environment.json'
    environment = json.loads(environment_path.read_text(encoding='utf-8')) if environment_path.exists() else {}
    counts = environment.get('counts', {})
    grafana = 'http://localhost:3001/d/gamelog-search-arrival?var-suite=' + source.name
    if results:
        grafana += f"&from={int(min(r['start'] for r in manifest['results'])*1000)}&to={int(max(r['end'] for r in manifest['results'])*1000)}"
    lines = ['# 게임 검색 부하 테스트 결과', '',
             f"실행 묶음: `{source.name}`. 상태: {manifest.get('status', '진행 중')}. ",
             f"완료한 조건 {len(manifest.get('completed_cases', []))}/{len(manifest['cases'])}, "
             f"유효 측정 {len(completed)}회 중 임시 기준 통과 {len(passed)}회.", '',
             '## 측정 조건', '',
             '- 로컬 백엔드 직접 호출. 브라우저 렌더링 및 Next.js 프록시는 포함하지 않는다.',
             f"- {'/'.join(map(str,manifest['rates']))} RPS, 각 {settings['duration']}초, 조건별 1 RPS {settings['warmup']}초 예열, 실행 사이 {settings['recovery']}초 회복, 최대 100 VU.",
             '- 임시 기준: p95 < 500ms, HTTP 실패율 < 1%, 응답 검사 모두 통과, 미전송 0건.',
             '- p95 ≥ 3초 또는 HTTP 실패율 ≥ 10%인 최근 30초 창이 30초 지속되면 중단한다.',
             '- 중단 결과의 응답 시간은 완료된 요청만의 관측값이며 정상 완료 결과로 해석하지 않는다.', '',
             '## 실행 결과', '',
             '| 조건 | 단계 | 목표 RPS | 완료 요청 | 실제 RPS¹ | p95 ms | HTTP 실패율 | 미전송 | 판정 |',
             '|---|---|---:|---:|---:|---:|---:|---:|---|']
    for r in results:
        o = r['observed']
        rate = r.get('metrics', {}).get('http_reqs', {}).get('rate')
        p95 = o['p95_ms']
        failure_rate = o['http_failure_rate']
        failure_text = f'{failure_rate:.2%}' if failure_rate is not None else '—'
        status = '통과' if r['passed'] else ('기준 초과' if r['valid'] else '중단/무효')
        lines.append(f"| {r['case']['name']} | {r['phase']} | {r['rate']} | {int(o['requests'])} | "
                     f"{f'{rate:.2f}' if rate is not None else '—'} | {f'{p95:.1f}' if p95 is not None else '—'} | "
                     f"{failure_text} | "
                     f"{int(o['dropped_iterations'])} | {status} |")
    lines += ['', '¹ 정상 종료 k6 요약의 http_reqs rate. 미전송 요청과 취소된 진행 중 요청은 완료 요청 수에 포함되지 않는다.', '',
              '## 같은 시간의 서버 관측', '',
              '| 실행 | 앱 CPU 최대² | 힙 최대 MiB | 풀 사용 최대 | 풀 대기 최대 | 연결 획득 최대 ms |',
              '|---|---:|---:|---:|---:|---:|']
    for r in results:
        def maximum(name):
            values = [v for sample in r['telemetry'] for v in sample.get(name, []) if isinstance(sample.get(name), list) and isinstance(v, (int, float))]
            return max(values) if values else None
        def fmt(value, factor=1):
            return f'{value * factor:.2f}' if value is not None else '—'
        lines.append(f"| {r['run_id']} | {fmt(maximum('process_cpu'),100)}% | {fmt(maximum('heap_bytes'),1/1024**2)} | "
                     f"{fmt(maximum('pool_active'))} | {fmt(maximum('pool_pending'))} | {fmt(maximum('pool_acquire_max_seconds'),1000)} |")
    lines += ['', '² Micrometer process_cpu_usage × 100. 5초 간격 관측의 최댓값이며 순간 최고점을 보장하지 않는다.', '',
              '## 제한과 증거', '',
              f"- 기록된 데이터: 게임 {counts.get('games','미기록')}건, 사용자 게임 기록 {counts.get('user_games','미기록')}건, 리뷰 {counts.get('reviews','미기록')}건. 집계 정렬의 대규모 사용자 데이터 성능은 미검증.",
              '- 같은 PC에서 JVM·Docker MySQL·k6·모니터링을 실행했다. 운영 환경 처리 용량으로 환산하지 않는다.',
              '- DB 내부 대기 및 쿼리별 시간은 직접 수집하지 않았다. 연결 풀 관측만으로 SQL 병목을 확정하지 않는다.',
              '- 고정 검색어를 반복하는 예열 상태 시험이며 실제 사용자 요청 분포·콜드 캐시 시험이 아니다.',
              '- 각 조건의 상세 URL, 필터 값, 단일 요청 결과는 manifest.json에 기록되어 있다.',
              f'- 원본 결과: `{source.resolve().as_posix()}`.',
              f'- [해당 시간 범위의 Grafana 대시보드]({grafana})', '', '## 중단 및 재개 이력', '']
    for directory in manifest.get('incomplete_directories', []):
        lines.append(f'- `{directory}`: 실행기 중단으로 완결된 결과가 없으므로 측정 결과에서 제외했다. 원본은 보존했다.')
    for r in results:
        if r['stop_reason']:
            lines.append(f"- `{r['run_id']}`: {r['stop_reason']}")
    if not manifest.get('incomplete_directories') and not any(r['stop_reason'] for r in results):
        lines.append('- 기록된 중단 없음.')
    lines += ['', '## 재실행', '', '```powershell', 'python -B k6/run-search-suite.py', '```', '']
    return '\n'.join(lines)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads((args.directory / 'manifest.json').read_text(encoding='utf-8'))
    args.output.write_text(render(manifest, args.directory), encoding='utf-8')
    print(args.output)

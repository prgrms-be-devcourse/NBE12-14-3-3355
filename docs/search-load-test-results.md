# 게임 검색 부하 테스트 결과

이 문서는 추천 계산 완료 조건을 제거하기 전의 측정 결과다. 2026-10-27 검색·자동완성·COUNT 및 상세 접근의 완료 조건을 제거했으며, 변경 후 성능은 재측정하지 않았다.

2026-10-27 사용자 요청으로 추가 시험을 종료했다. 최종 점검 시 Python/k6 부하 발생 프로세스는 없었다.

## 확인된 결론

- 임시 기준은 p95 500ms 미만, HTTP 실패율 1% 미만, 응답 검사 전부 통과, 미전송 0건이다. 아래 통과는 이 로컬 데이터와 2분 시험 구간에 한정한다.
- 장르 필터와 검색어+장르+플랫폼 복합 조건은 1/5/10/20 RPS를 모두 통과했다. 20 RPS의 p95는 각각 292ms와 381ms였다.
- 최신순은 5 RPS에서 첫 측정 443ms, 재측정 430ms로 통과했다. 10 RPS는 652ms 및 595ms로 기준을 넘었다.
- Baldur, 검색어 없는 목록, a 검색, 깊은 페이지, 플랫폼 필터는 1 RPS부터 기준을 넘었다. 첫 측정과 재측정의 p95 범위는 약 757~1,280ms였다.
- 결과 없는 검색은 5 RPS 첫 측정 484ms였지만 재측정 547ms로 기준을 넘었다. 안정적인 5 RPS 통과를 확인했다고 결론 내릴 수 없다.
- 제목순은 1 RPS에서 p95 494ms로 한 번 통과했으나 전체 단계와 재측정은 완료하지 못했다.
- 15개 조건 중 9개 완료, 제목순 일부 측정, 나머지 5개는 미측정이다. 사용자 평점순·서재 등록 수순·플레이 시간순 및 자동완성 a/al 결과는 없다.

## 병목 후보와 해석 범위

낮은 부하에서도 느린 조건은 DB 연결 대기 없이 기준을 넘었다. 고부하의 일부 조건에서는 최대 10개인 DB 풀이 모두 사용되고 연결 대기가 늘었으며, a 검색 10 RPS에서는 관측한 대기 스레드 최댓값이 125였다. SQL 조회 비용과 그에 따른 연결 풀 대기를 우선 조사할 근거다. 연결 풀 크기 자체가 근본 원인이라고 단정하거나 풀을 늘리는 것으로 해결된다고 결론 내리지는 않는다.

현재 IDE의 추천 계산 완료 게임만 노출하는 설정은 활성화돼 있다. 해당 조건을 포함한 EXPLAIN은 기본 검색의 넓은 인덱스 범위 스캔, 별도 정렬, COUNT 및 추천 계산 완료 여부 확인을 보여준다. 필터 COUNT는 필터 인덱스부터 시작하지만 플랫폼 조건의 후보 규모가 장르보다 크게 추정된다. 이는 추정 실행 계획이며 SQL별 실제 소요 시간은 측정하지 않았다. 근거는 원본 폴더의 explain-ready-only.txt 및 explain-filters.txt이다. 최초 explain.txt는 노출 조건을 생략한 참고 계획이므로 현재 쿼리의 근거로 사용하지 않는다.

중단 시험의 p95와 실패율은 종료 전에 완료된 요청만 반영한다. 미전송 및 진행 중 취소 요청이 있으므로 정상 완료 시험과 같은 처리 용량 수치로 해석하지 않는다. 여러 차례의 백엔드 재시작과 로컬 환경 변동도 비교의 한계다. 검색 코드 및 DB 인덱스 개선은 수행하지 않았다.

실행 묶음: `arrival-20261026T062232Z`. 상태: stopped_by_user. 
완료한 조건 9/15, 유효 측정 32회 중 임시 기준 통과 14회.

## 측정 조건

- 로컬 백엔드 직접 호출. 브라우저 렌더링 및 Next.js 프록시는 포함하지 않는다.
- 1/5/10/20 RPS, 각 120초, 조건별 1 RPS 30초 예열, 실행 사이 30초 회복, 최대 100 VU.
- 임시 기준: p95 < 500ms, HTTP 실패율 < 1%, 응답 검사 모두 통과, 미전송 0건.
- p95 ≥ 3초 또는 HTTP 실패율 ≥ 10%인 최근 30초 창이 30초 지속되면 중단한다.
- 중단 결과의 응답 시간은 완료된 요청만의 관측값이며 정상 완료 결과로 해석하지 않는다.

## 실행 결과

| 조건 | 단계 | 목표 RPS | 완료 요청 | 실제 RPS¹ | p95 ms | HTTP 실패율 | 미전송 | 판정 |
|---|---|---:|---:|---:|---:|---:|---:|---|
| list-baldur | measure | 1 | 120 | 1.00 | 1279.6 | 0.00% | 0 | 기준 초과 |
| list-baldur | measure | 5 | 261 | — | 10044.8 | 75.48% | 0 | 중단/무효 |
| list-baldur | repeat | 1 | 120 | 1.00 | 926.7 | 0.00% | 0 | 기준 초과 |
| list-all | measure | 1 | 121 | 1.00 | 756.7 | 0.00% | 0 | 기준 초과 |
| list-all | measure | 5 | 546 | — | 3051.6 | 0.00% | 0 | 중단/무효 |
| list-all | repeat | 1 | 121 | 1.00 | 1033.6 | 0.00% | 0 | 기준 초과 |
| list-broad-a | measure | 1 | 120 | 1.00 | 887.3 | 0.00% | 0 | 기준 초과 |
| list-broad-a | measure | 5 | 600 | 4.95 | 1694.1 | 0.00% | 0 | 기준 초과 |
| list-broad-a | measure | 10 | 202 | — | 10001.3 | 50.00% | 1 | 중단/무효 |
| list-broad-a | repeat | 1 | 120 | 1.00 | 857.3 | 0.00% | 0 | 기준 초과 |
| list-no-match | measure | 1 | 120 | 1.00 | 419.7 | 0.00% | 0 | 통과 |
| list-no-match | measure | 5 | 601 | 4.99 | 484.4 | 0.00% | 0 | 통과 |
| list-no-match | measure | 10 | 1201 | 9.96 | 705.0 | 0.00% | 0 | 기준 초과 |
| list-no-match | measure | 20 | 150 | — | 7205.5 | 0.00% | 49 | 중단/무효 |
| list-no-match | repeat | 5 | 600 | 4.99 | 547.4 | 0.00% | 0 | 기준 초과 |
| list-no-match | repeat | 10 | 1201 | 9.96 | 1324.2 | 0.00% | 0 | 기준 초과 |
| list-deep-a | measure | 1 | 121 | 1.00 | 870.9 | 0.00% | 0 | 기준 초과 |
| list-deep-a | measure | 5 | 601 | 4.96 | 1597.3 | 0.00% | 0 | 기준 초과 |
| list-deep-a | measure | 10 | 152 | — | 10000.9 | 19.08% | 1 | 중단/무효 |
| list-deep-a | repeat | 1 | 121 | 1.00 | 864.0 | 0.00% | 0 | 기준 초과 |
| list-genre | measure | 1 | 121 | 1.01 | 181.3 | 0.00% | 0 | 통과 |
| list-genre | measure | 5 | 600 | 5.00 | 178.8 | 0.00% | 0 | 통과 |
| list-genre | measure | 10 | 1200 | 9.99 | 212.0 | 0.00% | 0 | 통과 |
| list-genre | measure | 20 | 2401 | 19.97 | 292.4 | 0.00% | 0 | 통과 |
| list-platform | measure | 1 | 121 | 1.00 | 765.8 | 0.00% | 0 | 기준 초과 |
| list-platform | measure | 5 | 601 | 4.96 | 1423.4 | 0.00% | 0 | 기준 초과 |
| list-platform | measure | 10 | 152 | — | 9918.5 | 4.61% | 1 | 중단/무효 |
| list-platform | repeat | 1 | 121 | 1.00 | 773.8 | 0.00% | 0 | 기준 초과 |
| list-combined | measure | 1 | 121 | 1.01 | 219.2 | 0.00% | 0 | 통과 |
| list-combined | measure | 5 | 601 | 5.00 | 214.4 | 0.00% | 0 | 통과 |
| list-combined | measure | 10 | 1201 | 9.99 | 270.6 | 0.00% | 0 | 통과 |
| list-combined | measure | 20 | 2400 | 19.96 | 380.8 | 0.00% | 0 | 통과 |
| sort-latest | measure | 1 | 121 | 1.01 | 381.7 | 0.00% | 0 | 통과 |
| sort-latest | measure | 5 | 601 | 4.99 | 443.3 | 0.00% | 0 | 통과 |
| sort-latest | measure | 10 | 1201 | 9.97 | 651.7 | 0.00% | 0 | 기준 초과 |
| sort-latest | measure | 20 | 197 | — | 4938.4 | 0.00% | 3 | 중단/무효 |
| sort-latest | repeat | 5 | 600 | 4.99 | 430.1 | 0.00% | 0 | 통과 |
| sort-latest | repeat | 10 | 1201 | 9.97 | 595.4 | 0.00% | 0 | 기준 초과 |
| sort-title | measure | 1 | 120 | 1.00 | 494.0 | 0.00% | 0 | 통과 |

¹ 정상 종료 k6 요약의 http_reqs rate. 미전송 요청과 취소된 진행 중 요청은 완료 요청 수에 포함되지 않는다.

## 같은 시간의 서버 관측

| 실행 | 앱 CPU 최대² | 힙 최대 MiB | 풀 사용 최대 | 풀 대기 최대 | 연결 획득 최대 ms |
|---|---:|---:|---:|---:|---:|
| list-baldur-measure-1rps-2 | 1.67% | 251.95 | 2.00 | 0.00 | 5.18 |
| list-baldur-measure-5rps-5 | 2.11% | 389.16 | 10.00 | 76.00 | 15323.14 |
| list-baldur-repeat-1rps-6 | 0.94% | 370.38 | 1.00 | 0.00 | 15323.14 |
| list-all-measure-1rps-8 | 1.69% | 369.67 | 1.00 | 0.00 | 1.17 |
| list-all-measure-5rps-9 | 1.64% | 339.04 | 10.00 | 5.00 | 1431.72 |
| list-all-repeat-1rps-10 | 1.65% | 337.72 | 1.00 | 0.00 | 1431.72 |
| list-broad-a-measure-1rps-12 | 0.89% | 330.03 | 1.00 | 0.00 | 7.36 |
| list-broad-a-measure-5rps-13 | 1.43% | 315.99 | 8.00 | 0.00 | 48.72 |
| list-broad-a-measure-10rps-14 | 1.74% | 308.91 | 10.00 | 125.00 | 12543.50 |
| list-broad-a-repeat-1rps-15 | 1.07% | 321.08 | 1.00 | 0.00 | 24023.09 |
| list-no-match-measure-1rps-17 | 0.86% | 324.76 | 0.00 | 0.00 | 1.33 |
| list-no-match-measure-5rps-18 | 1.02% | 324.23 | 3.00 | 0.00 | 1.17 |
| list-no-match-measure-10rps-19 | 1.22% | 310.97 | 7.00 | 0.00 | 1.17 |
| list-no-match-measure-20rps-20 | 2.13% | 317.02 | 10.00 | 91.00 | 4915.85 |
| list-no-match-repeat-5rps-21 | 1.07% | 317.51 | 3.00 | 0.00 | 9332.55 |
| list-no-match-repeat-10rps-22 | 1.85% | 314.80 | 10.00 | 4.00 | 625.95 |
| list-deep-a-measure-1rps-25 | 1.58% | 356.96 | 1.00 | 0.00 | 1.46 |
| list-deep-a-measure-5rps-26 | 2.46% | 360.74 | 8.00 | 0.00 | 17.92 |
| list-deep-a-measure-10rps-27 | 2.20% | 278.90 | 10.00 | 84.00 | 8283.40 |
| list-deep-a-repeat-1rps-28 | 1.85% | 320.31 | 1.00 | 0.00 | 17528.44 |
| list-genre-measure-1rps-30 | 1.78% | 336.22 | 0.00 | 0.00 | 1.24 |
| list-genre-measure-5rps-31 | 2.28% | 322.32 | 1.00 | 0.00 | 1.24 |
| list-genre-measure-10rps-32 | 2.47% | 330.53 | 3.00 | 0.00 | 1.32 |
| list-genre-measure-20rps-33 | 3.78% | 304.52 | 6.00 | 0.00 | 7.58 |
| list-platform-measure-1rps-35 | 2.54% | 316.19 | 1.00 | 0.00 | 7.58 |
| list-platform-measure-5rps-36 | 2.15% | 316.37 | 7.00 | 0.00 | 1.26 |
| list-platform-measure-10rps-37 | 3.63% | 241.75 | 10.00 | 89.00 | 9035.49 |
| list-platform-repeat-1rps-38 | 2.01% | 315.75 | 1.00 | 0.00 | 14786.52 |
| list-combined-measure-1rps-40 | 1.76% | 315.26 | 0.00 | 0.00 | 1.18 |
| list-combined-measure-5rps-41 | 2.27% | 311.31 | 1.00 | 0.00 | 1.20 |
| list-combined-measure-10rps-42 | 2.49% | 302.80 | 2.00 | 0.00 | 1.46 |
| list-combined-measure-20rps-43 | 2.68% | 303.77 | 9.00 | 0.00 | 58.88 |
| sort-latest-measure-1rps-45 | 1.99% | 302.99 | 0.00 | 0.00 | 58.88 |
| sort-latest-measure-5rps-46 | 1.73% | 303.13 | 2.00 | 0.00 | 3.98 |
| sort-latest-measure-10rps-47 | 2.25% | 293.38 | 7.00 | 0.00 | 13.39 |
| sort-latest-measure-20rps-48 | 3.36% | 214.92 | 10.00 | 70.00 | 3576.78 |
| sort-latest-repeat-5rps-49 | 1.93% | 298.49 | 2.00 | 0.00 | 6701.72 |
| sort-latest-repeat-10rps-50 | 2.19% | 286.70 | 6.00 | 0.00 | 1.10 |
| sort-title-measure-1rps-52 | 2.45% | 288.25 | 0.00 | 0.00 | 1.30 |

² Micrometer process_cpu_usage × 100. 5초 간격 관측의 최댓값이며 순간 최고점을 보장하지 않는다.

## 제한과 증거

- 기록된 데이터: 게임 284294건, 사용자 게임 기록 2건, 리뷰 2건. 집계 정렬의 대규모 사용자 데이터 성능은 미검증.
- 같은 PC에서 JVM·Docker MySQL·k6·모니터링을 실행했다. 운영 환경 처리 용량으로 환산하지 않는다.
- DB 내부 대기 및 쿼리별 시간은 직접 수집하지 않았다. 연결 풀 관측만으로 SQL 병목을 확정하지 않는다.
- 고정 검색어를 반복하는 예열 상태 시험이며 실제 사용자 요청 분포·콜드 캐시 시험이 아니다.
- 각 조건의 상세 URL, 필터 값, 단일 요청 결과는 manifest.json에 기록되어 있다.
- 원본 결과: `C:/Users/문창원/Desktop/프로그래머스/NBE12-14-2-3355/k6/results/arrival-20261026T062232Z`.
- [해당 시간 범위의 Grafana 대시보드](http://localhost:3001/d/gamelog-search-arrival?var-suite=arrival-20261026T062232Z&from=1792995762988&to=1793018116579)

## 중단 및 재개 이력

- `list-baldur-measure-5rps-3`: 실행기 중단으로 완결된 결과가 없으므로 측정 결과에서 제외했다. 원본은 보존했다.
- `list-baldur-measure-5rps-5`: latency: 30s rolling threshold sustained for 30s
- `list-all-measure-5rps-9`: latency: 30s rolling threshold sustained for 30s
- `list-broad-a-measure-10rps-14`: dropped_iterations: target arrival rate not sustained
- `list-no-match-measure-20rps-20`: dropped_iterations: target arrival rate not sustained
- `list-deep-a-measure-10rps-27`: dropped_iterations: target arrival rate not sustained
- `list-platform-measure-10rps-37`: dropped_iterations: target arrival rate not sustained
- `sort-latest-measure-20rps-48`: dropped_iterations: target arrival rate not sustained

## 재실행

```powershell
python -B k6/run-search-suite.py
```

# 추천 API 고정 10 VU 테스트

기존 `*-recommendations.js`의 VU 증감 테스트와 별도로 실행한다.
`personalized-fixed-10vu.js`는 온보딩 5개·기록 기반 5개의 기존 계정을
각각 독립된 1 VU에 고정 배정한다. 전역 VU ID로 계정을 선택하지 않는다.
`related-fixed-10vu.js`는 로그인 없이 기준 게임 하나에 10 VU로 요청한다.
두 테스트를 동시에 실행하지 않는다.

## 실행 조건

기존 README의 백엔드·Prometheus 준비 조건을 따른다. 현재 서버의 빌드,
DB 이름·데이터, 추천 설정 3개, JVM, SQL 로그, 커넥션 풀 설정을 별도로 기록한다.
과거 Java 기준 측정은 `gamelog`와 추천 설정 3개 true 조건이므로 현재 서버와
같다고 가정하지 않는다. 기존 계정 파일은 그대로 재사용하며 신규 가입이나
선호·게임 기록 쓰기를 하지 않는다. 로그인에 따른 인증 토큰 저장은 발생한다.

| 설정 | 기본값 | 의미 |
|---|---|---|
| VU | 10 고정 | 맞춤 추천은 계정당 1 VU, 그룹별 5 VU |
| `WARMUP_SECONDS` | 30 | 워밍업 시간(0 이상의 정수, 초) |
| `MEASUREMENT_SECONDS` | 120 | 본측정 시간(1 이상의 정수, 초) |
| `SLEEP_SECONDS` | 1 | 응답 검사 후 대기 시간(0 이상) |
| `RUN_ID` | 도우미에서 생성 | 실행 식별자, 영문·숫자·밑줄·하이픈 |
| `RELATED_GAME_ID` | 목록 첫 게임 | 전후 비교 시 동일한 게임 ID 지정 |
| `SUMMARY_FILE` | 저장 안 함 | 민감 정보를 제외한 JSON 저장 경로 |

기본 부하 구간은 워밍업 30초 + 본측정 120초로 합계 2분 30초이다.
총 실행 시간은 setup + 워밍업 + 본측정 + 최대 30초 종료 유예이다.
워밍업부터 같은 VU를 유지한다. `SMOKE`·`STAGE_DURATION`은 새 테스트에서
사용하지 않는다. VU·duration·iterations를 CLI 옵션으로 덮어쓰지 않는다.

## 실행

프로젝트 루트에서 실행한다. 먼저 1초 대기로 실행하고 결과를 확인한 뒤에만
같은 조건으로 0.1초, 마지막으로 0초 대기를 각각 별도 실행한다.
아래 명령은 1초 기준 실행이며 연속 자동 증폭은 하지 않는다.

```sh
mkdir -p k6/recommendations/results

bash k6/recommendations/run.sh personalized-fixed \
  -e RUN_ID=personalized-fixed-sleep1-01 \
  -e SLEEP_SECONDS=1 \
  -e SUMMARY_FILE=results/personalized-fixed-sleep1-01.json

# 맞춤 추천 종료 후 별도로 실행한다. 게임 ID는 로컬 DB에 맞게 확인한다.
bash k6/recommendations/run.sh related-fixed \
  -e RUN_ID=related-fixed-sleep1-01 \
  -e RELATED_GAME_ID=47808 \
  -e SLEEP_SECONDS=1 \
  -e SUMMARY_FILE=results/related-fixed-sleep1-01.json
```

반복 실행마다 RUN_ID와 파일명을 변경한다. SUMMARY_FILE은 실행 도우미의
작업 디렉터리인 `k6/recommendations/` 기준이다. 터미널에는 안전하게 선별한
지표 JSON을 출력하며 파일 저장은 선택 사항이다. `--summary-export` 대신
SUMMARY_FILE을 사용한다. 원본 summary/setup_data에는 토큰이 포함될 수 있다.
계정 파일·토큰·인증 응답·전체 환경변수를 출력하거나 Git에 추가하지 않는다.

## 결과 해석

### 전용 Grafana 대시보드

기존 통합 대시보드 대신 API별 대시보드를 사용한다. 실행 명령에 RUN_ID를
지정하는 것만으로 기존 대시보드의 쿼리나 표시 항목이 변경되지는 않는다.

- 맞춤 추천: http://localhost:3001/d/gamelog-k6-personalized-fixed
- 상세 연관 추천: http://localhost:3001/d/gamelog-k6-related-fixed

상단 `테스트 실행`에서 RUN_ID 하나를 선택하고 해당 실행 시간대로 맞춘다.
파일 provisioning이 활성화된 기존 compose에서는 약 10초 후 자동 반영된다.
응답 시간·처리량·HTTP 실패율은 해당 API의 본측정만 표시하므로 워밍업과
본측정의 범례가 중복되지 않는다. VU는 워밍업을 포함한 선택 실행 전체 구간이다.
CPU·JVM은 서버 전체 지표이며 실행 ID로 분리할 수 없으므로 시간대를 맞춰 본다.

### 지표 범위

- 모든 요청에 `phase=setup|warmup|measurement`를 표시한다.
  요청 시작 시점으로 구간을 정하므로 워밍업 중 시작한 요청이 본측정 시간에
  끝나더라도 워밍업에 포함된다. 종료 유예 중 완료된 본측정 요청도 포함된다.
- 실행 태그는 `run_id`, `profile=fixed-10vu`, `sleep_seconds`이다.
- 본측정 HTTP/check `scenario`는 기존처럼 `onboarding`·`record`·`related`이다.
  워밍업은 `warmup_onboarding`·`warmup_record`·`warmup_related`로 분리한다.
  따라서 scenario를 정확히 지정하는 기존 패널에서도 워밍업이 같은 이름의
  별도 선으로 나타나지 않는다. 이미 저장된 과거 시계열은 변경하지 않으므로
  비교 화면의 시간 범위를 수정 후 실행 구간으로 맞춘다. 여러 실행을 동시에
  조회하면 run_id별로 선이 나뉘므로 실행 필터 또는 시간 범위도 구분한다.
  실제 실행 시나리오는 계정별 `onboarding_1~5`·`record_1~5`이다.
- 판정 기준은 본측정의 HTTP 실패율 1% 미만, check 성공률 99% 초과,
  P99 60초 미만과 요청 발생 여부이다. 60초는 기존 가드 수준이며 성능 목표가 아니다.
- 저장된 전체 `http_reqs`에는 setup·워밍업이 포함된다. 본측정은
  `http_reqs{scenario:onboarding,phase:measurement}` 등 필터된 count와
  해당 그룹의 `http_req_duration`·`http_req_failed`·`checks`를 사용한다.
  필터된 summary의 rate도 본측정 시간만을 분모로 한다고 가정하지 않는다.
- Grafana에서는 `run_id`와 `phase="measurement"`를 모두 필터링한다.
  기존 패널이 phase를 구분하지 않으면 워밍업과 본측정이 함께 표시될 수 있다.
  예: `k6_http_req_duration_p95{run_id="personalized-fixed-sleep1-01",scenario="record",phase="measurement"}`.
  P95/P99는 해당 태그 조합의 누적 분포이며 짧은 구간별 백분위수가 아니다.
- 본측정 처리량 추이는 `k6_http_reqs_total`에 동일한 필터를 적용한 rate로
  확인한다. 서버 패널도 해당 실행 시간대로 맞춘다. P95/P99·실패율·처리량과
  수집 가능한 CPU·메모리·GC·DB 풀 지표를 함께 확인한다.
- 대기 시간을 줄여도 실제 RPS가 정확히 10배가 되지는 않는다.
  응답이 느려지면 고정 VU에서 실제 요청률이 줄어들 수 있다.
  결과는 고정 계정·데이터·10 VU 조건에서의 성능이며 서버 전체 한계가 아니다.
- 실패 증가·지연 악화·처리량 감소 또는 서버 재시작이 보이면 다음 조건으로
  진행하지 않고 같은 조건을 재측정한다. 자동 중단·자동 증폭은 구현하지 않는다.

## 서버 호출 없는 설정 확인

```sh
k6 inspect -e RUN_ID=inspect-personalized k6/recommendations/personalized-fixed-10vu.js
k6 inspect -e RUN_ID=inspect-related k6/recommendations/related-fixed-10vu.js
```

inspect는 setup과 부하 요청을 실행하지 않는다. 실제 계정 데이터 일치 여부와
서버 성능은 실제 실행 시 별도로 확인해야 한다.

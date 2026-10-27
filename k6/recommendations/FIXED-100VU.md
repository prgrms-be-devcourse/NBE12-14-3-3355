# 추천 API 고정 100 VU 테스트

기존 `fixed-10vu` 파일과 실행 명령은 유지하고 별도의 100 VU 테스트를 사용한다.
기본 대기 시간은 0.1초이다. 두 API는 반드시 하나씩 순차 실행한다.

| 항목 | 맞춤 추천 | 상세 연관 추천 |
|---|---|---|
| 파일 | `personalized-fixed-100vu.js` | `related-fixed-100vu.js` |
| 합계 VU | 100 | 100 |
| 계정 사용 | 기존 온보딩 5개·기록 기반 5개 | 인증 없음 |
| 배정 | 계정당 10 VU, 그룹별 50 VU | 같은 기준 게임에 100 VU |
| 기본 시간 | 워밍업 30초 + 본측정 120초 | 동일 |
| 기본 대기 | 응답 검사 후 0.1초 | 동일 |

맞춤 추천은 setup에서 계정마다 한 번 로그인하고 기존 기록·선호·추천을
검사한다. 계정별 10 VU가 그 계정의 토큰을 공유하므로 같은 계정에 동시 요청이
발생한다. 신규 가입이나 선호·게임 기록 추가/변경은 하지 않는다.
로그인으로 인한 인증 토큰 저장은 발생한다. 계정 파일은 Git에 추가하지 않는다.

## 실행

프로젝트 루트에서 맞춤 추천을 실행한다.

```sh
bash k6/recommendations/run.sh personalized-fixed-100
```

완전히 종료된 뒤 게임 상세 연관 추천을 별도로 실행한다.
비교 대상 게임 ID는 10 VU 측정과 동일하게 유지한다.

```sh
bash k6/recommendations/run.sh related-fixed-100 \
  -e RELATED_GAME_ID=47808
```

대기 시간은 `-e SLEEP_SECONDS=0.1`로 명시하거나 다른 값으로 설정할 수 있다.
워밍업·본측정 시간과 안전한 결과 저장 방법은 [10 VU 안내](FIXED-10VU.md)와 같다.
공통 설정은 별도 `fixed-100vu-config.js`를 사용한다.

## 기존 Grafana 패널

- 본측정 scenario는 `onboarding`·`record`·`related`로 기존 쿼리를 유지한다.
- 워밍업 scenario는 `warmup_onboarding`·`warmup_record`·`warmup_related`로
  구분해 기존 본측정 범례가 워밍업 때문에 중복되지 않게 한다.
- `profile`은 `fixed-100vu`이며 저장 JSON의 VU도 100으로 기록한다.
- 실행마다 RUN_ID를 구분하고 시간 범위를 해당 실행에 맞춘다.
  기존 패널이 scenario만 필터링하면 10 VU와 100 VU 실행을 동시에 조회할 때
  같은 범례가 여러 개 나타날 수 있다.
- 기존 별도 10 VU 전용 대시보드는 `profile="fixed-10vu"`로 조회하므로
  100 VU 결과가 나타나지 않는다. 기존 사용자 패널에서 이번 실행 시간대를 본다.

## 결과 비교

계정·게임·데이터·서버 설정과 0.1초 대기를 유지한 10 VU 결과와 비교한다.
100 VU가 100개의 서로 다른 사용자 계정이나 정확한 1,000 RPS를 의미하지 않는다.
실제 요청률은 응답 시간·대기·부하 생성기 자원에 따라 달라진다.

P95/P99·처리량·HTTP 실패율·본문 검사 결과와 서버/부하 생성기 자원을 함께 확인한다.
실패·지연 악화·처리량 정체가 나타나면 추가 증폭을 멈추고 같은 조건을 재측정한다.
스크립트는 자동 증폭하지 않으며 판정 기준은 기존 10 VU 테스트와 같다.
전체 시간에는 setup과 최대 30초 종료 유예가 추가될 수 있다.

## 서버 호출 없는 설정 확인

```sh
k6 inspect -e RUN_ID=inspect-100 k6/recommendations/personalized-fixed-100vu.js
k6 inspect -e RUN_ID=inspect-100 k6/recommendations/related-fixed-100vu.js
```

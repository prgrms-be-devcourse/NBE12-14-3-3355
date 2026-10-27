# Kotlin 전환 검증 및 복구

## 검증 범위

기준 Java 테스트 515개를 유지하며 H2·격리된 Testcontainers MySQL을 사용한다. 로컬 실제 DB를 대상으로 하는 서버 확인은 DDL validate·SQL 초기화 비활성화·커넥션 read-only를 적용한 별도 포트의 조회로 제한한다. 기존 실행 서버를 종료하지 않는다.

## 최종 실행 결과

- `./gradlew clean build --no-daemon`: 성공, 1분 18초.
- 기존 Java 테스트 **515개 통과**, 실패·오류·건너뜀 0개. 테스트 클래스 69개. [집계 JSON](validation/build-tests.json)
- 운영 Java 0개, 운영 Kotlin 234개(기존 232개 전환 + 호환 함수 파일 2개). Java 테스트 파일 72개, Kotlin 테스트 파일 0개.
- `./gradlew recommendationPrecompute --args='--help' --no-daemon`: 성공. DB 연결 없이 기존 도움말·진입점 확인.
- 실행 JAR로 별도 포트 `18080`에서 기동 성공. 로컬 `gamelog` MySQL의 기존 스키마를 `ddl-auto=validate`로 검증했다. 추천 설정 3개는 모두 true이다.
- SQL 초기화 `never`, Hikari 연결 read-only, 수집·번역 runner 비활성화로 조회만 확인했으며 DB에 DDL·갱신 명령을 실행하지 않았다. 확인 후 이 별도 서버만 종료했다.

## API·검증 애너테이션 확인

[관찰 JSON](validation/api-observations.json)에 상태·응답 키·게임 ID·해시만 저장했다. 토큰·비밀번호와 응답 본문은 저장하지 않았다.

- Java 기준 공개 API **7개 모두 응답 JSON 해시 일치**: health, 게임 목록, Elden 검색·검색 제안, 47808 상세·연관 추천, 없는 게임 404.
- 인증 없는 맞춤 추천: 401, 음수 페이지 요청: 400, OpenAPI 문서: 200.
- 로그인·인증·기록·추천 변경 시나리오는 기존 Java 통합 테스트를 통해 검증했다. 실제 로컬 DB에서 로그인·기록 변경은 추가 실행하지 않았다.
- 문자열 호환 함수는 임시 Java 대조 프로그램으로 모든 Unicode scalar value에 대해 Java `isBlank`·`strip`·`trim` 결과와 일치함을 확인했다. 이 임시 프로그램은 저장소에 추가하지 않았다.
- `javap -v -p`로 `ReviewSaveRequest.rating` 필드의 `ValidRating`, 두 검색 ID 목록 요소의 JVM TYPE_USE `NotNull`·`Positive`를 확인했다.
- 실제 로컬 DB의 지정 ID 사전 계산은 실행하지 않았다. 저장·교체·실패·잠금·준비 상태는 격리된 MySQL 테스트로 검증했다.
- 성능 평가는 Q14 합의에 따라 후속 작업이다. Java 측정값은 유지하며 이번 전환에서 Kotlin 부하 테스트 수치를 새로 측정하지 않았다.

## 복구 원칙

- DB 구조와 데이터를 바꾸는 마이그레이션 SQL을 추가하지 않았다.
- 코드 복구는 운영 Kotlin 전환 커밋과 Kotlin 빌드 지원 커밋을 역순으로 revert하여 진행한다. 문서 커밋은 유지해도 된다.
- 기능 기준 Java 커밋은 `47997bf99150482084e6849635065cb2d3692718`이다.
- 복구 후 기존 DB와 환경변수를 그대로 사용해 Java clean build·서버 기동·추천 조회를 확인한다.
- 실제 revert·운영 배포·원격 push는 수행하지 않는다.

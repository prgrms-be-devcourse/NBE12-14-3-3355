# usergame API 테스트

## 범위

Controller 통합 테스트는 수업 예제와 같은 MockMvc 방식으로 실제 Controller·Service·MySQL을 연결한다.
요청 바인딩, 인증 요구, 검증 오류, HTTP 상태와 RsData JSON, 저장 결과를 확인한다.
서버 포트와 JWT 로그인은 사용하지 않으며 SecurityUser를 테스트 요청에 주입한다.

API E2E는 임의 포트에서 백엔드를 실행하고 JDK HttpClient로 실제 HTTP 요청을 보낸다.
로그인 API에서 발급받은 accessToken을 Bearer 헤더로 전달한다.
프런트엔드 화면을 조작하는 브라우저 E2E는 이번 범위에 포함하지 않는다.

## 역할 분담

- UserGameSearchTest: 검색 조건, 정렬, 특수문자, 페이지네이션을 실제 MySQL 쿼리로 검증한다.
- UserGameControllerTest: 전체 13개 API의 요청·응답 계약을 검증한다.
  기존 검색 테스트의 MockMvc 메서드 5개를 옮겨 중복을 피한다.
- UserGameE2ETest: 여러 API를 연결한 사용자 흐름과 실제 인증을 검증한다.
- 기존 Service·Entity 테스트: 계산 분기, 상태 변화, 라이브러리 및 인생게임 규칙을 자세히 검증한다.
  Controller가 반환값과 저장 결과를 검증하는 단순 상태 변경·해제 및 기록 수정 관련 Service 테스트 6개는 제거했다.
  수정 시 좋아요 저장과 미등록 기록의 오류 메시지 검증은 Controller로 옮겼다.

## Controller 시나리오

| API | 주요 검증 |
| --- | --- |
| GET /api/v1/library/games | 필터 바인딩, 페이지 JSON, 잘못된 검색 조건 |
| POST /api/v1/library/games/{gameId} | 최초 등록 201, 재등록 200, 기록 ID 유지, 없는 게임 |
| PATCH /api/v1/library/games/{gameId} | 시간·날짜·플랫폼 JSON과 저장, 미등록 기록 |
| PUT /api/v1/library/games/{gameId}/play-status | 상태 지정, 생략 시 해제 |
| PATCH /api/v1/library/games/{gameId}/playing | Boolean 바인딩과 저장 |
| PATCH /api/v1/library/games/{gameId}/wishlist | Boolean 바인딩과 저장 |
| PATCH /api/v1/library/games/{gameId}/backlog | Boolean 바인딩과 저장 |
| PATCH /api/v1/library/games/{gameId}/liked | Boolean 바인딩과 저장 |
| GET /api/v1/library/games/profile | 본인 판별, 통계·최근 게임·최근 리뷰 응답 |
| GET /api/v1/library/games/profile/{userId} | 비로그인 공개 조회, 본인 판별, 없는 사용자 |
| GET /api/v1/library/games/profile/{userId}/games | 비로그인 공개 목록, 없는 사용자 |
| GET /api/v1/library/games/recentGames | 최근 게임 목록 응답 |
| PUT /api/v1/library/games/favorite-games | 요청 순서와 표시 순서, 라이브러리 밖 게임 거절 |

공개 조회 2개를 제외한 API는 비로그인 요청에 401을 반환하는지 확인한다.
쓰기 요청은 음수 시간, 잘못된 JSON·enum, 필수 필드 누락, 잘못된 날짜 순서도 확인한다.
상세 취향 계산이나 모든 상태 조합을 Controller 테스트에 반복하지 않는다.

## E2E 시나리오

1. 비로그인 접근 거절 → 두 사용자 실제 로그인 → 기록 등록 → 목록 조회 → 기록 수정 →
   최근 게임·프로필 확인 → 다른 사용자의 목록과 분리 및 미등록 수정 거절 → 공개 조회.
2. 실제 로그인 → 좋아요가 있는 게임 기록 등록 → 인생게임 지정 → 마지막 플레이 상태 해제 →
   라이브러리·인생게임에서 제외 → Likes 조회에는 유지 → 인생게임 재지정 거절 →
   기록 재등록 시 기존 ID 유지.

## 데이터와 실행

테스트는 기존 MySqlIntegrationTest의 mysql:8.4 Testcontainers를 사용하므로 Docker가 필요하다.
외부 IGDB 호출 없이 필요한 사용자와 게임만 직접 저장한다.

Controller 테스트는 테스트 트랜잭션으로 롤백한다.
E2E는 서버 스레드에서 커밋하므로 테스트에 Transactional을 붙이지 않는다.
fixture는 TransactionTemplate으로 먼저 커밋하고,
종료 시 해당 사용자 ID의 RefreshToken·UserFavoriteGame·UserGame과 사용자·게임을 정리한다.
전체 테이블 삭제나 기존 사용자 데이터 삭제는 하지 않는다.

back 디렉터리에서 실행한다.

```bash
./gradlew test --tests '*UserGameControllerTest' --tests '*UserGameE2ETest' --tests '*UserGameSearchTest'
```

E2E는 e2e 태그로도 구분하며 현재 Gradle test 작업에 함께 포함된다.

2026-10-23 Controller·E2E 추가 직후 usergame 전체 115개가 통과했다. 이후 중복 Service 테스트 6개를 제거해 전체 실행 수는 109개다.

중복 제거 후에는 변경된 ServiceLibrary 24개와 Controller 26개, 총 50개가 모두 통과했다.

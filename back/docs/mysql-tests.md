# 담당 기능의 MySQL 통합테스트

게임 조회·검색, IGDB 동기화, 커뮤니티·댓글, 사용자 검색·번역의 DB 통합테스트는 기존
`@MySqlIntegrationTest`와 Testcontainers의 `mysql:8.4`를 사용한다.
로컬 실행용 Docker Compose와 DB 엔진·버전을 맞추기 위한 전환이다.
실제 배포 DB의 세부 설정까지 동일함을 보장하지는 않는다.

## 실행 조건과 격리

- Java 25와 실행 중인 Docker 엔진이 필요하다.
- 최초 실행 시 컨테이너 이미지 다운로드가 필요할 수 있다.
- `@ServiceConnection`이 테스트 컨테이너의 접속 정보를 데이터소스로 등록한다. `test` 프로파일에는 별도 DB 접속 정보가 없다.
- 컨테이너 수명은 Spring 테스트 컨텍스트와 일치한다. 테스트 메서드마다 새 컨테이너를 만들지는 않는다.
- DB를 변경하는 대상 테스트는 `@Transactional` 롤백으로 데이터를 정리한다.
- H2 설정과 의존성은 제거했다. DB를 사용하는 테스트는 `@MySqlIntegrationTest` 또는 `@Import(MySqlTestConfiguration::class)`를 적용해야 한다.

## 대상

- 게임 조회 API: `GameControllerTest`, `GameFilterControllerTest`
- 게임 조회 서비스: `GameServiceTest`
- `GameQueryTestSupport`를 상속하는 검색 테스트: `GameFilterTest`,
  `GameCatalogPolicyTest`, `GameOffsetSearchTest`, `GameSuggestionTest`, `GameSortTest`
- IGDB 동기화: `IgdbSyncTest`, `IgdbSyncSecurityTest`
- 커뮤니티·댓글: `CommunityReviewControllerTest`, `ReviewCommentControllerTest`
- 사용자 검색: `UserSearchIntegrationTest` (기존 검색 Mock 테스트 두 개를 대체)
- 번역 저장·재조회: `GameTranslationTest` (기존 네 시나리오를 MySQL 검증으로 전환)
- 함께 검증하는 기존 MySQL 테스트: `UserGameSearchTest`

담당 범위는 `ansckddnjs17`의 구현 커밋을 기준으로 정했다. 커뮤니티는
`08cf6e3`·`fac846e`, 라이브러리 검색은 `5de7da7`, 사용자 검색은 `c86f741`에 해당한다.
사용자 검색 테스트는 실제 사용자·팔로우 데이터를 저장한 뒤 검색 결과를 검증한다. 번역 테스트는 저장 후 영속성 컨텍스트를 비우고 재조회하여 번역문·원문·해시를 확인한다. 두 테스트에는 Mock이 없으며, 기존 UserFollowServiceTest의 팔로우 관련 Mock 테스트는 유지한다.
다른 작성자의 리뷰 테스트에 픽스처 정리나 Mock만 보완한 경우는 전환 범위에서 제외한다.

## 대상 테스트 실행

`back` 디렉터리에서 다음 명령을 실행한다. PowerShell에서는 `./gradlew` 대신
`.\gradlew.bat`를 사용하고, 줄 연결 없이 한 줄로 입력한다.

```sh
./gradlew test --tests 'com.gamelog.nbe121423355.domain.game.controller.GameControllerTest' --tests 'com.gamelog.nbe121423355.domain.game.controller.GameFilterControllerTest' --tests 'com.gamelog.nbe121423355.domain.game.service.GameServiceTest' --tests 'com.gamelog.nbe121423355.domain.game.service.GameFilterTest' --tests 'com.gamelog.nbe121423355.domain.game.service.GameCatalogPolicyTest' --tests 'com.gamelog.nbe121423355.domain.game.service.GameOffsetSearchTest' --tests 'com.gamelog.nbe121423355.domain.game.service.GameSuggestionTest' --tests 'com.gamelog.nbe121423355.domain.game.service.GameSortTest' --tests 'com.gamelog.nbe121423355.domain.game.sync.IgdbSyncTest' --tests 'com.gamelog.nbe121423355.domain.game.sync.IgdbSyncSecurityTest' --tests 'com.gamelog.nbe121423355.domain.review.controller.CommunityReviewControllerTest' --tests 'com.gamelog.nbe121423355.domain.review.controller.ReviewCommentControllerTest' --tests 'com.gamelog.nbe121423355.domain.usergame.service.UserGameSearchTest' --tests 'com.gamelog.nbe121423355.domain.user.service.UserSearchIntegrationTest' --tests 'com.gamelog.nbe121423355.domain.game.entity.GameTranslationTest'
```

전체 테스트는 `./gradlew test`로 실행한다. 기존 Backend CI의 `build`에도 이 테스트들이 포함된다.

## 작성분 집계와 중복 정리 (2026-10-22)

현재 남아 있는 테스트 메서드의 최초 추가 커밋을 `git log -S`로 추적했다.
작성자가 `ansckddnjs17`인 메서드와 이번 작업에서 대체한 사용자 검색 테스트를 포함한다.
단순 수정만 한 다른 작성자의 테스트는 포함하지 않는다.

직접 작성·대체한 테스트는 15개 클래스에 걸쳐 **73개 메서드, JUnit 실행 기준 78건**이다.
`GameControllerTest.pageRejectsInvalidParameters`가 6개 입력으로 실행되므로 5건이 늘어난다.
테스트 안의 일반 `for` 루프와 assertion 개수는 별도 테스트 건수로 세지 않는다.

- `GameControllerTest`: 7개 메서드 / 12건
- `GameFilterControllerTest`: 1개 / 1건
- `GameServiceTest`: 6개 / 6건
- `GameFilterTest`: 10개 / 10건
- `GameCatalogPolicyTest`: 2개 / 2건
- `GameOffsetSearchTest`: 3개 / 3건
- `GameSuggestionTest`: 3개 / 3건
- `GameSortTest`: 4개 / 4건
- `IgdbSyncTest`: 6개 / 6건
- `IgdbSyncSecurityTest`: 3개 / 3건
- `CommunityReviewControllerTest`: 6개 / 6건
- `ReviewCommentControllerTest`: 7개 / 7건
- `UserGameSearchTest`: 직접 작성 8개 / 8건 (파일 전체 11개)
- `UserSearchIntegrationTest`: 이번 대체 작업 3개 / 3건
- `GameTranslationTest`: 4개 / 4건

`UserGameSearchTest`의 `publicProfileAllowsAnonymousUser`,
`publicProfileIdentifiesAuthenticatedOwner`, `publicGameListAllowsAnonymousUser`는
`6eae2b9`에서 Hyemin Song이 추가하여 작성분 집계와 삭제 후보에서 제외한다.
클래스 전체를 실행하면 이 3건을 포함하여 **76개 메서드, 81건**이다.
중복 제거 전에는 직접 작성분이 75개 메서드 / 80건, 클래스 전체가 78개 메서드 / 83건이었다.
중복 제거 후 위 명령으로 15개 클래스를 함께 실행하여 MySQL에서 81건 모두 통과했다
(실패 0, 오류 0, 건너뜀 0). 직접 작성분 78건과 같은 파일의 다른 작성자 테스트 3건을 포함한다.

`Nbe121423355ApplicationTests`는 `747b1d9`에서 프로파일만 변경했다.
`ReviewServiceTest`의 Mock/stub 보완과 `GameRecordReviewTransactionTest`의 픽스처 정리도
직접 작성한 새 테스트로 세지 않는다. 기존 사용자 검색 Mock 테스트 2개는 이미 대체되어 제외한다.

### 제거한 중복 테스트

같은 계층·동작의 검증이 더 넓은 기존 테스트에 포함되는 경우만 제거한다.

1. `GameServiceTest.getGamesPageContentIsEmptyWhenNoGamesExist`
   - 같은 요청과 빈 DB를 사용하는 `getGamesPageReturnsEmptyPageWhenNoGamesExist`가
     빈 내용뿐 아니라 전체 개수·페이지 수·다음 페이지 여부도 검증한다.
2. `GameServiceTest.searchGamesWithBlankKeywordReturnsAllGames`
   - `GameOffsetSearchTest.unfilteredCatalogPreservesAllPagesAndTotalWithBlankKeywords`가
     같은 서비스 진입점에서 공백·빈 문자열·null 검색어의 전체 목록, 순서, 전체 개수를
     여러 페이지에 걸쳐 검증한다.

사용자 확인 후 위 두 테스트를 제거했다. 직접 작성분은 73개 메서드 / 78건, 클래스 전체는 76개 메서드 / 81건이다.
Controller의 HTTP 바인딩·응답 검증은 유지한다. 필터 유무에 따른 검색 쿼리 분기,
자동완성, 정렬 동률, 날짜 경계 등 고유 조건이 있는 테스트도 유지한다.

### MySQL 테스트가 확인하는 범위

MySQL 8.4에서 준비한 데이터와 요청에 대해 쿼리·매핑·저장·조회 결과가 기대와 일치함을
확인한다. H2와 MySQL 간 SQL 동작 차이에 대한 위험은 줄일 수 있지만 운영 무결함의 증명은 아니다.

- 테스트는 `ddl-auto: create-drop`, 운영 프로파일은 `validate`이므로 운영 DB의 기존
  스키마·인덱스·마이그레이션 상태를 이 테스트로 검증하지 않는다.
- 테스트 JDBC 설정은 `serverTimezone=UTC`를 지정한다. 실제 배포 DB 버전,
  서버 시간대·문자 정렬 규칙·SQL 모드·격리 수준의 일치 여부는 확인하지 않았다.
- 다수 테스트가 테스트 트랜잭션 안에서 동작하고 롤백된다. 실제 요청 종료 시 커밋과
  여러 동시 요청의 경쟁·교착·부하 동작은 별도 검증이 필요하다.
- MockMvc는 실제 외부 HTTP 연결·프록시·브라우저를 거치지 않는다.
  IGDB 동기화도 준비한 응답과 쿼리 문자열을 검증하며 실제 외부 API 연동 성공을 보장하지 않는다.

따라서 결과는 "담당 기능의 명시된 시나리오가 MySQL 통합테스트에서 통과했다"로 표현한다.

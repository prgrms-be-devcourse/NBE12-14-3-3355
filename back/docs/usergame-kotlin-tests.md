# usergame 테스트 Kotlin 전환

usergame의 Java 테스트 9개를 동일 패키지의 `src/test/kotlin`으로 옮겼다.
운영 코드와 빌드 의존성은 변경하지 않고 JUnit 5, AssertJ, Mockito,
`@MySqlIntegrationTest` 구성을 유지했다.

## 전환 기준

- 운영 코드의 프로퍼티, `List`, `UserGame?` 조회 반환 계약을 사용한다.
- 저장된 엔티티의 필수 ID는 `checkNotNull`로 확인하고 선택적 입력은 nullable로 유지한다.
- 긴 `UserGameReqBody` 생성에는 named argument를 사용한다.
- JPA 연관 도메인이 아직 `MutableList`를 요구하는 경우 해당 테스트 데이터만 그 계약에 맞춘다.
- Mockito 매처와 captor는 null을 반환할 수 있다. non-null 인자에는 대체 값을 전달하되,
  매처 등록과 mock 호출 사이에서 다른 mock을 생성하지 않는다.
- 엔티티 상태 변경과 플레이 시간 경계값은 각각 개별 `@Test`로 검증한다.
- 동시성 테스트는 트랜잭션 경계, 래치, 타임아웃, MySQL 중복 키 오류 검증과 정리를 유지한다.
- 순차 중복 INSERT의 UNIQUE 제약 검증은 `repository/UserGameRepositoryTest`에서 분리해 실행한다.

## 검증

2026-10-23 Kotlin 전환 시 기존 테스트 메서드 74개와 표시명을 유지했다.
이후 테스트 정리와 Controller·E2E 추가를 반영한 현재 메서드는 99개이며,
파라미터 확장 후 전체 실행 수는 109개다.

| 테스트 | 실행 수 |
| --- | ---: |
| UserGameInLibraryTest | 13 |
| UserGameConcurrencyTest | 1 |
| UserGameRepositoryTest | 1 |
| UserGameFavoriteTest | 10 |
| UserGameReviewStatisticsTest | 4 |
| UserGameSearchTest | 9 |
| UserGameControllerTest | 26 |
| UserGameE2ETest | 2 |
| UserGameServiceFavoriteTest | 4 |
| UserGameServiceLibraryTest | 24 |
| UserGameServiceProfileTest | 13 |
| UserGameServiceRecentTest | 2 |
| 합계 | 109 |

`back` 디렉터리에서 실행한다. MySQL 통합 테스트에는 Docker가 필요하다.

```bash
./gradlew clean test --tests 'com.gamelog.nbe121423355.domain.usergame.*'
```

Java 테스트 클래스가 남지 않도록 clean 빌드로 전환 결과를 확인했다.
Kotlin 전환 직후에는 review·탈퇴 관련 테스트까지 함께 실행해 202개 모두 통과했고,
당시 usergame 95개의 실패·오류·건너뛴 테스트는 모두 0개였다.
엔티티 테스트 정리 후에는 `UserGameInLibraryTest`의 13개 사례가 모두 통과했다.

```bash
./gradlew test \
  --tests 'com.gamelog.nbe121423355.domain.usergame.*' \
  --tests 'com.gamelog.nbe121423355.domain.review.*' \
  --tests '*UserWithdrawTest'
```

Controller·실제 HTTP E2E의 범위와 실행 방법은 [usergame-api-tests.md](usergame-api-tests.md)를 참고한다.
